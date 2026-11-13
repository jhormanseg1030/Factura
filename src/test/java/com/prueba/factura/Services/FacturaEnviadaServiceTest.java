package com.prueba.factura.Services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class FacturaEnviadaServiceTest {

    @Test
    void guardaElReporteSinLaClaveTecnicaYPermiteReimprimir() throws Exception {
        Path archivo = Files.createTempFile("facturas-enviadas", ".json");
        FacturaEnviadaService servicio = new FacturaEnviadaService();
        servicio.usarArchivo(archivo);

        servicio.registrar("guid:1", "SETP990000028", "SETP", 990000028L, "abc",
                "2026-11-06T15:56:11-05:00", "CONSUMIDOR FINAL", "FV", 21852L,
                "{\"cliente\":\"CONSUMIDOR FINAL\",\"clave_tecnica_xml\":\"secreto\",\"Total\":21852}",
                "10024", "enviada", "Enviada al portal", "{\"cufe\":\"abc\",\"Prefijo\":\"SETP\"}");

        FacturaEnviadaService.Resumen fila = servicio.listar().get(0);
        assertEquals("SETP990000028", fila.numero());
        assertEquals("abc", servicio.cufeDeNumero("SETP990000028"));
        assertTrue(servicio.payloadDe("guid:1").contains("\"cufe\":\"abc\""));
        assertEquals("enviada", fila.estadoEnvio());
        assertTrue(fila.reimprimible());

        JsonNode ticket = servicio.ticket("guid:1");
        assertEquals("CONSUMIDOR FINAL", ticket.path("cliente").asText());
        assertFalse(ticket.has("clave_tecnica_xml"));

        servicio.guardarEstadoDian("guid:1", "aprobada", "Aprobada por la DIAN");
        assertEquals("aprobada", servicio.listar().get(0).estadoDian());
        assertEquals(null, servicio.siguienteConsulta());

        servicio.marcarEnvio("{\"cufe\":\"abc\",\"Prefijo\":\"SETP\",\"numero_factura\":990000028}", false, "fallo");
        assertEquals("error", servicio.listar().get(0).estadoEnvio());
    }

    @Test
    void laConsultaEsperaYNoRepiteLaMismaFactura() throws Exception {
        assertEquals(0L, FacturaEnviadaService.esperaMillis(0, false));
        assertEquals(60_000L, FacturaEnviadaService.esperaMillis(1, false));
        assertEquals(180_000L, FacturaEnviadaService.esperaMillis(2, false));
        assertEquals(600_000L, FacturaEnviadaService.esperaMillis(3, false));
        assertEquals(1_800_000L, FacturaEnviadaService.esperaMillis(4, false));
        assertEquals(600_000L, FacturaEnviadaService.esperaMillis(1, true));

        String hoy = LocalDate.now().toString();
        Path archivo = Files.createTempFile("facturas-enviadas", ".json");
        FacturaEnviadaService servicio = new FacturaEnviadaService();
        servicio.usarArchivo(archivo);
        servicio.registrar("guid:1", "SETP1", "SETP", 1L, "cufe-1",
                hoy + "T10:00:00", "A", "FV", 10L, "{}", "1", "enviada", "", "{}");
        servicio.registrar("guid:2", "SETP2", "SETP", 2L, "cufe-2",
                hoy + "T11:00:00", "B", "FV", 20L, "{}", "2", "enviada", "", "{}");

        assertEquals("guid:2", servicio.siguienteConsulta().id());
        servicio.anotarConsulta("guid:2", "pendiente", "En proceso", false);
        assertEquals("guid:1", servicio.siguienteConsulta().id());
        servicio.anotarConsulta("guid:1", "error", "No se pudo consultar el portal", true);
        assertEquals(null, servicio.siguienteConsulta());
    }
}
