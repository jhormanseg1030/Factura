package com.prueba.factura.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FacturaPendienteService {

    private static final Logger logger = LoggerFactory.getLogger(FacturaPendienteService.class);
    private static final int MAX_INTENTOS = 5;

    @Value("${app.pendientes.file:facturas_pendientes.json}")
    private String pendientesFile;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public synchronized void guardarFacturaPendiente(String jsonPayload, String error) {
        try {
            List<Map<String, Object>> pendientes = leerPendientes();

            Map<String, Object> factura = new java.util.LinkedHashMap<>();
            factura.put("id", UUID.randomUUID().toString());   // ID ESTABLE
            factura.put("json_payload", jsonPayload);
            factura.put("fecha_procesamiento", LocalDateTime.now().toString());
            factura.put("fecha_transaccion", java.time.LocalDate.now().toString());  // fecha para diferenciar transacciones
            factura.put("intentos", 1);
            factura.put("ultimo_error", error);

            pendientes.add(factura);
            guardarPendientes(pendientes);

            logger.warn("Factura guardada en cola pendiente. Total pendientes: {}", pendientes.size());
        } catch (Exception e) {
            logger.error("Error guardando factura pendiente: {}", e.getMessage());
        }
    }

    public synchronized List<Map<String, Object>> leerPendientes() {
        Path path = Paths.get(pendientesFile);
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try {
            String contenido = Files.readString(path).trim();
            if (contenido.isEmpty()) {
                return new ArrayList<>();
            }
            List<Map<String, Object>> pendientes = objectMapper.readValue(contenido, new TypeReference<List<Map<String, Object>>>() {});

            // Registros antiguos pueden no tener id: se les asigna uno para poder operarlos de forma segura.
            boolean cambio = false;
            for (Map<String, Object> factura : pendientes) {
                Object id = factura.get("id");
                if (id == null || id.toString().isBlank()) {
                    factura.put("id", UUID.randomUUID().toString());
                    cambio = true;
                }
            }
            if (cambio) {
                guardarPendientes(pendientes);
            }
            return pendientes;
        } catch (IOException e) {
            logger.error("Error leyendo facturas pendientes: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public synchronized void guardarPendientes(List<Map<String, Object>> pendientes) {
        try {
            Path path = Paths.get(pendientesFile);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), pendientes);
        } catch (IOException e) {
            logger.error("Error guardando facturas pendientes: {}", e.getMessage());
        }
    }

    /** Incrementa el contador de intentos de una factura identificada por su id. */
    public synchronized void incrementarIntentoPorId(String id) {
        if (id == null || id.isBlank()) {
            logger.warn("No se puede incrementar intento: id vacío");
            return;
        }
        List<Map<String, Object>> pendientes = leerPendientes();
        for (Map<String, Object> factura : pendientes) {
            if (id.equals(factura.get("id"))) {
                int intentos = leerIntentos(factura);
                factura.put("intentos", intentos + 1);
                factura.put("ultimo_intento", LocalDateTime.now().toString());
                guardarPendientes(pendientes);
                logger.info("Intento registrado para factura pendiente {}", id);
                return;
            }
        }
        logger.warn("No se encontró la factura pendiente con id {}", id);
    }

    /** Elimina de la cola la factura identificada por su id (no por posición). */
    public synchronized void eliminarPorId(String id) {
        if (id == null || id.isBlank()) {
            logger.warn("No se puede eliminar: id vacío");
            return;
        }
        List<Map<String, Object>> pendientes = leerPendientes();
        boolean eliminada = pendientes.removeIf(factura -> id.equals(factura.get("id")));
        if (eliminada) {
            guardarPendientes(pendientes);
            logger.info("Factura eliminada de cola pendiente (id={}). Restantes: {}", id, pendientes.size());
        } else {
            logger.warn("No se encontró la factura pendiente con id {} para eliminar", id);
        }
    }

    public boolean superaMaximoIntentos(Map<String, Object> factura) {
        return leerIntentos(factura) >= MAX_INTENTOS;
    }

    private int leerIntentos(Map<String, Object> factura) {
        Object value = factura.get("intentos");
        if (value instanceof Number numero) {
            return numero.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int totalPendientes() {
        return leerPendientes().size();
    }
}
