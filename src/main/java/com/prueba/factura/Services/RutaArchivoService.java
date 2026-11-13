package com.prueba.factura.Services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Valida que los archivos leídos desde los endpoints pertenezcan a carpetas autorizadas.
 * Evita path traversal / lectura arbitraria de archivos (LFI).
 */
@Service
public class RutaArchivoService {

    private static final Logger logger = LoggerFactory.getLogger(RutaArchivoService.class);

    @Value("${app.rutas.permitidas:}")
    private String rutasPermitidas;

    @Value("${app.inbox.directory:}")
    private String inboxDirectory;

    @Value("${app.processed.directory:}")
    private String processedDirectory;

    public List<String> basesPermitidas() {
        List<String> bases = new ArrayList<>();
        agregar(bases, inboxDirectory);
        agregar(bases, processedDirectory);
        if (rutasPermitidas != null && !rutasPermitidas.isBlank()) {
            for (String ruta : rutasPermitidas.split(",")) {
                agregar(bases, ruta);
            }
        }
        return bases;
    }

    private void agregar(List<String> bases, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        String limpio = valor.trim();
        if (!bases.contains(limpio)) {
            bases.add(limpio);
        }
    }

    /**
     * Devuelve el archivo canonical si la ruta está dentro de una base permitida.
     * Si la ruta no está permitida o no existe, devuelve null.
     */
    public File resolverPermitido(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return null;
        }
        try {
            File archivo = new File(ruta).getCanonicalFile();
            if (!archivo.isFile()) {
                return null;
            }
            Path destino = archivo.toPath().toAbsolutePath().normalize();
            for (String base : basesPermitidas()) {
                Path basePath = Paths.get(base).toAbsolutePath().normalize();
                if (destino.startsWith(basePath)) {
                    return archivo;
                }
            }
            logger.warn("Acceso denegado: ruta fuera de las carpetas permitidas -> {}", ruta);
            return null;
        } catch (IOException | RuntimeException e) {
            logger.warn("Ruta inválida: {}", ruta);
            return null;
        }
    }
}
