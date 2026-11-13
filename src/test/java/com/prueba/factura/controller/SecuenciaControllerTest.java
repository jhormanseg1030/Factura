package com.prueba.factura.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.prueba.factura.Services.FacturaEnviadaService.Resumen;
import com.prueba.factura.Services.SecuenciaService;

class SecuenciaControllerTest {

    @Test
    void elLoginNoMuestraSecuencias() {
        String html = SecuenciaController.login("acceso");
        assertTrue(html.contains("Usuario o contraseña incorrectos"));
        assertTrue(html.contains("/secuencias/entrar"));
        assertFalse(html.contains("Próximo número"));
    }

    @Test
    void sinBaseExplicaQueFaltaSql() {
        String html = SecuenciaController.sinBase();
        assertTrue(html.contains("No se pudo conectar a SQL Server"));
        assertTrue(html.contains("instalar.bat"));
    }

    @Test
    void elPanelMuestraFacturaYNotaCredito() {
        String html = SecuenciaController.panel(
                "admin",
                new SecuenciaService.ConfiguracionSecuencia(SecuenciaService.FACTURA, "DM", 104918),
                new SecuenciaService.ConfiguracionSecuencia(SecuenciaService.NOTA_CREDITO, "NC", 15),
                "token-1",
                null,
                "secuencia");
        assertTrue(html.contains("value=\"DM\""));
        assertTrue(html.contains("value=\"104918\""));
        assertTrue(html.contains("value=\"NC\""));
        assertTrue(html.contains("value=\"15\""));
        assertTrue(html.contains("Secuencias guardadas"));
        assertTrue(html.contains("href=\"/secuencias/empresa\">Empresa"));
        assertTrue(html.contains("href=\"/secuencias/estado\">Estado de facturas"));
        assertTrue(html.contains("href=\"/secuencias/clave\">Contraseña"));
        assertFalse(html.contains("Facturas enviadas"));
        assertFalse(html.contains("Logo de la factura"));
        assertFalse(html.contains("Cambiar contraseña"));
        assertFalse(html.contains("clave_hash"));
    }

    @Test
    void laContrasenaTieneSuPagina() {
        String html = VistaPortal.clave("admin", "token-1", null, "clave");
        assertTrue(html.contains("Cambiar contraseña"));
        assertTrue(html.contains("action=\"/secuencias/clave\""));
        assertTrue(html.contains("Contraseña actualizada"));
        assertFalse(html.contains("Próximo número"));
    }

    @Test
    void laEmpresaMuestraDatosYLogo() {
        String html = VistaPortal.empresa(
                "admin",
                new SecuenciaService.DatosEmisor(
                        "Zona K SAS", "860510863", "Calle 1", "3001234567",
                        "Gran Contribuyente ICA", "Gracias por su visita"),
                true,
                "token-1",
                null,
                "empresa");
        assertTrue(html.contains("value=\"Zona K SAS\""));
        assertTrue(html.contains("value=\"860510863\""));
        assertTrue(html.contains("Gran Contribuyente ICA"));
        assertTrue(html.contains("Logo de la factura"));
        assertTrue(html.contains("action=\"/secuencias/logo\""));
        assertTrue(html.contains("Datos de la empresa guardados"));
        assertFalse(html.contains("Próximo número"));
    }

    @Test
    void elReportePermiteReenviarYMuestraElError() {
        Resumen nota = new Resumen(
                "guid:1", "NC990000001", "2026-11-09T11:55:10", "CONSUMIDOR FINAL", "NC", "10026",
                1852L, "error",
                "El portal rechazó el documento (400): Nota crédito POS requiere factura_referencia con numeroDocumento y cufe.",
                "pendiente", "", "", true, "abc");
        String html = VistaPortal.estado("admin", "token", null, "envio",
                "No se pudo reenviar NC990000001. El portal rechazó el documento (400): Nota crédito POS requiere factura_referencia con numeroDocumento y cufe.",
                List.of(nota));
        assertTrue(html.contains("Reenviar"));
        assertTrue(html.contains("/secuencias/reenviar"));
        assertTrue(html.contains("factura_referencia con numeroDocumento y cufe"));
        assertTrue(html.contains("No se pudo reenviar NC990000001"));
    }
}
