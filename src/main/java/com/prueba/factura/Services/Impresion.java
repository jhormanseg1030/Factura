package com.prueba.factura.Services;

import java.io.OutputStream;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

@Service 
public class Impresion {

    @Value("${impresora.ip}")
    private String ipImpresora;

    @Value("${impresora.puerto}")
    private int puerto;

    private static final Logger logger = LoggerFactory.getLogger(Impresion.class);

    public void imprimirFactura(JsonNode datos){
        logger.info(">>> INTENTANDO CONECTAR A IP: [{}] Y PUERTO: [{}]", ipImpresora, puerto);
        try(Socket socket = new Socket(ipImpresora, puerto);
        OutputStream out = socket.getOutputStream()){

            out.write(new byte[] {0x1B, 0x40});

            byte[] izquierda = new byte[] {0x1B, 0x61,0x00};
            byte[] centro = new byte[] {0x1B, 0X61, 0X01};
            byte[] negrita = new byte[] {0x1B, 0x45, 0x01};
            byte[] noNegrita = new byte[] {0x1B, 0x45, 0x00};
            byte[] cortarPapel = new byte[] {0x1D, 0x56, 0x41, 0x03};
            // Cabecera
            out.write(centro);
            out.write((datos.path("restaurante").asText("N/A") + "\n").getBytes("IBM850"));
            out.write(noNegrita);

            out.write(("\n").getBytes("IBM850"));
            
            out.write(("QR #:" + datos.path("urlQr")).getBytes("IBM850"));
            out.write(("\n").getBytes("IBM850"));
            out.write(("\n").getBytes("IBM850"));
            out.write(noNegrita);
            out.write(("CUFE #: " + datos.path("cufeGenerado")).getBytes("IBM850"));
            out.write(noNegrita);

            //cuerpo 
            out.write(centro);
            out.write(("Ticket #: " + datos.path("numero_ticket").asText("N/A")).getBytes("IBM850"));
            out.write(("Numero de la factura #: " + datos.path("numero_factura").asText("N/A")).getBytes("IBM850"));
            out.write(("Numero del Check #: " + datos.path("check_id").asText("N/A")).getBytes("IBM850"));
            out.write(("Fecha de Generacion #:" + datos.path("fecha_procesamiento").asText("N/A")).getBytes("IBM850"));
            out.write(("Tipo de Empleado #:" + datos.path("empleado").asText("N/A")).getBytes("IBM850"));
            out.write(("Tipo de Identificacion #:" + datos.path("identificacion_cliente").asText("N/A")).getBytes("IBM850"));
            out.write(("Nombre del cliente #:" + datos.path("cliente").asText("N/A")).getBytes("IBM850"));
            out.write(("\n").getBytes("IBM850"));

            JsonNode jsonNode = (JsonNode) datos.path("items"); 
            if(jsonNode.isArray()){
                for(JsonNode item : jsonNode){
                out.write(izquierda);
                out.write(("codigo : " + item.path("items").asText("N/A") + "\n").getBytes("IBM850"));
                out.write(("nombreProducto: " + item.path("nombre").asText("N/A") + "\n").getBytes("IBM850"));
                out.write(("cantidad: " + item.path("cantidad").asText("0.00") + "\n").getBytes("IBM850"));
                out.write(("precioUnitarioSinImpuesto: " + item.path("precio").asText("0,00") + "\n").getBytes("IBM850"));
                out.write(("baseImponible: " + item.path("subtotal").asText("0.00") + "\n").getBytes("IBM850"));
                out.write(("totalItem: " + item.path("total").asText("0.00") + "\n").getBytes("IBM850"));
                out.write(("\n").getBytes("IBM850"));
                }
            }   
                JsonNode pagosNode = (JsonNode) datos.path("pagos");
                if( pagosNode.isArray()){
                    for(JsonNode pago : pagosNode){
                    out.write(("nombrePago : " + pago.path("tenderName").asText("N/A") + "\n").getBytes("IBM850"));
                    out.write(("tenderAmount : " + pago.path("tenderAmount").asText("N/A") + "\n").getBytes("IBM850"));
                    out.write(("tipAmount : " + pago.path("tipAmount").asText("N/A") + "\n").getBytes("IBM850"));
                    out.write(("refNum : " + pago.path("referenceNumber").asText("N/A") + "\n").getBytes("IBM850"));
                    out.write(("\n").getBytes("IBM850"));   
                    }
                }
                out.write(("\n,\n,\n").getBytes("IBM850"));
                out.write(cortarPapel);
                out.flush();

                logger.info("Factura impresa correctamente en la impresora {}:{}", ipImpresora, puerto);

        } catch (Exception e) {
            logger.error("Error al imprimir la factura", e);
            e.printStackTrace();
        }
    }

}
