package com.prueba.factura.Services;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

final class TextosFactura {

    static final String FISCALES = """
            Act 5611 13.8x1000
            Act 5630 13.8x1000
            Act 5613 13.8x1000
            Act 5619 13.8x1000
            Act 4711 4.14x1000
            Gran Contribuyente ICA
            Agente Retenedor de IVA""".strip();

    static final String PIE = """
            Impuestos Incluidos
            Advertencia de Propina
            En la parte posterior de este documento

            Implementado Por Inverleoka SAS
            NIT 8605108638
            Integrador: Hospitality
            Restaurants Automation SAS
            NIT 901518527-2""".strip();

    private TextosFactura() {
    }

    static List<String> lineas(JsonNode datos, String campo, String defecto) {
        if (datos == null || !datos.has(campo) || datos.get(campo).isNull()) {
            return partir(defecto);
        }
        return partir(datos.get(campo).asText(""));
    }

    static List<String> partir(String texto) {
        if (texto == null || texto.isEmpty()) {
            return List.of();
        }
        String normalizado = texto.replace("\r\n", "\n").replace('\r', '\n');
        if (normalizado.endsWith("\n")) {
            normalizado = normalizado.substring(0, normalizado.length() - 1);
        }
        List<String> lineas = new ArrayList<>();
        for (String linea : normalizado.split("\n", -1)) {
            lineas.add(linea.strip());
        }
        return lineas;
    }
}
