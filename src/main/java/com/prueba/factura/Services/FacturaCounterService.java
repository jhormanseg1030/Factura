package com.prueba.factura.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FacturaCounterService {

    private static final Logger logger = LoggerFactory.getLogger(FacturaCounterService.class);

    @Value("${app.counter.file:factura_counter.txt}")
    private String counterFile;

    private long contador = 0;

    public synchronized long obtenerSiguienteNumero() {
        contador = leerContador();
        contador++;
        guardarContador(contador);
        logger.info("Factura #{} asignada", contador);
        return contador;
    }

    private long leerContador() {
        Path path = Paths.get(counterFile);
        if (!Files.exists(path)) {
            logger.info("Archivo de contador no existe, creando en 0");
            guardarContador(0);
            return 0;
        }
        try {
            String contenido = Files.readString(path).trim();
            if (contenido.isEmpty()) {
                return 0;
            }
            return Long.parseLong(contenido);
        } catch (IOException | NumberFormatException e) {
            logger.warn("Error leyendo contador, reiniciando a 0: {}", e.getMessage());
            return 0;
        }
    }

    private void guardarContador(long valor) {
        Path path = Paths.get(counterFile);
        try {
            Files.writeString(path, String.valueOf(valor), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            logger.error("Error guardando contador: {}", e.getMessage());
        }
    }
}
