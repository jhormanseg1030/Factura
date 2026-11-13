package com.prueba.factura.Services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PortalEnvioService {

    private static final Logger logger = LoggerFactory.getLogger(PortalEnvioService.class);

    private final HttpClient cliente = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${app.target.url:}")
    private String destino;

    @Value("${app.webhook.url:}")
    private String respaldo;

    @Value("${app.api.key:}")
    private String apiKey;

    @Value("${app.emission.point.id:}")
    private String puntoEmision;

    public record Resultado(boolean aceptado, String mensaje) {
    }

    public Resultado enviar(String jsonPayload) {
        String url = destino != null && !destino.isBlank() ? destino : respaldo;
        if (url == null || url.isBlank()) {
            return new Resultado(false, "No hay dirección del portal configurada.");
        }
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return new Resultado(false, "No hay copia del JSON para reenviar este documento.");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "FacturaApp/1.0")
                    .header("X-API-KEY", apiKey == null ? "" : apiKey);
            if (puntoEmision != null && !puntoEmision.isBlank()) {
                builder.header("X-Emission-Point-ID", puntoEmision);
            }
            HttpResponse<String> respuesta = cliente.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(jsonPayload)).build(),
                    HttpResponse.BodyHandlers.ofString());
            String mensaje = mensajePortal(respuesta.statusCode(), respuesta.body());
            if (respuesta.statusCode() >= 200 && respuesta.statusCode() < 300) {
                logger.info("Portal aceptó el documento: {}", respuesta.statusCode());
                return new Resultado(true, mensaje);
            }
            logger.warn("Portal rechazó el documento: {}", mensaje);
            return new Resultado(false, mensaje);
        } catch (Exception e) {
            String detalle = e.getMessage() == null || e.getMessage().isBlank()
                    ? "sin detalle"
                    : e.getMessage();
            logger.warn("No se pudo contactar el portal: {}", detalle);
            return new Resultado(false, "No se pudo contactar el portal: " + detalle);
        }
    }

    static String mensajePortal(int codigo, String cuerpo) {
        String error = textoError(cuerpo);
        if (codigo >= 200 && codigo < 300) {
            return error.isBlank() ? "Aceptada por el portal" : error;
        }
        if (!error.isBlank()) {
            return "El portal rechazó el documento (" + codigo + "): " + error;
        }
        if (cuerpo != null && !cuerpo.isBlank() && !cuerpo.trim().startsWith("{") && !cuerpo.trim().startsWith("<")) {
            return "El portal rechazó el documento (" + codigo + "): " + acotar(cuerpo);
        }
        return "El portal rechazó el documento (" + codigo + ").";
    }

    private static String textoError(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return "";
        }
        try {
            JsonNode nodo = new ObjectMapper().readTree(cuerpo);
            for (String campo : new String[] {"error", "message", "mensaje", "detail", "detalle"}) {
                JsonNode valor = nodo.get(campo);
                if (valor != null && !valor.isNull()) {
                    String texto = valor.isTextual() ? valor.asText() : valor.toString();
                    if (!texto.isBlank() && !"null".equals(texto)) {
                        return acotar(texto);
                    }
                }
            }
        } catch (Exception ignorado) {
            return "";
        }
        return "";
    }

    private static String acotar(String texto) {
        String limpio = texto.replaceAll("\\s+", " ").trim();
        return limpio.length() <= 400 ? limpio : limpio.substring(0, 400);
    }
}
