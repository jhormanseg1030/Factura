package com.prueba.factura.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prueba.factura.Services.FacturaPendienteService;

@RestController
@RequestMapping("/api")
public class FacturasPendientesController {

    private static final Logger logger = LoggerFactory.getLogger(FacturasPendientesController.class);

    @Autowired
    private FacturaPendienteService facturaPendienteService;

    @GetMapping("/facturas-pendientes")
    public ResponseEntity<Map<String, Object>> verPendientes() {
        List<Map<String, Object>> pendientes = facturaPendienteService.leerPendientes();

        Map<String, Object> respuesta = new java.util.LinkedHashMap<>();
        respuesta.put("total_pendientes", pendientes.size());
        respuesta.put("facturas", pendientes);

        logger.info("Consulta de facturas pendientes: {} en cola", pendientes.size());
        return ResponseEntity.ok(respuesta);
    }
}
