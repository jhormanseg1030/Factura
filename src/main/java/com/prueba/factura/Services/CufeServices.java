package com.prueba.factura.Services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class CufeServices {
    
    public static String generarCufe(
        String numFac,
        String fecFac,
        String horFac,
        double valFac,

        String codImp1,
        double valIva,

        String codImp2,
        double valInc,

        String codImp3,
        double valIca,

        double valTotal,
        String nitFe,
        String numAdq,
        String ciTec,
        String tipoAmbiente){

        String valFacStr = String.format(Locale.US, "%.2f", valFac);
        String valIvaStr = String.format(Locale.US , "%.2f", valIva);
        String valIncStr = String.format(Locale.US , "%.2f", valInc);
        String valIcaStr = String.format(Locale.US , "%.2f", valIca);
        String valTotalStr = String.format(Locale.US , "%.2f", valTotal);

        StringBuilder cufeBuilder = new StringBuilder();
        cufeBuilder.append(numFac)
            .append(fecFac)
            .append(horFac)
            .append(valFacStr)
            .append(codImp1).append(valIvaStr)
            .append(codImp2).append(valIncStr)
            .append(codImp3).append(valIcaStr)
            .append(valTotalStr)
            .append(nitFe)
            .append(numAdq)
            .append(ciTec)
            .append(tipoAmbiente);

        return sha384Hex(cufeBuilder.toString());
    }

    private static String sha384Hex(String input){
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-384");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();

            for(byte b : hash){
                String hex = Integer.toHexString(0xff & b);
                if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toLowerCase();
        } catch(NoSuchAlgorithmException e){
            throw new RuntimeException("Error al generar algoritmo SHA-384 para CUFE", e);
        }
    }
}
