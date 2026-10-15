package com.prueba.factura.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prueba.factura.Services.FacturaPendienteService;
import com.prueba.factura.Services.Impresion;



@RestController 
@RequestMapping("/api/impresion")
public class ImpresionFactura {
    
    @Autowired 
    private Impresion impresionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private FacturaPendienteService facturaPendienteService;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ImpresionFactura.class);

    @PostMapping 
    public ResponseEntity<String> procesarImpresion(@RequestBody String jsonRaw){
        try{
            logger.info(">>>> JSON RECIBIDO EN CONTROLLER:" + jsonRaw);

            JsonNode datos = objectMapper.readTree(jsonRaw);
            logger.info(">>>> JSON PROCESADO EN CONTROLLER:" + datos);
            impresionService.imprimirFactura(datos);
            return ResponseEntity.ok("Factura enviada");
        } catch (Exception e) {
            logger.error("Error en impresion", e);
            return ResponseEntity.badRequest().body("Error en la impresion" + e.getMessage());
        }
    }

    @PostMapping("/reimprimir")
    public ResponseEntity<String> reimprimir(@RequestBody String jsonRaw) {
        try {
            com.fasterxml.jackson.databind.JsonNode req = objectMapper.readTree(jsonRaw);
            String identificador = req.path("identificador").asText("");
            if (identificador.isBlank()) {
                return ResponseEntity.badRequest().body("Falta identificador");
            }
            String nombre = identificador.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json";
            java.nio.file.Path path = java.nio.file.Paths.get("facturas_impresas", nombre);
            if (!java.nio.file.Files.exists(path)) {
                return ResponseEntity.status(404).body("No existe payload para: " + identificador);
            }
            String payload = java.nio.file.Files.readString(path);
            com.fasterxml.jackson.databind.JsonNode datos = objectMapper.readTree(payload);
            impresionService.imprimirFactura(datos);
            return ResponseEntity.ok("Factura reimpresa: " + identificador);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al reimprimir: " + e.getMessage());
        }
    }

    @GetMapping("/pendientes-total")
    public ResponseEntity<String> pendientesTotal() {
        int total = facturaPendienteService.totalPendientes();
        return ResponseEntity.ok("Pendientes en cola: " + total);
    }
}
