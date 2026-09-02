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
public class ComunicadorBase {
    private static final Logger logger = LoggerFactory.getLogger(ComunicadorBase.class);
    
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.clientes.api.url:http://192.168.10.111:8000/api/api.php?action-list}")
    private String api;

    public JsonNode buscarPorIdentificacion(String identificacion) {
        if (identificacion == null || identificacion.isBlank()) {
            return null;
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(api + "?action=list"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                logger.warn("La base de clientes respondió HTTP {}", res.statusCode());
                return null;
            }

            JsonNode respuesta = objectMapper.readTree(res.body());
            for (JsonNode cliente : respuesta.path("data")) {
                if (identificacion.equals(cliente.path("identificacion").asText())) {
                    return cliente;
                }
            }
            return null;
        } catch (Exception e) {
            logger.warn("No se pudo consultar la base de clientes por identificacion: {}", e.getMessage());
            return null;
        }
    }

    public JsonNode buscarPorCheckId(String checkId) {
        if (checkId == null || checkId.isBlank()) {
            return null;
        }

        try {
            // NOTA: La API debe consultar la tabla factura_cliente
            // SELECT c.* FROM clientes c 
            // JOIN factura_cliente fc ON c.id = fc.usuario
            // WHERE fc.check_id = ?
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(api + "?action=list"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                logger.warn("La base de clientes respondió HTTP {}", res.statusCode());
                return null;
            }

            JsonNode respuesta = objectMapper.readTree(res.body());
            for (JsonNode cliente : respuesta.path("data")) {
                String clienteCheckId = cliente.path("check_id").asText();
                if (checkId.equals(clienteCheckId) && !clienteCheckId.isEmpty()) {
                    return cliente;
                }
            }
            return null;
        } catch (Exception e) {
            logger.warn("No se pudo consultar la base de clientes por check_id: {}", e.getMessage());
            return null;
        }
    }

    public void listar() throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(api + "?action=list")).GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println(res.body());
    }
    public void crear() throws Exception {
        String json = "{\"tipoCliente\":\"CC\",\"identificacion\":\"123456789\",\"nombre\":\"Prueba\",\"apellido\":\"Compas\",\"emails\":[\"prueba@mail.com\"]}";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(api))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println(res.body());
    }
    public static void main(String[] args) throws Exception {
        System.out.println("Usa la aplicación Spring para consultar clientes");
    }
}
