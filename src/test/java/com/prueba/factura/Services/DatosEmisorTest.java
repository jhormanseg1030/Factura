package com.prueba.factura.Services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.prueba.factura.Services.SecuenciaService.DatosEmisor;

class DatosEmisorTest {

    @Test
    void elNitSeGuardaSinDigitoDeVerificacion() {
        DatosEmisor limpio = SecuenciaService.validarEmisor(new DatosEmisor(
                " Zona K ", "860510863", "Calle 1", "300 123", "IVA", "Gracias"));

        assertEquals("Zona K", limpio.razonSocial());
        assertEquals("860510863", limpio.nit());
        assertEquals("300 123", limpio.telefono());
    }

    @Test
    void rechazaElNitConGuion() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SecuenciaService.validarEmisor(new DatosEmisor(
                        "Zona K", "860510863-1", "", "", "", "")));
        assertTrue(error.getMessage().contains("dígito de verificación"));
    }

    @Test
    void unDatoVacioNoPisaLoQueTraeLaCaja() {
        java.util.Map<String, String> datos = new java.util.LinkedHashMap<>();
        datos.put("RazonSocial", "Caja");
        datos.put("RucEmisor", "900");
        MiddlewareSimphony.aplicarEmisor(datos, new DatosEmisor("", "", "Calle 10", "", "", ""));

        assertEquals("Caja", datos.get("RazonSocial"));
        assertEquals("900", datos.get("RucEmisor"));
        assertEquals("Calle 10", datos.get("Direccion"));
    }
}
