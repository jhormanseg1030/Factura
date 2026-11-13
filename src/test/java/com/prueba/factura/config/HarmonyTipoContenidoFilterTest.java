package com.prueba.factura.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.prueba.factura.config.HarmonyTipoContenidoFilter.SolicitudTipoXml;

class HarmonyTipoContenidoFilterTest {

    @Test
    void unTipoVacioSeTrataComoXml() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("  ");
        SolicitudTipoXml envuelta = new SolicitudTipoXml(request);

        assertEquals("text/xml", envuelta.getContentType());
        assertEquals("text/xml", envuelta.getHeader("Content-Type"));
    }

    @Test
    void conservaUnTipoYaInformado() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/xml");
        SolicitudTipoXml envuelta = new SolicitudTipoXml(request);

        assertEquals("application/xml", envuelta.getContentType());
    }

    @Test
    void unFormularioNoSeParseaComoParametros() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/x-www-form-urlencoded");
        request.setParameter("payloadSatcom", "<Check/>");
        SolicitudTipoXml envuelta = new SolicitudTipoXml(request);

        assertEquals("text/xml", envuelta.getContentType());
        assertNull(envuelta.getParameter("payloadSatcom"));
    }

    @Test
    void reconoceLaRutaDeHarmony() {
        assertTrue(HarmonyTipoContenidoFilter.esRutaHarmony("/api/Harmony/col"));
        assertTrue(HarmonyTipoContenidoFilter.esRutaHarmony("/api/harmony/col"));
        assertTrue(HarmonyTipoContenidoFilter.esRutaHarmony("/api/Harmony/col/"));
    }
}
