package com.prueba.factura.Services;

import java.io.OutputStream;
import java.net.Socket;

import javax.imageio.ImageIO;


import java.awt.image.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

@Service 
public class Impresion {

    @Value("${impresora.ip}")
    private String ipImpresora;

    @Value("${impresora.puerto}")
    private int puerto;

    private static final Logger logger = LoggerFactory.getLogger(Impresion.class);

    public record Producto(String Cant, String Descripcion, String precio, String Total){}
    
    private void imprimirLineaTexto(String etiqueta, String valor, int tamanoLetra, OutputStream out) throws Exception {
        BufferedImage imgTexto = TextoImagen.crearTexto(etiqueta + ": " + valor, tamanoLetra);
        ImagenFactura.imprimirImagen(imgTexto, out);
    }

    private void sinLinea(String valor, int tamanoLetra, OutputStream out) throws Exception {
        BufferedImage imgTexto = TextoImagen.crearTexto( valor, tamanoLetra);
        ImagenFactura.imprimirImagen(imgTexto, out);
    }

    public void imprimirFactura(JsonNode datos) {

        try (Socket socket = new Socket(ipImpresora, puerto);
             OutputStream out = socket.getOutputStream()) {
                
            out.write(new byte[] {0x1B, 0x40});

            byte[] izquierda = new byte[] {0x1B, 0x61, 0x00};
            byte[] centro    = new byte[] {0x1B, 0x61, 0x01};
            byte[] cortarPapel = new byte[] {0x1D, 0x56, 0x41, 0x03};

            // Imagen de Zonak
            ClassPathResource resource = new ClassPathResource("Zonak.jpeg");
            BufferedImage logo = ImageIO.read(resource.getInputStream());
            out.write(centro);
            ImagenFactura.imprimirImagen(logo, out);
//------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles de la empresa                                                       */ 
            out.write(("\n").getBytes("IBM850"));
            out.write(centro);
            sinLinea(datos.path("Local").asText("N/A"), 32, out);
            out.write(centro);
            sinLinea(datos.path("restaurante").asText("N/A"), 28, out);
            out.write(("\n").getBytes("IBM850"));
            
            out.write(centro);
            sinLinea(datos.path("NIT").asText("N/A"), 28, out);
            out.write(centro);
            sinLinea(datos.path("Direccion").asText("N/A"), 28, out);
            out.write(centro);
            sinLinea(datos.path("Tel").asText("N/A"), 28, out);
            out.write(("\n").getBytes("IBM850"));

            out.write("\n_________________________________________\n".getBytes("IBM850"));
            out.write(("\n").getBytes("IBM850"));
//------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles del Cliente                                                       */            
            out.write(izquierda);
            imprimirLineaTexto("Cliente", datos.path("Cliente").asText("N/A"),26, out);
            imprimirLineaTexto("NIT/CC", datos.path("NIT/CC").asText("N/A"), 26, out);
            imprimirLineaTexto("Dirección", datos.path("Dirección").asText("N/A"), 26, out);
            imprimirLineaTexto("Telefono", datos.path("Telefono").asText("N/A"), 26, out);
            imprimirLineaTexto("Fecha de Generacion", datos.path("Fecha de Generacion").asText("N/A"), 26, out);
            out.write(("\n").getBytes("IBM850"));
//------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles de la mesa                                                       */
            out.write(izquierda);
            imprimirLineaTexto("Mesa", datos.path("Mesa").asText("N/A"), 26, out);
            imprimirLineaTexto("Cajero", datos.path("Cajero").asText("N/A"), 26, out);
            imprimirLineaTexto("Chk", datos.path("Chk").asText("N/A"), 26, out);
            imprimirLineaTexto("Caja", datos.path("Caja").asText("N/A"), 26, out);
            out.write("\n_________________________________________\n".getBytes("IBM850"));
//------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles de los productos                                                       */  
            String enc = ColumnasProducto.formatearEncabezado();
            BufferedImage imgEncabezado = TextoImagen.crearTexto(enc, 26);
            ImagenFactura.imprimirImagen(imgEncabezado, out);


            JsonNode itemsNode = datos.path("items"); 
            if (itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    out.write(izquierda);
                    String cant = item.path("Cantidad").asText(item.path("cantidad").asText("1"));
                    String codigo = item.path("Items").asText(item.path("items").asText(" "));
                    String nombre = item.path("Descripcion").asText(item.path("descripcion").asText("N/A"));
                    String precio = item.path("Precio").asText(item.path("precio").asText("0.00"));
                    String total = item.path("Total").asText(item.path("total").asText("0.00"));

                    ColumnasProducto.Producto produc = new ColumnasProducto.Producto(cant, codigo, nombre, precio, total);

                    String linea1 = ColumnasProducto.formatoLinea1(produc);
                    BufferedImage imgProducto = TextoImagen.crearTexto(linea1, 28);
                    ImagenFactura.imprimirImagen(imgProducto, out);

                    if(nombre != null && !nombre.trim().isEmpty() && !nombre.trim().equalsIgnoreCase(codigo.trim())){
                        String linea2 = ColumnasProducto.formatoLinea2(produc);
                        BufferedImage imgNombre = TextoImagen.crearTexto(linea2, 28);
                        ImagenFactura.imprimirImagen(imgNombre, out);
                    }
                }
            }
            out.write("\n_________________________________________\n".getBytes("IBM850"));

//-----------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles del total a pagar                                                       */


//------------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles del forma de pago                                                       */
            JsonNode pagosNode = datos.path("pagos");
            if (pagosNode.isArray()) {
                for (JsonNode pago : pagosNode) {
                    out.write(("nombrePago : " + pago.path("tenderName").asText("N/A") + "\n").getBytes("IBM850"));
                    out.write(("tenderAmount : " + pago.path("tenderAmount").asText("N/A") + "\n").getBytes("IBM850"));
                    out.write(("tipAmount : " + pago.path("tipAmount").asText("N/A") + "\n").getBytes("IBM850"));
                    out.write(("refNum : " + pago.path("referenceNumber").asText("N/A") + "\n\n").getBytes("IBM850"));   
                }
            }
            out.write("\n_________________________________________\n".getBytes("IBM850"));

//------------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles de la resolucion de la DIAN                                        */
            out.write(izquierda);
            imprimirLineaTexto("Resolucion DIAN", datos.path("Resolucion").asText("N/A"),26, out);
            imprimirLineaTexto("Fecha Resolucion",datos.path("Fecha Resolucion").asText("N/A"),26, out);
            imprimirLineaTexto("Fecha Inicial", datos.path("Fecha Inicial").asText("N/A"), 26, out);
            imprimirLineaTexto("Fecha Final", datos.path("Fecha Final").asText("N/A"), 26, out);
            imprimirLineaTexto("Rango Inicial", datos.path("Rango Inicial").asText("N/A"), 26, out);
            imprimirLineaTexto("Rango Final", datos.path("Rango Final").asText("N/A"), 26, out);
            out.write("\n_________________________________________\n".getBytes("IBM850"));
//------------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles del CUFE Y QR                                                           */

            out.write(("CUFE: " + datos.path("cufeGenerado").asText("N/A") + "\n\n").getBytes("IBM850"));

            out.write(cortarPapel);
            out.flush();

            logger.info("Factura impresa correctamente en {}:{}", ipImpresora, puerto);

        } catch (Exception e) {
            logger.error("Error al imprimir la factura", e);
            e.printStackTrace();
        }
    }
}