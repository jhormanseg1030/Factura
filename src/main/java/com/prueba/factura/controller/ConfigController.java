package com.prueba.factura.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/admin")
public class ConfigController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.inbox.directory:}")
    private String inboxDirectory;
    @Value("${app.processed.directory:}")
    private String processedDirectory;
    @Value("${app.webhook.url:}")
    private String webhookUrl;
    @Value("${app.api.key:}")
    private String apiKey;
    @Value("${app.clientes.api.url:}")
    private String clientesApiUrl;
    @Value("${app.counter.file:factura_counter.txt}")
    private String counterFile;
    @Value("${app.pendientes.file:facturas_pendientes.json}")
    private String pendientesFile;
    @Value("${app.reintento.delay:60000}")
    private String reintentoDelay;
    @Value("${impresora.ip:}")
    private String impresoraIp;
    @Value("${impresora.puerto:9100}")
    private int impresoraPuerto;
    @Value("${app.dian.ambiente:1}")
    private String dianAmbiente;
    @Value("${app.envio.automatico:true}")
    private boolean envioAutomatico;
    @Value("${check.historico.file:facturas_historico.json}")
    private String checkHistoricoFile;

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("app.inbox.directory", inboxDirectory);
        cfg.put("app.processed.directory", processedDirectory);
        cfg.put("app.webhook.url", maskSensitive(webhookUrl));
        cfg.put("app.api.key", maskSecret(apiKey));
        cfg.put("app.clientes.api.url", maskSensitive(clientesApiUrl));
        cfg.put("app.counter.file", counterFile);
        cfg.put("app.pendientes.file", pendientesFile);
        cfg.put("app.reintento.delay", reintentoDelay);
        cfg.put("impresora.ip", impresoraIp);
        cfg.put("impresora.puerto", impresoraPuerto);
        cfg.put("app.dian.ambiente", dianAmbiente);
        cfg.put("app.envio.automatico", envioAutomatico);
        return ResponseEntity.ok(cfg);
    }

    @GetMapping("/historico-checks")
    public ResponseEntity<?> historicoChecks() {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(checkHistoricoFile);
            if (!java.nio.file.Files.exists(path)) {
                return ResponseEntity.status(404).body("No existe histórico de checks");
            }
            java.util.Map<String, Object> data = objectMapper.readValue(path.toFile(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error leyendo histórico: " + e.getMessage());
        }
    }

    private String maskSecret(String v) {
        if (v == null || v.isBlank()) return "";
        if (v.length() <= 4) return "****";
        return v.substring(0, 2) + "****" + v.substring(v.length() - 2);
    }

    private String maskSensitive(String v) {
        if (v == null) return "";
        return v;
    }
}
