package com.prueba.factura.Services;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EstadoPortalService {

    private static final Logger logger = LoggerFactory.getLogger(EstadoPortalService.class);

    private final FacturaEnviadaService enviadas;
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

    @Value("${app.portal.estado.url:}")
    private String plantilla;

    public EstadoPortalService(FacturaEnviadaService enviadas) {
        this.enviadas = enviadas;
    }

    public record Resultado(String estado, String detalle) {
    }

    @Scheduled(fixedDelayString = "${app.portal.estado.delay:6000}")
    public void refrescar() {
        FacturaEnviadaService.Consulta factura = enviadas.siguienteConsulta();
        if (factura == null) {
            return;
        }
        Resultado resultado = consultar(factura.cufe(), factura.numero());
        enviadas.anotarConsulta(factura.id(), resultado.estado(), resultado.detalle(), "error".equals(resultado.estado()));
    }

    public Resultado consultar(String cufe, String numero) {
        if (numero == null || numero.isBlank()) {
            return new Resultado("pendiente", "Sin número de factura para consultar");
        }
        String url = urlConsulta(plantilla, destinoUtil(), cufe, numero);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .header("User-Agent", "FacturaApp/1.0")
                    .header("X-API-Key", apiKey == null ? "" : apiKey)
                    .GET();
            HttpResponse<String> respuesta = cliente.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Resultado resultado = interpretar(respuesta.statusCode(), respuesta.body());
            logger.info("Estado DIAN de {} : {}", numero, resultado.estado());
            return resultado;
        } catch (Exception e) {
            logger.warn("No se pudo consultar el estado en el portal: {}", e.getMessage());
            return new Resultado("error", "No se pudo consultar el portal");
        }
    }

    static String urlConsulta(String plantilla, String destino, String cufe, String numero) {
        String numeroCodificado = codificar(numero == null ? "" : numero.trim());
        String cufeCodificado = codificar(cufe == null ? "" : cufe.trim());
        if (plantilla != null && !plantilla.isBlank()) {
            return plantilla.trim()
                    .replace("{numero_factura}", numeroCodificado)
                    .replace("{numero}", numeroCodificado)
                    .replace("{cufe}", cufeCodificado);
        }
        return sinBarraFinal(destino) + "/estado?numero_factura=" + numeroCodificado;
    }

    static Resultado interpretar(int codigo, String cuerpo) {
        if (codigo == 404) {
            return new Resultado("pendiente", "El portal todavía no publica el documento");
        }
        if (codigo < 200 || codigo >= 300) {
            return new Resultado("error", "El portal respondió " + codigo);
        }
        if (cuerpo == null || cuerpo.isBlank()) {
            return new Resultado("pendiente", "Sin detalle del portal");
        }
        try {
            JsonNode nodo = new ObjectMapper().readTree(cuerpo);
            String mensaje = primerTexto(nodo, "mensaje", "message", "detail", "detalle");
            String status = primerTexto(nodo, "status");
            if (!status.isBlank()) {
                String estado = clasificarStatus(status);
                return new Resultado(estado, detalle(estado, mensaje, status));
            }
            if (nodo.path("aprobada_dian").asBoolean(false)
                    || nodo.path("aprobada").asBoolean(false)
                    || nodo.path("approved").asBoolean(false)) {
                return new Resultado("aprobada", detalle("aprobada", mensaje, ""));
            }
            String interno = primerTexto(nodo, "estado_dian", "estadoDian", "dian_status", "estado", "state");
            if (esAutorizadoInterno(interno)) {
                return new Resultado("aprobada", detalle("aprobada", mensaje, interno));
            }
            String estado = clasificar(interno.isBlank() ? cuerpo : interno);
            return new Resultado(estado, detalle(estado, mensaje, interno));
        } catch (Exception e) {
            return new Resultado(clasificar(cuerpo), acotar(cuerpo));
        }
    }

    static String clasificarStatus(String status) {
        String normal = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (normal.equals("no_encontrado") || normal.equals("no encontrado") || normal.equals("not_found")) {
            return "no_encontrado";
        }
        if (normal.equals("enviado") || normal.equals("enviada")
                || normal.contains("validacion exitosa") || normal.contains("validación exitosa")) {
            return "aprobada";
        }
        return clasificar(status);
    }

    static String clasificar(String texto) {
        String normal = texto == null ? "" : texto.toLowerCase(Locale.ROOT).replace("faj43b", " ");
        if (normal.contains("rechaz") || normal.contains("reject") || normal.contains("invalid")) {
            return "rechazada";
        }
        if (normal.contains("aprob") || normal.contains("acept") || normal.contains("author")
                || normal.contains("valid")) {
            return "aprobada";
        }
        return "pendiente";
    }

    private static boolean esAutorizadoInterno(String estado) {
        String normal = estado == null ? "" : estado.trim().toLowerCase(Locale.ROOT);
        return normal.equals("enviado") || normal.equals("enviada")
                || normal.contains("validacion exitosa") || normal.contains("validación exitosa");
    }

    private static String detalle(String estado, String mensaje, String respaldo) {
        if (mensaje != null && !mensaje.isBlank()) {
            return acotar(mensaje);
        }
        return switch (estado) {
            case "aprobada" -> "Aprobada por la DIAN";
            case "rechazada" -> respaldo == null || respaldo.isBlank() ? "Rechazada por la DIAN" : acotar(respaldo);
            case "no_encontrado" -> "El portal no encontró el documento";
            default -> respaldo == null || respaldo.isBlank() ? "Sin estado publicado" : acotar(respaldo);
        };
    }

    private String destinoUtil() {
        if (destino != null && !destino.isBlank()) {
            return destino;
        }
        return respaldo == null ? "" : respaldo;
    }

    private static String sinBarraFinal(String destino) {
        if (destino == null) {
            return "";
        }
        String base = destino.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String primerTexto(JsonNode nodo, String... campos) {
        for (String campo : campos) {
            JsonNode valor = nodo.get(campo);
            if (valor != null && !valor.isNull() && !valor.asText("").isBlank()) {
                return valor.asText();
            }
        }
        return "";
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor == null ? "" : valor, StandardCharsets.UTF_8);
    }

    private static String acotar(String texto) {
        String limpio = texto.replaceAll("\\s+", " ").trim();
        return limpio.length() <= 180 ? limpio : limpio.substring(0, 180);
    }
}
