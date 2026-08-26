package com.prueba.factura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.prueba.factura.Services.MiddlewareSimphony;

class MiddlewareSimphonyTest {

    @Test
        void shouldExtractMainInvoiceFieldsFromXml() throws Exception {
                String xmlContenido = """
                        <Root>
                            <Check CheckNum=\"11496\" Id=\"340889\" HarmonyId=\"H001\" WsID=\"17401\" Timestamp=\"2026-06-10T18:45:31\" />
                            <OraPayloadEntityMI>
                                <OraPayloadEntityField field=\"Name\" value=\"PitaSchnitzel\"/>
                                <OraPayloadEntityField field=\"SalesCount\" value=\"1\"/>
                                <OraPayloadEntityField field=\"UnitPrice\" value=\"49800.00\"/>
                                <OraPayloadEntityField field=\"Total\" value=\"49800.00\"/>
                            </OraPayloadEntityMI>
                            <OraPayloadEntityField field=\"CheckAutoServiceCharge\" value=\"4611.00\"/>
                            <OraPayloadEntityField field=\"CheckTotalDue\" value=\"54411.00\"/>
                            <OraPayloadEntityField field=\"PropertyName\" value=\"Pravda\"/>
                            <OraPayloadEntityField field=\"WorkstationName\" value=\"Aux1Pravda\"/>
                            <OraPayloadEntityField field=\"DE_SATCOM_Genera_Documento\" value=\"TRUE\"/>
                        </Root>
                        """;

                Path temporal = Files.createTempFile("middleware-simphony-", ".xml");
                Files.writeString(temporal, xmlContenido, StandardCharsets.UTF_8);

                MiddlewareSimphony middleware = new MiddlewareSimphony();
                Map<String, String> factura = middleware.extraerDatosFactura(temporal.toFile());

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

        Files.deleteIfExists(temporal);
    }

    @Test
    void shouldFailWhenCheckTagIsMissing() throws Exception {
        String xmlSinCheck = """
            <Root>
              <OraPayloadEntityField field=\"PropertyName\" value=\"Pravda\"/>
            </Root>
            """;

        Path temporal = Files.createTempFile("middleware-simphony-invalid-", ".xml");
        Files.writeString(temporal, xmlSinCheck, StandardCharsets.UTF_8);

        MiddlewareSimphony middleware = new MiddlewareSimphony();
        assertThrows(IllegalStateException.class, () -> middleware.extraerDatosFactura(temporal.toFile()));

        Files.deleteIfExists(temporal);
    }

        @Test
        void shouldExtractSatcomTaxesFromXml() throws Exception {
                String xmlContenido = """
                        <Root>
                            <Check CheckNum="11496" />
                            <OraPayloadEntityField field="CheckSubtotal" value="100.00"/>
                            <OraPayloadEntityField field="TaxRates" type="List">
                                <GenericParameterList>
                                    <OraPayloadEntityFieldGenericParameter field="DE_SATCOM_CodigoImpuesto" value="04"/>
                                    <OraPayloadEntityFieldGenericParameter field="DE_SATCOM_NombreImpuesto" value="INC 8%"/>
                                    <OraPayloadEntityFieldGenericParameter field="DE_SATCOM_Numero_Impuestos" value="1"/>
                                    <OraPayloadEntityFieldGenericParameter field="DE_SATCOM_Porc_Impuestos" value="8,00"/>
                                </GenericParameterList>
                            </OraPayloadEntityField>
                        </Root>
                        """;

                Path temporal = Files.createTempFile("middleware-simphony-tax-", ".xml");
                Files.writeString(temporal, xmlContenido, StandardCharsets.UTF_8);

                MiddlewareSimphony middleware = new MiddlewareSimphony();
                Map<String, String> factura = middleware.extraerDatosFactura(temporal.toFile());

                assertTrue(factura.get("impuestos_json").contains("\"codigo_impuesto\":\"04\""));
                assertTrue(factura.get("impuestos_json").contains("\"nombre_impuesto\":\"INC 8%\""));
                assertTrue(factura.get("impuestos_json").contains("\"porcentaje_impuesto\":\"8.00\""));
                assertTrue(factura.get("impuestos_json").contains("\"base_imponible\":\"92.59\""));
                assertTrue(factura.get("impuestos_json").contains("\"monto_impuesto\":\"7.41\""));

                Files.deleteIfExists(temporal);
        }

    @Test
    void shouldFailWhenFileDoesNotExist() {
        MiddlewareSimphony middleware = new MiddlewareSimphony();
        File inexistente = new File("C:/ruta/inexistente/prueba.xml");

        assertThrows(IllegalStateException.class, () -> middleware.extraerDatosFactura(inexistente));
    }
}
