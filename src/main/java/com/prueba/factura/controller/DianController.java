package com.prueba.factura.controller;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prueba.factura.Services.DianXmlParserService;
import com.prueba.factura.Services.RutaArchivoService;
import com.prueba.factura.dto.DianFacturaDto;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@RestController
@RequestMapping("/api/dian")
public class DianController {

    private static final Logger logger = LoggerFactory.getLogger(DianController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DianXmlParserService dianParser;

    @Autowired
    private RutaArchivoService rutaArchivoService;

    @Value("${app.cufe.registry.file:facturas_cufes.json}")
    private String cufeRegistryFile;

    @RateLimiter(name = "simularPago", fallbackMethod = "rateLimitFallback")
    @GetMapping("/parsear")
    public ResponseEntity<String> parsearXml(@RequestParam(value = "xmlPath", required = false) String xmlPath) {
        try {
            if (xmlPath == null || xmlPath.isBlank()) {
                return ResponseEntity.badRequest().body("Debe indicar el archivo XML a procesar");
            }

            File xmlFile = rutaArchivoService.resolverPermitido(xmlPath);
            if (xmlFile == null) {
                return ResponseEntity.badRequest().body("Acceso Denegado: ruta no permitida o archivo inexistente");
            }

            DianFacturaDto factura = dianParser.parsearXml(xmlFile);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(factura);

            logger.info("XML DIAN parseado: {} - {}", factura.getNumeroDocumento(), factura.getTipoDocumento());
            return ResponseEntity.ok(json);

        } catch (Exception e) {
            logger.error("Error parseando XML DIAN: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Error al procesar la solicitud. Contacte con el administrador.");
        }
    }

    @GetMapping("/parsear/basicos")
    public ResponseEntity<String> parsearBasicos(@RequestParam(value = "xmlPath") String xmlPath) {
        try {
            File xmlFile = rutaArchivoService.resolverPermitido(xmlPath);
            if (xmlFile == null) {
                return ResponseEntity.badRequest().body("Acceso Denegado: ruta no permitida o archivo inexistente");
            }

            java.util.Map<String, String> datos = dianParser.extraerDatosBasicos(xmlFile);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(datos);

            return ResponseEntity.ok(json);

        } catch (Exception e) {
            logger.error("Error parseando XML DIAN: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Error al procesar la solicitud. Contacte con el administrador.");
        }
    }

    @GetMapping("/cufe")
    public ResponseEntity<String> buscarPorCufe(@RequestParam("cufe") String cufe) {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(cufeRegistryFile);
            if (!java.nio.file.Files.exists(path)) {
                return ResponseEntity.status(404).body("No existe registro de CUFEs");
            }
            java.util.Map<String, java.util.Map<String, String>> registros = objectMapper.readValue(path.toFile(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            for (java.util.Map.Entry<String, java.util.Map<String, String>> e : registros.entrySet()) {
                java.util.Map<String, String> v = e.getValue();
                String c = v.getOrDefault("cufe", "");
                if (c != null && c.equalsIgnoreCase(cufe)) {
                    java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
                    out.put("identificador", e.getKey());
                    out.put("numero_factura", v.getOrDefault("numero_factura_completo", "N/A"));
                    out.put("cufe", c);
                    return ResponseEntity.ok(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(out));
                }
            }
            return ResponseEntity.status(404).body("CUFE no encontrado");
        } catch (Exception e) {
            logger.error("Error buscando CUFE: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Error al buscar CUFE");
        }
    }

    public ResponseEntity<String> rateLimitFallback(Exception e) {
        return ResponseEntity.status(429)
                .body("Demasiadas solicitudes. Intente mas tarde.");
    }
}
