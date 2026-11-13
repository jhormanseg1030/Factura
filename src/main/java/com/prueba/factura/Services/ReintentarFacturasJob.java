package com.prueba.factura.Services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private FacturaEnviadaService facturaEnviadaService;

    @Autowired
    private PortalEnvioService portalEnvio;

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
                String body = extraerBodyFactura(jsonPayload);
                PortalEnvioService.Resultado respuesta = portalEnvio.enviar(body);
                facturaEnviadaService.marcarEnvio(body, respuesta.aceptado(), respuesta.mensaje());
                if (respuesta.aceptado()) {
                    logger.info("Factura {} reenviada exitosamente", i + 1);
                    indicesAEliminar.add(i);
                } else {
                    facturaPendienteService.incrementarIntento(i);
                    logger.warn("Factura {} falló al reenviar: {}", i + 1, respuesta.mensaje());
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
