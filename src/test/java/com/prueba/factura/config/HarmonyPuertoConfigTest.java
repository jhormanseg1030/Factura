package com.prueba.factura.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HarmonyPuertoConfigTest {

    @TempDir
    Path temporal;

    @Test
    void aceptaElPuerto8098() {
        assertEquals(8098, HarmonyPuertoConfig.validarPuerto("8098"));
    }

    @Test
    void aceptaOtroPuertoYArmaLaUrl() {
        assertEquals(8080, HarmonyPuertoConfig.validarPuerto("8080"));
        assertEquals("http://172.19.56.163:8080/api/harmony/col",
                HarmonyPuertoConfig.urlPartner("172.19.56.163", 8080));
    }

    @Test
    void guardaYRecuperaLaEleccion() throws Exception {
        Path archivo = temporal.resolve("harmony.properties");
        HarmonyPuertoConfig.guardar(archivo, new HarmonyPuertoConfig.DatosPuerto(
                9090, "10.1.1.8", "http://10.1.1.20:8000/api/factura", "llave-local", "POS-1", "", "", ""));

        HarmonyPuertoConfig.DatosPuerto datos = HarmonyPuertoConfig.cargar(archivo);
        assertEquals(9090, datos.puerto());
        assertEquals("10.1.1.8", datos.host());
        assertEquals("http://10.1.1.20:8000/api/factura", datos.urlFirma());
        assertEquals("llave-local", datos.apiKey());
        assertEquals("POS-1", datos.puntoEmision());
        assertTrue(Files.readString(archivo).contains("app.webhook.url=http://10.1.1.20:8000/api/factura"));
    }

    @Test
    void alCambiarElPuertoConservaLaAppQueFirma() {
        Path archivo = temporal.resolve("harmony.properties");
        HarmonyPuertoConfig.guardar(archivo, new HarmonyPuertoConfig.DatosPuerto(
                8080, "10.1.1.8", "https://firma.ejemplo/api/factura", "llave", "",
                "localhost\\SQLEXPRESS", "sa", "clave\\secreta"));

        HarmonyPuertoConfig.DatosPuerto actual = HarmonyPuertoConfig.cargar(archivo);
        HarmonyPuertoConfig.guardar(archivo, new HarmonyPuertoConfig.DatosPuerto(
                9090, actual.host(), actual.urlFirma(), actual.apiKey(), actual.puntoEmision(),
                actual.instanciaSql(), actual.usuarioSql(), actual.claveSql()));

        HarmonyPuertoConfig.DatosPuerto datos = HarmonyPuertoConfig.cargar(archivo);
        assertEquals(9090, datos.puerto());
        assertEquals("https://firma.ejemplo/api/factura", datos.urlFirma());
        assertEquals("llave", datos.apiKey());
        assertEquals("localhost\\SQLEXPRESS", datos.instanciaSql());
        assertEquals("sa", datos.usuarioSql());
        assertEquals("clave\\secreta", datos.claveSql());
    }

    @Test
    void armaLaUrlDeUnaInstanciaConNombre() {
        assertEquals(
                "jdbc:sqlserver://localhost;instanceName=SQLEXPRESS;databaseName=Zonak;encrypt=true;trustServerCertificate=true",
                HarmonyPuertoConfig.urlJdbc(".\\SQLEXPRESS", "Zonak"));
        assertEquals(
                "jdbc:sqlserver://localhost:1433;databaseName=Zonak;encrypt=true;trustServerCertificate=true",
                HarmonyPuertoConfig.urlJdbc("localhost,1433", "Zonak"));
    }

    @Test
    void rechazaUnaUrlDeFirmaSinProtocolo() {
        assertThrows(IllegalArgumentException.class, () -> HarmonyPuertoConfig.validarUrlFirma("10.1.1.20/api"));
        assertEquals("", HarmonyPuertoConfig.validarUrlFirma("  "));
    }
}
