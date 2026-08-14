package com.prueba.factura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.prueba.factura.Services.MiddlewareSimphony;

class MiddlewareSimphonyTest {

    @Test
    void shouldExtractMainInvoiceFieldsFromXml() {
        File xml = new File("Pay17401_11496_340889_20260610184531.xml");

        assertTrue(xml.exists(), "El XML de prueba debe existir en la raíz del proyecto");

        Map<String, String> factura = MiddlewareSimphony.extraerDatosFactura(xml);

        assertEquals("11496", factura.get("numero_ticket"));
        assertEquals("PitaSchnitzel", factura.get("producto"));
        assertEquals("1", factura.get("cantidad"));
        assertEquals("49800.00", factura.get("precio_unitario"));
        assertEquals("49800.00", factura.get("subtotal"));
        assertEquals("4611.00", factura.get("propina"));
        assertEquals("54411.00", factura.get("total"));
        assertEquals("Pravda", factura.get("restaurante"));
        assertEquals("17401", factura.get("workstation_id"));
        assertEquals("Aux1Pravda", factura.get("workstation_nombre"));
        assertEquals("TRUE", factura.get("genera_documento"));
    }
}
