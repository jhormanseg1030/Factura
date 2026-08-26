package com.prueba.factura.controller;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prueba.factura.Services.DianXmlParserService;
import com.prueba.factura.dto.DianFacturaDto;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@RestController
@RequestMapping("/api/dian")
public class DianController {

    private static final Logger logger = LoggerFactory.getLogger(DianController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DianXmlParserService dianParser;

    @RateLimiter(name = "simularPago", fallbackMethod = "rateLimitFallback")
    @GetMapping("/parsear")
    public ResponseEntity<String> parsearXml(@RequestParam(value = "xmlPath", required = false) String xmlPath) {
        try {
            String resolvedPath = (xmlPath == null || xmlPath.isBlank())
                    ? "C:/SimphonyTest/inbox/"
                    : xmlPath;

            File xmlFile = new File(resolvedPath);
            if (!xmlFile.exists()) {
                return ResponseEntity.badRequest().body("El archivo no se encuentra: " + resolvedPath);
            }

            DianFacturaDto factura = dianParser.parsearXml(xmlFile);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(factura);

            logger.info("XML DIAN parseado: {} - {}", factura.getNumeroDocumento(), factura.getTipoDocumento());
            return ResponseEntity.ok(json);

        } catch (Exception e) {
            logger.error("Error parseando XML DIAN: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Error al parsear el XML DIAN: " + e.getMessage());
        }
    }

    @GetMapping("/parsear/basicos")
    public ResponseEntity<String> parsearBasicos(@RequestParam(value = "xmlPath") String xmlPath) {
        try {
            File xmlFile = new File(xmlPath);
            if (!xmlFile.exists()) {
                return ResponseEntity.badRequest().body("El archivo no se encuentra");
            }

            java.util.Map<String, String> datos = dianParser.extraerDatosBasicos(xmlFile);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(datos);

            return ResponseEntity.ok(json);

        } catch (Exception e) {
            logger.error("Error parseando XML DIAN: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Error al parsear: " + e.getMessage());
        }
    }

    public ResponseEntity<String> rateLimitFallback(Exception e) {
        return ResponseEntity.status(429)
                .body("Demasiadas solicitudes. Intente mas tarde.");
    }
}
