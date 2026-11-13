package com.prueba.factura.Services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortalEnvioServiceTest {

    @Test
    void elMensajeDeErrorTraeElTextoDelPortal() {
        String mensaje = PortalEnvioService.mensajePortal(400,
                "{\"error\":\"Nota crédito POS requiere factura_referencia con numeroDocumento y cufe.\"}");
        assertTrue(mensaje.contains("400"));
        assertTrue(mensaje.contains("factura_referencia con numeroDocumento y cufe"));
    }

    @Test
    void unAceptadoNoSeDescribeComoRechazo() {
        assertEquals("Aceptada por el portal", PortalEnvioService.mensajePortal(202, ""));
    }
}
