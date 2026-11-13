package com.prueba.factura.Services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class FormatoFacturaHarmonyTest {

    @Test
    void conservaLasLineasDelFormatoActual() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode datos = mapper.createObjectNode();
        datos.put("RazonSocial", "Zona K");
        datos.put("restaurante", "Pravda");
        datos.put("RucEmisor", "860510863");
        datos.put("Direccion", "Calle 1");
        datos.put("Tel", "300");
        datos.put("cliente", "Consumidor Final");
        datos.put("identificacion_cliente", "2222222222");
        datos.put("Dirección", " ");
        datos.put("Telefono", "");
        datos.put("Fecha de Generacion", "2026-05-05 10:14:15");
        datos.put("Mesa", "N/A");
        datos.put("Cajero", "Yuliana");
        datos.put("numero_ticket", "60029346");
        datos.put("caja_wsid", "36259");
        datos.put("harmony_id", "623010");
        datos.put("fecha_hora", "2026-05-05T10:14:15-05:00");
        datos.put("Base", "13240.00");
        datos.put("INC", "1059.00");
        datos.put("Subtotal", "14300.00");
        datos.put("Propina", "0.00");
        datos.put("Total", "14300.00");
        datos.put("Resolucion", "18764104647329");
        datos.put("Fecha Resolucion", "2026-01-16");
        datos.put("Fecha Inicial", "2026-01-16");
        datos.put("Fecha Final", "2027-01-16");
        datos.put("Rango Inicial", "DM89750");
        datos.put("Rango Final", "DM500000");
        datos.put("cufeGenerado", "abc123");

        ArrayNode items = datos.putArray("items");
        ObjectNode item = items.addObject();
        item.put("cantidad", "1");
        item.put("codigo", "100580");
        item.put("descripcion", "Donut");
        item.put("precio", "5370.00");
        item.put("Total", "5370.00");

        ArrayNode pagos = datos.putArray("pagos");
        ObjectNode pago = pagos.addObject();
        pago.put("tenderName", "Tarjeta");

        String xml = FormatoFacturaHarmony.exito(datos);

        assertTrue(xml.contains("<OraPayloadResponseCode>1</OraPayloadResponseCode>"));
        assertTrue(xml.contains("WsID=\"36259\""));
        assertTrue(xml.contains("HarmonyId=\"623010\""));
        assertTrue(xml.contains("CheckNum=\"60029346\""));
        assertTrue(xml.contains(">Zona K</OraPayloadPrintData>"));
        assertTrue(xml.contains("cliente: Consumidor Final"));
        assertTrue(xml.contains("NIT/CC: 2222222222"));
        assertTrue(xml.contains("Act 5611 13.8x1000"));
        assertTrue(xml.contains("Gran Contribuyente ICA"));
        assertTrue(xml.contains("Cant Descripcion Precio Total"));
        assertTrue(xml.contains(">1 100580</OraPayloadPrintData>"));
        assertTrue(xml.contains("Donut 5370.00 5370.00"));
        assertTrue(xml.contains("Base 13240.00"));
        assertTrue(xml.contains("Forma de Pago: N/A"));
        assertTrue(xml.contains("Medio de Pago: Tarjeta"));
        assertTrue(xml.contains("Resolucion DIAN: 18764104647329"));
        assertTrue(xml.contains("Implementado Por Inverleoka SAS"));
        assertTrue(xml.contains("NIT 901518527-2"));
        assertTrue(xml.contains("type=\"QRCode\""));
        assertTrue(xml.contains("documentkey=abc123"));
        assertTrue(xml.contains(">CUFE:</OraPayloadPrintData>"));
        assertTrue(xml.contains("type=\"CutPaper\""));
        assertFalse(xml.contains("type=\"CutPaper\"/><OraPayloadPrintData type=\"String\">Zona K"));
    }

    @Test
    void usaLosTextosConfiguradosEnElEncabezadoYElPie() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode datos = mapper.createObjectNode();
        datos.put("textos_fiscales", "Responsable de IVA");
        datos.put("pie_factura", "Gracias por su visita");

        String xml = FormatoFacturaHarmony.exito(datos);

        assertTrue(xml.contains("Responsable de IVA"));
        assertTrue(xml.contains("Gracias por su visita"));
        assertFalse(xml.contains("Act 5611 13.8x1000"));
        assertFalse(xml.contains("Implementado Por Inverleoka SAS"));
    }

    @Test
    void elTicketImprimeElMismoNumeroQueElJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode datos = mapper.createObjectNode();
        datos.put("Propina", 1852);
        datos.put("Base", 18519);
        datos.put("INC", 1481);
        datos.put("Subtotal", 20000);
        datos.put("Total", 21852);
        ArrayNode items = datos.putArray("items");
        ObjectNode item = items.addObject();
        item.put("cantidad", 1);
        item.put("codigo", "200001");
        item.put("descripcion", "AgPanna1000Pet");
        item.put("precio", 18519);
        item.put("Total", 20000);

        String xml = FormatoFacturaHarmony.exito(datos);

        assertTrue(xml.contains("AgPanna1000Pet 18519 20000"));
        assertTrue(xml.contains("Base 18519"));
        assertTrue(xml.contains("INC 1481"));
        assertTrue(xml.contains("Propina 1852"));
        assertTrue(xml.contains("Total 21852"));
    }

    @Test
    void elRechazoLlevaElNumeroDelCheque() {
        String xml = FormatoFacturaHarmony.rechazo("Error al procesar el payload", "10010");

        assertTrue(xml.contains("<OraPayloadResponseCode>0</OraPayloadResponseCode>"));
        assertTrue(xml.contains("CheckNum=\"10010\""));
        assertTrue(xml.contains("Error al procesar el payload"));
    }
}
