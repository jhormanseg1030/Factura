package com.prueba.factura.Services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClaveAccesoTest {

    @Test
    void laClaveGuardadaCoincideYLaIncorrectaNo() {
        String hash = ClaveAcceso.hash("admin");
        assertTrue(ClaveAcceso.coincide("admin", hash));
        assertFalse(ClaveAcceso.coincide("otra", hash));
        assertFalse(ClaveAcceso.coincide("admin", "no-es-un-hash"));
    }

    @Test
    void elPrefijoSoloAceptaLetrasYNumeros() {
        assertTrue(SecuenciaService.validarPrefijo(" nc ").equals("nc"));
        assertThrows(IllegalArgumentException.class, () -> SecuenciaService.validarPrefijo("NC-1"));
    }
}
