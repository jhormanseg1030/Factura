package com.prueba.factura.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.prueba.factura.Services.MiddlewareSimphony;
import com.prueba.factura.config.HarmonyTipoContenidoFilter;
import com.prueba.factura.config.HarmonyTomcatConfig;
import com.prueba.factura.dto.ResultadoFiscal;

@SpringBootTest(
        classes = HarmonyPeticionRealTest.Mini.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
        })
class HarmonyPeticionRealTest {

    @LocalServerPort
    private int puerto;

    @Test
    void aceptaElXmlDeHarmonySinContentTypeYConFormulario() throws Exception {
        byte[] xml = "<Check CheckNum=\"9\" v=\"100%\"/>".getBytes(StandardCharsets.UTF_8);

        String sinTipo = enviar(puerto, "", xml);
        String tipoVacio = enviar(puerto, "Content-Type: \r\n", xml);
        String formulario = enviar(puerto, "Content-Type: application/x-www-form-urlencoded\r\n", xml);
        String expect = enviarConExpect(puerto, "Content-Type: \r\n", xml);

        assertRespuestaOk(sinTipo);
        assertRespuestaOk(tipoVacio);
        assertRespuestaOk(formulario);
        assertRespuestaOk(expect);
    }

    private static void assertRespuestaOk(String respuesta) {
        assertTrue(respuesta.contains(" 200"), respuesta);
        assertTrue(!respuesta.contains(" 400"), respuesta);
        assertTrue(respuesta.contains("OraPayloadResponseEntity"), respuesta);
    }

    private static String enviar(int puerto, String encabezados, byte[] cuerpo) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", puerto), 3000);
            socket.setSoTimeout(8000);
            OutputStream salida = socket.getOutputStream();
            String inicio = "POST /api/Harmony/col HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + puerto + "\r\n"
                    + encabezados
                    + "Content-Length: " + cuerpo.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            salida.write(inicio.getBytes(StandardCharsets.US_ASCII));
            salida.write(cuerpo);
            salida.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static String enviarConExpect(int puerto, String encabezados, byte[] cuerpo) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", puerto), 3000);
            socket.setSoTimeout(8000);
            OutputStream salida = socket.getOutputStream();
            String inicio = "POST /api/Harmony/col HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + puerto + "\r\n"
                    + encabezados
                    + "Content-Length: " + cuerpo.length + "\r\n"
                    + "Expect: 100-continue\r\n"
                    + "Connection: close\r\n\r\n";
            salida.write(inicio.getBytes(StandardCharsets.US_ASCII));
            salida.flush();
            InputStream entrada = socket.getInputStream();
            ByteArrayOutputStream leido = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!leido.toString(StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                int n = entrada.read(buffer);
                if (n < 0) {
                    break;
                }
                leido.write(buffer, 0, n);
            }
            if (leido.toString(StandardCharsets.ISO_8859_1).startsWith("HTTP/1.1 100")) {
                salida.write(cuerpo);
                salida.flush();
                int n;
                while ((n = entrada.read(buffer)) >= 0) {
                    leido.write(buffer, 0, n);
                }
            }
            return leido.toString(StandardCharsets.ISO_8859_1);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({HarmonyTipoContenidoFilter.class, HarmonyTomcatConfig.class})
    static class Mini {

        @Bean
        HarmonyController harmonyController() {
            MiddlewareSimphony simphony = mock(MiddlewareSimphony.class);
            when(simphony.procesarPayload(anyString())).thenReturn(
                    ResultadoFiscal.ok("1", "cufe", "https://catalogo", "<OraPayloadResponseEntity/>"));
            return new HarmonyController(simphony);
        }
    }
}
