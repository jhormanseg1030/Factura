package com.prueba.factura.Services;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.regex.Matcher;

import com.prueba.factura.dto.FacturaDto;

@Service
public class TicketParserService {

    public FacturaDto parseTicket(String ticketContenido){
        if (ticketContenido == null || ticketContenido.length() > 100_000) {
            throw new IllegalArgumentException("Contenido de ticket inválido");
        }
        
        FacturaDto factura = new FacturaDto();

        // Validar entrada antes de regex
        String sanitized = ticketContenido.replaceAll("[^a-zA-Z0-9\\s\\-:]", "");
        
        Pattern cufePattern = Pattern.compile("(?i)(?:cufe|cudf):\\s*([a-f0-9]{96}|[a-f0-9]{110})");
        Matcher cufeMatcher = cufePattern.matcher(sanitized);
        
        if(cufeMatcher.find()){
            String capturaCufe = cufeMatcher.group(1).trim();

            if(capturaCufe.length() == 96 || capturaCufe.length() == 110){
                //Esto valida que sea hexadecimal válida
                if (capturaCufe.matches("^[a-f0-9]+$")) {
                    factura.setCufe(capturaCufe);
                }
            }
        }
        
        //Extraer el Qr
        Pattern qrPattern = Pattern.compile("https?://[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=]+");
        Matcher qrMatcher = qrPattern.matcher(sanitized);
        if(qrMatcher.find()){
            factura.setQrCode(qrMatcher.group(0));
        }

        //Extraer el Check
        Pattern checkPattern = Pattern.compile("(?i)(?:check|cuenta|folio)\\s*#?:?\\s*(\\d{1,10})");
        Matcher checkMatcher = checkPattern.matcher(sanitized);
        if(checkMatcher.find()){
            factura.setCheckId(checkMatcher.group(1));
        }

        //Extraer el total
        Pattern totalPattern = Pattern.compile("(?i)total\\s*\\$?\\s*([0-9]{1,10}(?:[.,][0-9]{2})?)");
        Matcher totalMatcher = totalPattern.matcher(sanitized);
        if(totalMatcher.find()){
            factura.setTotal(totalMatcher.group(1));
        }

        factura.setItems(new ArrayList<>());

        return factura;
    }
}
