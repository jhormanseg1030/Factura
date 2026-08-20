package com.prueba.factura.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            factura.put("json_payload", jsonPayload);
            factura.put("fecha_procesamiento", LocalDateTime.now().toString());
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
            return objectMapper.readValue(contenido, new TypeReference<List<Map<String, Object>>>() {});
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

    public synchronized void incrementarIntento(int index) {
        List<Map<String, Object>> pendientes = leerPendientes();
        if (index >= 0 && index < pendientes.size()) {
            Map<String, Object> factura = pendientes.get(index);
            int intentos = (int) factura.getOrDefault("intentos", 0);
            factura.put("intentos", intentos + 1);
            factura.put("ultimo_intento", LocalDateTime.now().toString());
            guardarPendientes(pendientes);
        }
    }

    public synchronized void eliminarFacturaPendiente(int index) {
        List<Map<String, Object>> pendientes = leerPendientes();
        if (index >= 0 && index < pendientes.size()) {
            pendientes.remove(index);
            guardarPendientes(pendientes);
            logger.info("Factura eliminada de cola pendiente. Restantes: {}", pendientes.size());
        }
    }

    public boolean superaMaximoIntentos(Map<String, Object> factura) {
        int intentos = (int) factura.getOrDefault("intentos", 0);
        return intentos >= MAX_INTENTOS;
    }

    public int totalPendientes() {
        return leerPendientes().size();
    }
}
