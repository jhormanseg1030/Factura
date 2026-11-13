package com.prueba.factura.controller;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prueba.factura.Services.FormatoFacturaHarmony;
import com.prueba.factura.Services.MiddlewareSimphony;
import com.prueba.factura.dto.ResultadoFiscal;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping({"/api/harmony", "/api/Harmony"})
public class HarmonyController {

    private static final Logger logger = LoggerFactory.getLogger(HarmonyController.class);
    private static final int MAX_XML_CHARS = 10_000_000;

    private final MiddlewareSimphony simphony;

    public HarmonyController(MiddlewareSimphony simphony) {
        this.simphony = simphony;
    }

    @PostMapping(value = "/col", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> recibir(HttpServletRequest request) throws IOException {
        String xml = xmlDelParametro(request);
        if (xml == null || xml.isBlank()) {
            int contentLength = request.getContentLength();
            if (contentLength > MAX_XML_CHARS) {
                return xml(FormatoFacturaHarmony.rechazo("El XML supera el tamano permitido"));
            }
            int porLeer = contentLength > 0 ? contentLength : MAX_XML_CHARS + 1;
            xml = texto(request.getInputStream().readNBytes(porLeer));
            xml = extraerPayloadSatcom(xml);
        }
        xml = MiddlewareSimphony.prepararXml(xml);

        if (xml.length() > MAX_XML_CHARS) {
            return xml(FormatoFacturaHarmony.rechazo("El XML supera el tamano permitido"));
        }

        logger.info("Payload de Harmony recibido, {} caracteres", xml.length());
        ResultadoFiscal resultado = simphony.procesarPayload(xml);
        return xml(resultado.cuerpo());
    }

    private static String xmlDelParametro(HttpServletRequest request) {
        String tipo = request.getContentType();
        if (tipo == null || !tipo.toLowerCase().contains("multipart/")) {
            return null;
        }
        return request.getParameter("payloadSatcom");
    }

    private static String texto(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, StandardCharsets.UTF_16BE);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static String extraerPayloadSatcom(String cuerpo) {
        if (cuerpo == null) {
            return "";
        }
        String recortado = cuerpo.stripLeading();
        if (recortado.startsWith("<") || !recortado.startsWith("payloadSatcom=")) {
            return cuerpo;
        }
        for (String parte : cuerpo.split("&")) {
            int igual = parte.indexOf('=');
            if (igual <= 0) {
                continue;
            }
            String nombre = decodificar(parte.substring(0, igual));
            if (!"payloadSatcom".equals(nombre)) {
                continue;
            }
            return decodificar(parte.substring(igual + 1));
        }
        return cuerpo;
    }

    private static String decodificar(String valor) {
        try {
            return URLDecoder.decode(valor, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return valor;
        }
    }

    private static ResponseEntity<String> xml(String cuerpo) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(cuerpo);
    }
}
