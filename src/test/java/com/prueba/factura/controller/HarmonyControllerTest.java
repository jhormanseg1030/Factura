package com.prueba.factura.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.prueba.factura.Services.MiddlewareSimphony;
import com.prueba.factura.dto.ResultadoFiscal;

@ExtendWith(MockitoExtension.class)
class HarmonyControllerTest {

    @Mock
    private MiddlewareSimphony simphony;

    @Test
    void respondeElTicketDeHarmonyConElCampoPayloadSatcom() throws Exception {
        when(simphony.procesarPayload("<Check CheckNum=\"1\"/>")).thenReturn(
                ResultadoFiscal.ok("SETT12", "abc123",
                        "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=abc123",
                        "<OraPayloadResponseEntity><OraPayloadResponseCode>1</OraPayloadResponseCode></OraPayloadResponseEntity>"));

        HarmonyController controller = new HarmonyController(simphony);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/x-www-form-urlencoded");
        String cuerpo = "payloadSatcom=" + URLEncoder.encode("<Check CheckNum=\"1\"/>", StandardCharsets.UTF_8);
        request.setContent(cuerpo.getBytes(StandardCharsets.UTF_8));

        ResponseEntity<String> respuesta = controller.recibir(request);

        assertTrue(respuesta.getBody().contains("<OraPayloadResponseEntity>"));
        assertTrue(respuesta.getBody().contains("<OraPayloadResponseCode>1</OraPayloadResponseCode>"));
        verify(simphony).procesarPayload("<Check CheckNum=\"1\"/>");
    }

    @Test
    void leeElXmlDelCuerpoCuandoNoHayCampo() throws Exception {
        when(simphony.procesarPayload("<Check CheckNum=\"2\"/>")).thenReturn(
                ResultadoFiscal.ok("SETT13", "cufe", "https://catalogo/cufe", "<OraPayloadResponseEntity/>"));

        HarmonyController controller = new HarmonyController(simphony);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("text/xml");
        request.setContent("<Check CheckNum=\"2\"/>".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<String> respuesta = controller.recibir(request);

        assertTrue(respuesta.getStatusCode().is2xxSuccessful());
        verify(simphony).procesarPayload("<Check CheckNum=\"2\"/>");
    }

    @Test
    void quitaLaMarcaYLoQueEsteAntesDelXml() throws Exception {
        when(simphony.procesarPayload("<Check CheckNum=\"10010\"/>")).thenReturn(
                ResultadoFiscal.ok("SETP1", "cufe", "https://catalogo/cufe",
                        "<OraPayloadResponseEntity CheckNum=\"10010\"/>"));

        HarmonyController controller = new HarmonyController(simphony);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("text/xml");
        request.setContent("\uFEFFxx<Check CheckNum=\"10010\"/>".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<String> respuesta = controller.recibir(request);

        assertTrue(respuesta.getBody().contains("CheckNum=\"10010\""));
        verify(simphony).procesarPayload("<Check CheckNum=\"10010\"/>");
    }
}
