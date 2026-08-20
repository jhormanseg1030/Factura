package com.prueba.factura.Services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReintentarFacturasJob {

    private static final Logger logger = LoggerFactory.getLogger(ReintentarFacturasJob.class);

    @Autowired
    private FacturaPendienteService facturaPendienteService;

    @Value("${app.webhook.url}")
    private String apiUrl;

    @Scheduled(fixedDelayString = "${app.reintento.delay:60000}")
    public void reintentarFacturasPendientes() {
        List<Map<String, Object>> pendientes = facturaPendienteService.leerPendientes();

        if (pendientes.isEmpty()) {
            return;
        }

        logger.info("Reintentando {} facturas pendientes...", pendientes.size());

        List<Integer> indicesAEliminar = new ArrayList<>();

        for (int i = 0; i < pendientes.size(); i++) {
            Map<String, Object> factura = pendientes.get(i);

            if (facturaPendienteService.superaMaximoIntentos(factura)) {
                logger.warn("Factura {} excedió máximo de reintentos, requiere intervención manual", i + 1);
                indicesAEliminar.add(i);
                continue;
            }

            String jsonPayload = (String) factura.get("json_payload");

            try {
                boolean exito = enviarHTTP(jsonPayload);

                if (exito) {
                    logger.info("Factura {} reenviada exitosamente", i + 1);
                    indicesAEliminar.add(i);
                } else {
                    facturaPendienteService.incrementarIntento(i);
                    logger.warn("Factura {} falló al reenviar, reintento registrado", i + 1);
                }
            } catch (Exception e) {
                facturaPendienteService.incrementarIntento(i);
                logger.warn("Factura {} error al reenviar: {}", i + 1, e.getMessage());
            }
        }

        for (int i = indicesAEliminar.size() - 1; i >= 0; i--) {
            facturaPendienteService.eliminarFacturaPendiente(indicesAEliminar.get(i));
        }

        int restantes = facturaPendienteService.totalPendientes();
        if (restantes > 0) {
            logger.warn("Quedan {} facturas pendientes en cola", restantes);
        }
    }

    private boolean enviarHTTP(String jsonPayload) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("User-Agent", "FacturaApp/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }
}
