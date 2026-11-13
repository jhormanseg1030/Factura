package com.prueba.factura.controller;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.prueba.factura.Services.MiddlewareSimphony;
import com.prueba.factura.Services.RutaArchivoService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private static final String DEFAULT_XML_PATH = "C:/SimphonyTest/inbox/Pay17401_34770_209274_20251102134648.xml";

    private final MiddlewareSimphony simphony;
    private final RutaArchivoService rutaArchivoService;

    private static final Logger logger = LoggerFactory.getLogger(TestController.class);

    public TestController(MiddlewareSimphony simphony, RutaArchivoService rutaArchivoService) {
        this.simphony = simphony;
        this.rutaArchivoService = rutaArchivoService;
    }

    @RateLimiter(name = "simularPago", fallbackMethod = "rateLimitFallback")
    @GetMapping("/simular-pago")
    public ResponseEntity<String> simularPago(@RequestParam(value = "xmlPath", required = false) String xmlPath){
        try{
            String resolvedPath = (xmlPath == null || xmlPath.isBlank()) ? DEFAULT_XML_PATH : xmlPath;

            // Validación de ruta: solo archivos dentro de las carpetas autorizadas.
            File filePrueba = rutaArchivoService.resolverPermitido(resolvedPath);
            if (filePrueba == null) {
                return ResponseEntity.badRequest().body("Acceso Denegado: ruta no permitida o archivo inexistente");
            }

            simphony.procesarXML(filePrueba);
            return ResponseEntity.ok("Simulacion ejecutada con exito. Revisa la consola Webhook!");

        } catch(Exception e){
            logger.error("Error inesperado: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body("Error al procesar la solicitud. por favor contacte con el administrador");
        }
    }

    public ResponseEntity<String> rateLimitFallback(Exception e) {
        return ResponseEntity.status(429)
            .body("Demasiadas solicitudes. Intente mas tarde.");
    }

    @PostMapping("/mock-webhook")
    public ResponseEntity<String> mockWebhook(@RequestBody String payload){
        return ResponseEntity.ok("Factura recibida Correctamente");
    }

}
