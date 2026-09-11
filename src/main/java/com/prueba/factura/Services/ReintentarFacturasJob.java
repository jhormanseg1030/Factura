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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ReintentarFacturasJob {

    private static final Logger logger = LoggerFactory.getLogger(ReintentarFacturasJob.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private FacturaPendienteService facturaPendienteService;

    @Value("${app.webhook.url}")
    private String apiUrl;

    @Value("${app.target.url:${app.webhook.url}}")
    private String targetUrl;

    @Value("${app.api.key}")
    private String apiKey;

    @Value("${app.emission.point.id:}")
    private String emissionPointId;

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
            String body = extraerBodyFactura(jsonPayload);
            String destino = (targetUrl != null && !targetUrl.isBlank()) ? targetUrl : apiUrl;

            HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(destino))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("User-Agent", "FacturaApp/1.0")
                .header("X-API-KEY", apiKey != null ? apiKey : "");

            if (emissionPointId != null && !emissionPointId.isBlank()) {
                requestBuilder.header("X-Emission-Point-ID", emissionPointId);
            }

            HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("Reintento falló con status {} body: {}", response.statusCode(), response.body());
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.warn("Error en reintento HTTP: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Soporta payloads antiguos con wrapper {targetUrl, headers, body}
     * y payloads nuevos que ya son el body de la factura.
     */
    private String extraerBodyFactura(String jsonPayload) throws Exception {
        JsonNode root = objectMapper.readTree(jsonPayload);
        if (root.has("body") && (root.has("headers") || root.has("targetUrl"))) {
            return objectMapper.writeValueAsString(root.get("body"));
        }
        return jsonPayload;
    }
}
