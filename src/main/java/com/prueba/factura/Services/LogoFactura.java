package com.prueba.factura.Services;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class LogoFactura {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final String INCLUIDO = "Zonak.jpeg";

    private static final Logger logger = LoggerFactory.getLogger(LogoFactura.class);

    private final Path archivo;

    public LogoFactura(@Value("${app.logo.archivo:logo-factura.png}") String archivo) {
        this.archivo = Path.of(archivo);
    }

    public boolean hayLogo() {
        return Files.isRegularFile(archivo);
    }

    public boolean visible() {
        return hayLogo() || new ClassPathResource(INCLUIDO).exists();
    }

    public BufferedImage imagen() {
        if (hayLogo()) {
            try {
                BufferedImage propia = ImageIO.read(archivo.toFile());
                if (propia != null) {
                    return propia;
                }
            } catch (IOException e) {
                logger.warn("No se pudo leer el logo de la factura", e);
            }
        }
        return imagenIncluida();
    }

    public byte[] bytes() throws IOException {
        if (hayLogo()) {
            return Files.readAllBytes(archivo);
        }
        ClassPathResource recurso = new ClassPathResource(INCLUIDO);
        if (!recurso.exists()) {
            return null;
        }
        try (InputStream entrada = recurso.getInputStream()) {
            return entrada.readAllBytes();
        }
    }

    public String tipo() {
        return hayLogo() ? "image/png" : "image/jpeg";
    }

    public void guardar(byte[] contenido) throws IOException {
        BufferedImage imagen = leerSubida(contenido);
        Path carpeta = archivo.getParent();
        if (carpeta != null) {
            Files.createDirectories(carpeta);
        }
        Path temporal = archivo.resolveSibling(archivo.getFileName().toString() + ".tmp");
        if (!ImageIO.write(imagen, "png", temporal.toFile())) {
            throw new IllegalArgumentException("No se pudo guardar el logo.");
        }
        try {
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static BufferedImage leerSubida(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new IllegalArgumentException("Seleccione una imagen.");
        }
        if (contenido.length > MAX_BYTES) {
            throw new IllegalArgumentException("El logo no puede pasar de 2 MB.");
        }
        try {
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(contenido));
            if (imagen == null) {
                throw new IllegalArgumentException("Use un archivo PNG o JPG.");
            }
            return imagen;
        } catch (IOException e) {
            throw new IllegalArgumentException("Use un archivo PNG o JPG.");
        }
    }

    private static BufferedImage imagenIncluida() {
        ClassPathResource recurso = new ClassPathResource(INCLUIDO);
        if (!recurso.exists()) {
            return null;
        }
        try (InputStream entrada = recurso.getInputStream()) {
            return ImageIO.read(entrada);
        } catch (IOException e) {
            logger.warn("No se pudo leer el logo incluido", e);
            return null;
        }
    }
}
