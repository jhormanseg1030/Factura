package com.prueba.factura.Services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class ProbadorCufe {

    public static void main(String[] args) {
        // 1. Datos de prueba (extraídos del XML o JSON)
        String numFac = "P660500";
        String fecFac = "2026-09-02";
        String horFac = "15:15:34-05:00";
        String valFac = formatoEntero(411667.00);      // Base imponible
        String codImp1 = "01";          // IVA
        String valImp1 = formatoEntero(0.00);
        String codImp2 = "04";          // INC
        String valImp2 = formatoEntero(32933.00);
        String codImp3 = "03";          // ICA
        String valImp3 = formatoEntero(0.00);
        String valTot = formatoEntero(444600.00);      // Total fiscal (Base + Impuestos, SIN propina)
        String nitOfe = "8605108638";
        String numAdq = "2222222222";
        String claveTecnica = "e3e0eb95b191080c608175f03c5d01fc5e8699f6be293892fcceac2b51fb2822";
        String tipoAmbiente = "2";

        // 2. Construir la cadena plana con valores enteros .00
        String cadenaPlana = numFac + fecFac + horFac + valFac
            + codImp1 + valImp1
            + codImp2 + valImp2
            + codImp3 + valImp3
            + valTot + nitOfe + numAdq + claveTecnica + tipoAmbiente;

        // 3. Generar el Hash SHA-384
        String cufeCalculado = generarSha384(cadenaPlana);

        // 4. Imprimir reporte de verificación
        System.out.println("=== VERIFICADOR DE CUFE ===");
        System.out.println("• NumFac       : " + numFac);
        System.out.println("• FecFac       : " + fecFac);
        System.out.println("• HorFac       : " + horFac);
        System.out.println("• ValFac (Base): " + valFac);
        System.out.println("• ValImp (INC) : " + valImp2);
        System.out.println("• ValTot (Fisc): " + valTot);
        System.out.println("----------------------------------------");
        System.out.println("CADENA PLANA ENVIADA AL HASH:");
        System.out.println(cadenaPlana);
        System.out.println("----------------------------------------");
        System.out.println("CUFE GENERADO SHA-384:");
        System.out.println(cufeCalculado);
    }

    private static String formatoEntero(double valor) {
        return String.format(Locale.US, "%.2f", (double) Math.round(valor));
    }

    private static String generarSha384(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-384");
            byte[] bytes = md.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error calculando SHA-384", e);
        }
    }
}
