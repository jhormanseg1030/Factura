package com.prueba.factura.Services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EstadoPortalServiceTest {

    @Test
    void armaLaConsultaConElMismoServidorDelPortal() {
        String url = EstadoPortalService.urlConsulta(
                "",
                "http://181.48.67.98:8080/api/v1/ingest/pos",
                "abc123",
                "SETP990000028");
        assertEquals("http://181.48.67.98:8080/api/v1/ingest/pos/estado?numero_factura=SETP990000028", url);
    }

    @Test
    void respetaLaRutaConfigurada() {
        String url = EstadoPortalService.urlConsulta(
                "http://portal.local/api/v1/estado?cufe={cufe}&numero={numero}",
                "http://otro",
                "abc",
                "NC1");
        assertEquals("http://portal.local/api/v1/estado?cufe=abc&numero=NC1", url);
    }

    @Test
    void interpretaLaRespuestaDelPortal() {
        assertEquals("aprobada", EstadoPortalService.interpretar(200, "{\"estado_dian\":\"Aprobada\"}").estado());
        assertEquals("rechazada", EstadoPortalService.interpretar(200, "{\"status\":\"RECHAZADA\"}").estado());
        assertEquals("pendiente", EstadoPortalService.interpretar(404, "").estado());
        assertEquals("error", EstadoPortalService.interpretar(500, "").estado());
        assertEquals("aprobada", EstadoPortalService.clasificar("Documento validado"));
        assertEquals("rechazada", EstadoPortalService.clasificar("invalid"));
        assertEquals("pendiente", EstadoPortalService.clasificar("Notificación FAJ43b del nombre en el RUT"));
    }

    @Test
    void leeStatusAunqueElEstadoInternoSeaEnviado() {
        String cuerpo = """
                {"status":"APROBADA","aprobada_dian":true,"pendiente":false,"estado_dian":"ENVIADO","mensaje":"La Factura electrónica SETP990000030, ha sido autorizada."}
                """;
        EstadoPortalService.Resultado resultado = EstadoPortalService.interpretar(200, cuerpo);
        assertEquals("aprobada", resultado.estado());
        assertTrue(resultado.detalle().contains("autorizada"));
    }

    @Test
    void enviadoSinStatusQuedaAprobadaYFaj43bNoEsRechazo() {
        String cuerpo = """
                {"estado_dian":"ENVIADO","mensaje":"Notificación FAJ43b: el nombre no coincide con el registrado en el RUT"}
                """;
        EstadoPortalService.Resultado resultado = EstadoPortalService.interpretar(200, cuerpo);
        assertEquals("aprobada", resultado.estado());
        assertTrue(resultado.detalle().contains("FAJ43b"));
    }

    @Test
    void noEncontradoNoQuedaPendiente() {
        EstadoPortalService.Resultado resultado = EstadoPortalService.interpretar(
                200, "{\"status\":\"NO_ENCONTRADO\",\"pendiente\":false}");
        assertEquals("no_encontrado", resultado.estado());
        assertEquals("El portal no encontró el documento", resultado.detalle());
    }
}
