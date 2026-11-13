package com.prueba.factura.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Stream;

import ch.qos.logback.core.rolling.RolloverFailure;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Deja un solo archivo del día. Al cambiar de fecha borra el anterior.
 */
public class PoliticaLogDiario extends TimeBasedRollingPolicy<ILoggingEvent> {

    @Override
    public void start() {
        super.start();
        limpiar(Path.of("logs"));
    }

    @Override
    public void rollover() throws RolloverFailure {
        super.rollover();
        limpiar(Path.of("logs"));
    }

    static void limpiar(Path carpeta) {
        if (carpeta == null || !Files.isDirectory(carpeta)) {
            return;
        }
        try (Stream<Path> archivos = Files.list(carpeta)) {
            archivos.filter(PoliticaLogDiario::esArchivoAnterior).forEach(PoliticaLogDiario::borrar);
        } catch (IOException ignored) {
            return;
        }
        Path activo = carpeta.resolve("errores.txt");
        if (!Files.isRegularFile(activo)) {
            return;
        }
        try {
            LocalDate dia = LocalDate.ofInstant(
                    Files.getLastModifiedTime(activo).toInstant(), ZoneId.systemDefault());
            if (!dia.equals(LocalDate.now())) {
                borrar(activo);
            }
        } catch (IOException ignored) {
            // El archivo sigue como está si no se puede leer la fecha.
        }
    }

    private static boolean esArchivoAnterior(Path archivo) {
        String nombre = archivo.getFileName().toString();
        return nombre.matches("errores\\.\\d{4}-\\d{2}-\\d{2}\\.txt");
    }

    private static void borrar(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ignored) {
            // Si está en uso, el siguiente arranque vuelve a intentarlo.
        }
    }
}
