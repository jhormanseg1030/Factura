package com.prueba.factura.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prueba.factura.Services.Impresion;



@RestController 
@RequestMapping("/api/impresion")
public class ImpresionFactura {
    
    @Autowired 
    private Impresion impresionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping 
    public ResponseEntity<String> procesarImpresion(@RequestBody String jsonRaw){
        try{
            System.out.println(">>>> JSON RECIBIDO EN CONTROLLER:" + jsonRaw);

            JsonNode datos = objectMapper.readTree(jsonRaw);
            System.out.println(">>>> JSON PROCESADO EN CONTROLLER:" + datos);   
            impresionService.imprimirFactura(datos);
            return ResponseEntity.ok("Factura enviada");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error en la impresion" + e.getMessage());
        }
    }
}