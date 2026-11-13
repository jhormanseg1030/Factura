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
    private MetricasService metricasService;

    @Autowired
    private PortalEnvioService portalEnvio;

    @Scheduled(fixedDelayString = "${app.reintento.delay:60000}")
    public void reintentarFacturasPendientes() {
        long inicioCiclo = System.nanoTime();
        try {
            ejecutarCicloReintentos();
        } finally {
            metricasService.registrar("reintento.ciclo", System.nanoTime() - inicioCiclo);
        }
    }

    private void ejecutarCicloReintentos() {
        List<Map<String, Object>> pendientes = facturaPendienteService.leerPendientes();

        if (pendientes.isEmpty()) {
            return;
        }

        logger.info("Reintentando {} facturas pendientes...", pendientes.size());

        // Se opera por id estable (no por índice) para no afectar la factura equivocada.
        List<String> idsAEliminar = new ArrayList<>();

        for (Map<String, Object> factura : pendientes) {
            String id = factura.get("id") == null ? "" : factura.get("id").toString();

            if (facturaPendienteService.superaMaximoIntentos(factura)) {
                logger.warn("Factura pendiente {} excedió máximo de reintentos, requiere intervención manual", id);
                idsAEliminar.add(id);
                continue;
            }

            String jsonPayload = (String) factura.get("json_payload");

            try {
                String body = extraerBodyFactura(jsonPayload);
                PortalEnvioService.Resultado respuesta = portalEnvio.enviar(body);
                facturaEnviadaService.marcarEnvio(body, respuesta.aceptado(), respuesta.mensaje());
                if (respuesta.aceptado()) {
                    logger.info("Factura pendiente {} reenviada exitosamente", id);
                    idsAEliminar.add(id);
                } else {
                    facturaPendienteService.incrementarIntentoPorId(id);
                    logger.warn("Factura pendiente {} falló al reenviar: {}", id, respuesta.mensaje());
                }
            } catch (Exception e) {
                facturaPendienteService.incrementarIntentoPorId(id);
                logger.warn("Factura pendiente {} error al reenviar: {}", id, e.getMessage());
            }
        }

        for (String id : idsAEliminar) {
            facturaPendienteService.eliminarPorId(id);
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
