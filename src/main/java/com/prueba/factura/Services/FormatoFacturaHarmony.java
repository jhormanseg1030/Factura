package com.prueba.factura.Services;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Traduce el ticket de {@link Impresion#imprimirFactura} a las líneas que Harmony imprime.
 * No cambia el formato ni el método de impresión local.
 */
public final class FormatoFacturaHarmony {

    private static final String SEPARADOR = "_________________________________________";
    private static final String QR_DIAN = "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=";

    private FormatoFacturaHarmony() {
    }

    public static String exito(JsonNode datos) {
        return envolver(datos, "1", "", lineas(datos));
    }

    public static String sinDocumento(JsonNode datos) {
        return envolver(datos, "1", "", List.of());
    }

    public static String rechazo(String motivo) {
        return rechazo(motivo, "");
    }

    public static String rechazo(String motivo, String checkNum) {
        JsonNode datos = JsonNodeFactory.instance.objectNode().put("numero_ticket", checkNum == null ? "" : checkNum);
        return envolver(datos, "0", motivo == null ? "Error al procesar el payload" : motivo, List.of());
    }

    private static List<String> lineas(JsonNode datos) {
        List<String> lineas = new ArrayList<>();
        texto(lineas, datos.path("RazonSocial").asText("N/A"));
        texto(lineas, datos.path("restaurante").asText("N/A"));
        vacia(lineas);
        texto(lineas, datos.path("RucEmisor").asText("N/A"));
        texto(lineas, datos.path("Direccion").asText("N/A"));
        texto(lineas, datos.path("Tel").asText("N/A"));
        for (String linea : TextosFactura.lineas(datos, "textos_fiscales", TextosFactura.FISCALES)) {
            if (linea.isBlank()) {
                vacia(lineas);
            } else {
                texto(lineas, linea);
            }
        }
        vacia(lineas);
        texto(lineas, SEPARADOR);
        vacia(lineas);

        etiqueta(lineas, "cliente", datos.path("cliente").asText("N/A"));
        etiqueta(lineas, "NIT/CC", datos.path("identificacion_cliente").asText("N/A"));
        etiqueta(lineas, "Dirección", datos.path("Dirección").asText("N/A"));
        etiqueta(lineas, "Telefono", datos.path("Telefono").asText("N/A"));
        etiqueta(lineas, "Fecha de Generacion", datos.path("Fecha de Generacion").asText("N/A"));
        vacia(lineas);

        etiqueta(lineas, "Mesa", datos.path("Mesa").asText("N/A"));
        etiqueta(lineas, "Cajero", datos.path("Cajero").asText("N/A"));
        etiqueta(lineas, "Chk", datos.path("numero_ticket").asText("N/A"));
        etiqueta(lineas, "Caja", datos.path("caja_wsid").asText("N/A"));
        texto(lineas, SEPARADOR);

        texto(lineas, "Cant Descripcion Precio Total");
        JsonNode items = datos.path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                String cant = item.path("cantidad").asText("1");
                String codigo = item.path("Codigo").asText(item.path("codigo").asText("0.00"));
                String nombre = item.path("Descripcion").asText(item.path("descripcion").asText("N/A"));
                String precio = item.path("Precio").asText(item.path("precio").asText("0.00"));
                String total = item.path("Total").asText(item.path("total").asText("0.00"));
                boolean tieneCodigo = codigo != null && !codigo.trim().isEmpty();
                if (tieneCodigo) {
                    texto(lineas, cant + " " + codigo.trim());
                    if (nombre != null && !nombre.trim().isEmpty()) {
                        texto(lineas, nombre + " " + precio + " " + total);
                    }
                } else {
                    texto(lineas, cant + " " + nombre + " " + precio + " " + total);
                }
            }
        }
        texto(lineas, SEPARADOR);

        espacio(lineas, "Base", datos.path("Base").asText("0.00"));
        espacio(lineas, "INC", datos.path("INC").asText("0.00"));
        espacio(lineas, "Total", datos.path("Subtotal").asText("0.00"));
        espacio(lineas, "Propina", datos.path("Propina").asText("0.00"));
        espacio(lineas, "Total", datos.path("Total").asText("0.00"));
        texto(lineas, SEPARADOR);

        JsonNode pagos = datos.path("pagos");
        if (pagos.isArray()) {
            for (JsonNode pago : pagos) {
                etiqueta(lineas, "Forma de Pago", pago.path("Forma de Pago").asText("N/A"));
                etiqueta(lineas, "Medio de Pago", pago.path("tenderName").asText("N/A"));
            }
        }
        texto(lineas, SEPARADOR);

        etiqueta(lineas, "Resolucion DIAN", datos.path("Resolucion").asText("N/A"));
        etiqueta(lineas, "Fecha Resolucion", datos.path("Fecha Resolucion").asText("N/A"));
        etiqueta(lineas, "Fecha Inicial", datos.path("Fecha Inicial").asText("N/A"));
        etiqueta(lineas, "Fecha Final", datos.path("Fecha Final").asText("N/A"));
        etiqueta(lineas, "Rango Inicial", datos.path("Rango Inicial").asText("N/A"));
        etiqueta(lineas, "Rango Final", datos.path("Rango Final").asText("N/A"));
        texto(lineas, SEPARADOR);

        for (String linea : TextosFactura.lineas(datos, "pie_factura", TextosFactura.PIE)) {
            if (linea.isBlank()) {
                vacia(lineas);
            } else {
                texto(lineas, linea);
            }
        }

        String cufe = datos.path("cufeGenerado").asText("N/A");
        if (!"N/A".equals(cufe) && !cufe.isBlank()) {
            lineas.add("<OraPayloadPrintData type=\"QRCode\" correctionLevel=\"Level_L\" size=\"4\" model=\"Model2\" justification=\"Center\">"
                    + escapar(QR_DIAN + cufe) + "</OraPayloadPrintData>");
            vacia(lineas);
            texto(lineas, "CUFE:");
            int bloque = 32;
            for (int i = 0; i < cufe.length(); i += bloque) {
                int fin = Math.min(i + bloque, cufe.length());
                texto(lineas, cufe.substring(i, fin));
            }
        }

        lineas.add("<OraPayloadPrintData type=\"CutPaper\"/>");
        return lineas;
    }

    private static String envolver(JsonNode datos, String codigo, String motivo, List<String> lineas) {
        String wsId = datos == null ? "" : datos.path("caja_wsid").asText("");
        String harmonyId = datos == null ? "" : datos.path("harmony_id").asText("");
        String checkNum = datos == null ? "" : datos.path("numero_ticket").asText("");
        String timestamp = datos == null ? "" : datos.path("fecha_hora").asText("");
        if (timestamp.isBlank()) {
            timestamp = OffsetDateTime.now(ZoneOffset.of("-05:00")).toString();
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<OraPayloadResponseEntity><Response");
        xml.append(" Timestamp=\"").append(escapar(timestamp)).append("\"");
        xml.append(" WsID=\"").append(escapar(wsId)).append("\"");
        xml.append(" HarmonyId=\"").append(escapar(harmonyId)).append("\"");
        xml.append(" CheckNum=\"").append(escapar(checkNum)).append("\">");
        xml.append("<Header><OraPayloadResponseCode>").append(escapar(codigo)).append("</OraPayloadResponseCode>");
        xml.append("<OraPayloadResponseRejectReason>").append(escapar(motivo)).append("</OraPayloadResponseRejectReason></Header>");
        xml.append("<Printing><PrintDataList>");
        for (String linea : lineas) {
            xml.append(linea);
        }
        xml.append("</PrintDataList></Printing></Response></OraPayloadResponseEntity>");
        return xml.toString();
    }

    private static void texto(List<String> lineas, String valor) {
        lineas.add("<OraPayloadPrintData type=\"String\">" + escapar(valor) + "</OraPayloadPrintData>");
    }

    private static void vacia(List<String> lineas) {
        lineas.add("<OraPayloadPrintData type=\"String\"/>");
    }

    private static void etiqueta(List<String> lineas, String etiqueta, String valor) {
        texto(lineas, etiqueta + ": " + valor);
    }

    private static void espacio(List<String> lineas, String etiqueta, String valor) {
        texto(lineas, etiqueta + " " + valor);
    }

    private static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
