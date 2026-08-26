package com.prueba.factura;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.prueba.factura.Services.DianXmlParserService;
import com.prueba.factura.dto.DianFacturaDto;

class DianXmlParserServiceTest {

    @Test
    void shouldParseDianXml() {
        File xml = new File(getClass().getClassLoader().getResource("test_dian.xml").getFile());
        assertTrue(xml.exists(), "El XML de prueba DIAN debe existir");

        DianXmlParserService parser = new DianXmlParserService();
        DianFacturaDto factura = parser.parsearXml(xml);

        assertEquals("NOTA DE CREDITO ELECTRONICA", factura.getTipoDocumento());
        assertEquals("SF1392", factura.getNumeroDocumento());
        assertNotNull(factura.getCufe());
        assertEquals("Inverleoka S.A.S", factura.getRazonSocialEmisor());
        assertEquals("8605108638", factura.getRucEmisor());
        assertEquals("Autorizado", factura.getEstadoComprobante());

        assertEquals("Consumidor final", factura.getClienteNombre());
        assertEquals("222222222222", factura.getClienteIdentificacion());

        assertEquals("103148.00", factura.getTotalSinImpuestos());
        assertEquals("121714.84", factura.getTotalComprobante());
        assertEquals("10315", factura.getPropina());
        assertEquals("COP", factura.getMoneda());

        assertEquals(8, factura.getDetalles().size());
        assertEquals("Parfait", factura.getDetalles().get(0).getDescripcionProducto());
        assertEquals("1", factura.getDetalles().get(0).getCantidad());

        assertEquals(1, factura.getImpuestos().size());
        assertEquals("INC 8%", factura.getImpuestos().get(0).getNombreImpuesto());

        assertEquals(1, factura.getPagos().size());
        assertEquals("Tarjeta", factura.getPagos().get(0).getFormaPago());

        assertEquals("18764106468802", factura.getResolucion());
        assertEquals("SF", factura.getPrefijo());
    }

    @Test
    void shouldExtractBasicData() {
        File xml = new File(getClass().getClassLoader().getResource("test_dian.xml").getFile());

        DianXmlParserService parser = new DianXmlParserService();
        Map<String, String> datos = parser.extraerDatosBasicos(xml);

        assertEquals("NOTA DE CREDITO ELECTRONICA", datos.get("tipo_documento"));
        assertEquals("SF1392", datos.get("numero_documento"));
        assertEquals("Inverleoka S.A.S", datos.get("razon_social_emisor"));
        assertEquals("121714.84", datos.get("total_comprobante"));
        assertEquals("8", datos.get("cantidad_items"));
    }
}
