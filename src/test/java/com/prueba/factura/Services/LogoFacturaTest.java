package com.prueba.factura.Services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogoFacturaTest {

    @TempDir
    java.nio.file.Path carpeta;

    @Test
    void aceptaUnPngYLoGuarda() throws Exception {
        BufferedImage imagen = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(imagen, "png", salida));

        LogoFactura logo = new LogoFactura(carpeta.resolve("logo-factura.png").toString());
        logo.guardar(salida.toByteArray());

        BufferedImage guardada = logo.imagen();
        assertEquals(4, guardada.getWidth());
        assertEquals(2, guardada.getHeight());
        assertEquals("image/png", logo.tipo());
    }

    @Test
    void rechazaUnArchivoQueNoEsImagen() {
        assertThrows(IllegalArgumentException.class, () -> LogoFactura.leerSubida(new byte[] {1, 2, 3, 4}));
        assertThrows(IllegalArgumentException.class, () -> LogoFactura.leerSubida(new byte[0]));
    }
}
