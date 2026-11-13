package com.prueba.factura.Services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class MiddlewareSimphonyPortalTest {

    @Test
    void separaElNumeroYElPrefijoQueEsperaElPortal() {
        assertEquals("SETP", MiddlewareSimphony.prefijoDe("SETP990000009"));
        assertEquals(990000009L, MiddlewareSimphony.numeroDe("SETP990000009"));
    }

    @Test
    void armaElClienteComoObjeto() {
        Map<String, String> consumidor = MiddlewareSimphony.clienteParaPortal(null, "2222222222");
        assertEquals("13", consumidor.get("tipo_identificacion"));
        assertEquals("2222222222", consumidor.get("numero_identificacion"));
        assertEquals("CONSUMIDOR FINAL", consumidor.get("razon_social"));

        ObjectNode cliente = new ObjectMapper().createObjectNode();
        cliente.put("identificacion", "123");
        cliente.put("nombre", "Ana");
        cliente.put("apellido", "Perez");
        Map<String, String> portal = MiddlewareSimphony.clienteParaPortal(cliente, "2222222222");

        assertEquals("13", portal.get("tipo_identificacion"));
        assertEquals("123", portal.get("numero_identificacion"));
        assertEquals("Ana Perez", portal.get("razon_social"));
    }

    @Test
    void elPuntoDeTresDigitosEsSeparadorDeMiles() {
        assertEquals(1852L, MiddlewareSimphony.parseDineroEntero("1.852"));
        assertEquals(20000L, MiddlewareSimphony.parseDineroEntero("20.000"));
        assertEquals(21852L, MiddlewareSimphony.parseDineroEntero("21.852"));
        assertEquals(20000L, MiddlewareSimphony.parseDineroEntero("20,000.00"));
        assertEquals(4611L, MiddlewareSimphony.parseDineroEntero("4611.00"));
        assertEquals(1481L, MiddlewareSimphony.parseDineroEntero("1481.481481"));
    }

    @Test
    void elJsonDelPortalUsaLaMismaFormaEnImpuestosYEnElPago() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("RucEmisor", "N/A");
        json.put("Propina", 3296);
        json.put("propina", 3296);
        json.put("Total", 38896);
        json.put("Subtotal", 35600);
        json.put("items", mapper.readTree("""
                [{"codigo":"200001","nombre":"AgPanna1000Pet","descripcion":"AgPanna1000Pet","cantidad":1,"precio":18519,"precio_unitario":18519,"Total":20000,"total":20000,"subtotal":18519,"descuento":0,"impuestos":[{"codigo":"04","nombre":"INC 8%","numero_impuesto":"1","porcentaje":"8.00","base":"18519.00","valor":"1481.00"}]}]
                """));
        json.put("impuestos", mapper.readTree("""
                [{"codigo_impuesto":"04","nombre_impuesto":"INC 8%","numero_impuesto":"1","porcentaje_impuesto":"8.00","base_imponible":"32963.00","monto_impuesto":"2637.00"}]
                """));
        json.put("pagos", mapper.readTree("""
                [{"tenderMediaId":2,"tenderName":"Efectivo","tenderAmount":50000,"valor":50000,"tipAmount":0,"forma_pago":"10","medio_pago":"Efectivo"}]
                """));

        MiddlewareSimphony.aplicarFormaPortal(json);

        @SuppressWarnings("unchecked")
        Map<String, Object> item = ((List<Map<String, Object>>) json.get("items")).get(0);
        assertEquals(18519L, item.get("precio"));
        assertEquals(18519L, item.get("precio_unitario"));
        assertEquals(18519L, item.get("subtotal"));
        assertEquals(20000L, item.get("total"));
        @SuppressWarnings("unchecked")
        Map<String, String> impuestoItem = ((List<Map<String, String>>) item.get("impuestos")).get(0);
        assertEquals("18519.00", impuestoItem.get("base"));
        assertFalse(impuestoItem.containsKey("numero_impuesto"));

        @SuppressWarnings("unchecked")
        Map<String, String> impuesto = ((List<Map<String, String>>) json.get("impuestos")).get(0);
        assertEquals("04", impuesto.get("codigo"));
        assertEquals("32963.00", impuesto.get("base"));
        assertEquals("2637.00", impuesto.get("valor"));
        assertEquals("1", impuesto.get("numero_impuesto"));
        assertFalse(impuesto.containsKey("base_imponible"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pago = ((List<Map<String, Object>>) json.get("pagos")).get(0);
        assertEquals(38896L, pago.get("valor"));
        assertEquals(3296L, pago.get("propina"));
        assertEquals("Efectivo", pago.get("medio_pago"));
        assertEquals("10", pago.get("forma_pago"));
        assertFalse(json.containsKey("RucEmisor"));
        assertFalse(json.containsKey("Propina"));
    }

    @Test
    void laEmpresaConfiguradaQuedaEnElJsonYLosTextosNo() {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("RazonSocial", "Caja");
        datos.put("RucEmisor", "");
        datos.put("Direccion", "Local");
        datos.put("Telefono", "111");
        MiddlewareSimphony.aplicarEmisor(datos, new SecuenciaService.DatosEmisor(
                "Zona K SAS", "860510863", "Calle 10", "6015550101",
                "Gran Contribuyente ICA", "Gracias"));

        assertEquals("Zona K SAS", datos.get("RazonSocial"));
        assertEquals("860510863", datos.get("RucEmisor"));
        assertEquals("Calle 10", datos.get("Direccion"));
        assertEquals("6015550101", datos.get("Telefono"));

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("RucEmisor", datos.get("RucEmisor"));
        json.put("RazonSocial", datos.get("RazonSocial"));
        json.put("Direccion", datos.get("Direccion"));
        json.put("Tel", datos.get("Telefono"));
        json.put("Total", 20000);
        json.put("propina", 0);
        json.put("textos_fiscales", "Gran Contribuyente ICA");
        json.put("pie_factura", "Gracias");
        MiddlewareSimphony.aplicarFormaPortal(json);

        assertEquals("860510863", json.get("RucEmisor"));
        assertEquals("Zona K SAS", json.get("RazonSocial"));
        assertEquals("Calle 10", json.get("Direccion"));
        assertEquals("6015550101", json.get("Tel"));
        assertFalse(json.containsKey("textos_fiscales"));
        assertFalse(json.containsKey("pie_factura"));
    }

    @Test
    void laNotaCreditoNoSaleComoFactura() {
        Map<String, Object> nota = new LinkedHashMap<>();
        MiddlewareSimphony.marcarDocumento(nota, "SETP990000028", "06/11/2026", "PRUEBA", "cufe-original");

        assertEquals("91", nota.get("DocumentType"));
        assertEquals("91", nota.get("tipo_documento"));
        assertEquals("2", nota.get("credit_note_type_code"));
        assertEquals("20", nota.get("tipo_operacion"));
        assertEquals("SETP990000028", nota.get("CreditNoteNumber"));
        assertEquals("06/11/2026", nota.get("CreditNoteInvoiceDate"));
        assertEquals("PRUEBA", nota.get("CreditNoteReason"));
        assertEquals("SETP", nota.get("prefijo_referencia"));
        assertEquals(990000028L, nota.get("numero_referencia"));
        @SuppressWarnings("unchecked")
        Map<String, String> referencia = (Map<String, String>) nota.get("factura_referencia");
        assertEquals("SETP990000028", referencia.get("numeroDocumento"));
        assertEquals("cufe-original", referencia.get("cufe"));

        Map<String, Object> factura = new LinkedHashMap<>();
        MiddlewareSimphony.marcarDocumento(factura, "", null, null, null);
        assertEquals("01", factura.get("DocumentType"));
        assertEquals("FV", factura.get("tipo_documento"));
        assertFalse(factura.containsKey("credit_note_type_code"));
        assertFalse(factura.containsKey("tipo_operacion"));
        assertFalse(factura.containsKey("numero_referencia"));
        assertFalse(factura.containsKey("factura_referencia"));
    }

    @Test
    void elCufeDeLaFacturaOriginalSeBuscaPorElNumero() {
        Map<String, Map<String, String>> registros = new java.util.LinkedHashMap<>();
        registros.put("guid:1", Map.of("numero_factura_completo", "SETP990000029", "cufe", "cufe-29"));
        registros.put("guid:2", Map.of("numero_factura_completo", "NC990000001", "cufe", "cufe-nc"));
        assertEquals("cufe-29", MiddlewareSimphony.cufeDeNumero(registros, "SETP990000029"));
        assertEquals("", MiddlewareSimphony.cufeDeNumero(registros, "SETP990000099"));
    }

    @Test
    void laNotaCreditoIgualaLaBaseConLaSumaDeLasLineas() {
        Map<String, Object> nota = new LinkedHashMap<>();
        nota.put("tipo_documento", "NC");
        nota.put("Base", 43400L);
        nota.put("INC", 0L);
        nota.put("Subtotal", 43400L);
        nota.put("Total", 47419L);
        nota.put("items", java.util.List.of(
                linea(5370L, 5800L, "430.00"),
                linea(18519L, 20000L, "1481.00"),
                linea(5370L, 5800L, "430.00"),
                linea(10926L, 11800L, "874.00")));
        Map<String, String> cabecera = new LinkedHashMap<>();
        cabecera.put("codigo", "04");
        cabecera.put("base", "43400.00");
        cabecera.put("valor", "0.00");
        nota.put("impuestos", java.util.List.of(cabecera));

        MiddlewareSimphony.cuadrarNotaCredito(nota);

        assertEquals(40185L, nota.get("Base"));
        assertEquals(40185L, nota.get("Subtotal"));
        assertEquals(3215L, nota.get("INC"));
        assertEquals(47419L, nota.get("Total"));
        assertEquals("40185.00", cabecera.get("base"));
        assertEquals("3215.00", cabecera.get("valor"));
    }

    private static Map<String, Object> linea(long base, long total, String valorImpuesto) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("precio", base);
        item.put("subtotal", base);
        item.put("total", total);
        item.put("impuestos", java.util.List.of(java.util.Map.of("base", base + ".00", "valor", valorImpuesto)));
        return item;
    }
}
