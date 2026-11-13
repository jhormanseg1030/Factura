package com.prueba.factura.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PoliticaLogDiarioTest {

    @TempDir
    Path carpeta;

    @Test
    void borraElDiaAnteriorYConservaElDeHoy() throws Exception {
        Path hoy = carpeta.resolve("errores.txt");
        Path ayer = carpeta.resolve("errores.2026-11-10.txt");
        Files.writeString(hoy, "error de hoy");
        Files.writeString(ayer, "error de ayer");
        Path viejo = carpeta.resolve("errores-viejo.txt");
        Files.writeString(viejo, "de ayer");
        Files.setLastModifiedTime(viejo, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));

        PoliticaLogDiario.limpiar(carpeta);

        assertTrue(Files.exists(hoy));
        assertFalse(Files.exists(ayer));
        assertTrue(Files.readString(hoy).contains("error de hoy"));

        Path otro = Files.createDirectory(carpeta.resolve("otro"));
        Path pasado = otro.resolve("errores.txt");
        Files.writeString(pasado, "de ayer");
        Files.setLastModifiedTime(pasado, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));
        PoliticaLogDiario.limpiar(otro);
        assertFalse(Files.exists(pasado));
    }
}
