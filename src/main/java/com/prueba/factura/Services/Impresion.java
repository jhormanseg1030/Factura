package com.prueba.factura.Services;

import java.io.OutputStream;
import java.net.Socket;

import javax.imageio.ImageIO;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired 
    private GenerarQr generarQr;
    
    private void imprimirLineaTexto(String etiqueta, String valor, int tamanoLetra, OutputStream out) throws Exception {
        BufferedImage imgTexto = TextoImagen.crearTexto(etiqueta + ": " + valor, tamanoLetra);
        ImagenFactura.imprimirImagen(imgTexto, out);
    }

    private void sinLinea(String valor, int tamanoLetra, OutputStream out) throws Exception {
        BufferedImage imgTexto = TextoImagen.crearTexto( valor, tamanoLetra);
        ImagenFactura.imprimirImagen(imgTexto, out);
    }

private void conEspacio(String etique, String valor, int tamanoLetra, OutputStream out) throws Exception {
    if(etique == null ) etique = "";
    if(valor == null)valor = "";

    BufferedImage imgEtique = TextoImagen.crearTexto(etique, tamanoLetra);
    BufferedImage imgValor = TextoImagen.crearTexto(valor, tamanoLetra);

    int ANCHO_PAPEL_PX = 400;
    int alto = Math.max(imgEtique.getHeight(), imgValor.getHeight());

    BufferedImage lineaCompleta = new BufferedImage(ANCHO_PAPEL_PX, alto,BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = lineaCompleta.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, ANCHO_PAPEL_PX, alto);

    g.drawImage(imgEtique, 0, 0, null);
    g.drawImage(imgValor, ANCHO_PAPEL_PX - imgValor.getWidth(), 0 , null);
    g.dispose();

    ImagenFactura.imprimirImagen(lineaCompleta, out);
    }

    private void imprimirCufeFragmentado(String cufe, int tamanoLetra, OutputStream out) throws Exception {
    if (cufe == null || cufe.isEmpty() || "N/A".equals(cufe)) return;

    sinLinea("CUFE:", tamanoLetra, out);
    
    int bloque = 32;
    for (int i = 0; i < cufe.length(); i += bloque) {
        int fin = Math.min(i + bloque, cufe.length());
        String fragmento = cufe.substring(i, fin);
        sinLinea(fragmento, tamanoLetra - 2, out);
    }
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
            sinLinea(datos.path("RazonSocial").asText("N/A"), 32, out);
            out.write(centro);
            sinLinea(datos.path("restaurante").asText("N/A"), 28, out);
            out.write(("\n").getBytes("IBM850"));
            
            out.write(centro);
            sinLinea(datos.path("RucEmisor").asText("N/A"), 28, out);
            out.write(centro);
            sinLinea(datos.path("Direccion").asText("N/A"), 28, out);
            out.write(centro);
            sinLinea(datos.path("Tel").asText("N/A"), 28, out);
            out.write(centro);
            sinLinea("Act 5611 13.8x1000", 28, out);
            out.write(centro);
            sinLinea("Act 5630 13.8x1000", 28, out);
            out.write(centro);
            sinLinea("Act 5613 13.8x1000", 28, out);
            out.write(centro);
            sinLinea("Act 5619 13.8x1000", 28, out);
            out.write(centro);
            sinLinea("Act 4711 4.14x1000", 28, out);
            out.write(centro);
            sinLinea("Gran Contribuyente ICA", 28, out);
            out.write(centro);
            sinLinea("Agente Retenedor de IVA", 28, out);
            out.write(("\n").getBytes("IBM850"));
            out.write("\n_________________________________________\n".getBytes("IBM850"));
            out.write(("\n").getBytes("IBM850"));
//------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles del Cliente                                                       */            
            out.write(izquierda);
            imprimirLineaTexto("cliente", datos.path("cliente").asText("N/A"),26, out);
            imprimirLineaTexto("NIT/CC", datos.path("identificacion_cliente").asText("N/A"), 26, out);
            imprimirLineaTexto("Dirección", datos.path("Dirección").asText("N/A"), 26, out);
            imprimirLineaTexto("Telefono", datos.path("Telefono").asText("N/A"), 26, out);
            imprimirLineaTexto("Fecha de Generacion", datos.path("Fecha de Generacion").asText("N/A"), 32, out);
            out.write(("\n").getBytes("IBM850"));
//------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles de la mesa                                                       */
            out.write(izquierda);
            imprimirLineaTexto("Mesa", datos.path("Mesa").asText("N/A"), 26, out);
            imprimirLineaTexto("Cajero", datos.path("Cajero").asText("N/A"), 26, out);
            imprimirLineaTexto("Chk", datos.path("numero_ticket").asText("N/A"), 26, out);
            imprimirLineaTexto("Caja", datos.path("caja_wsid").asText("N/A"), 26, out);
            out.write("\n_________________________________________\n".getBytes("IBM850"));
//------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles de los productos                                                       */  
            BufferedImage imgEncabezado = ColumnasProducto.formatearEncabezado();
            ImagenFactura.imprimirImagen(imgEncabezado, out);

            JsonNode items = datos.path("items");
            if(items.isArray()){
                for(JsonNode item : items){
                    out.write(izquierda);

                    String cant = item.path("cantidad").asText(item.path("cantidad").asText("1"));
                    String codigo = item.path("Codigo").asText(item.path("codigo").asText("0.00"));
                    String nombre = item.path("Descripcion").asText(item.path("descripcion").asText("N/A"));
                    String precio = item.path("Precio").asText(item.path("precio").asText("0.00"));
                    String total = item.path("Total").asText(item.path("total").asText("0.00"));

                    ColumnasProducto.Producto produc = new ColumnasProducto.Producto(cant, codigo, nombre, precio, total);

                    BufferedImage imgLinea1 = ColumnasProducto.formatoLinea1(produc);
                    ImagenFactura.imprimirImagen(imgLinea1, out);

                    BufferedImage imgLinea2 = ColumnasProducto.formatoLinea2(produc);
                    if(imgLinea2 != null){
                        ImagenFactura.imprimirImagen(imgLinea2, out);
                    }
                }
            }
            out.write("\n_________________________________________\n".getBytes("IBM850"));

//-----------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles del total a pagar                                                       */
            out.write(izquierda);
            conEspacio("Base", datos.path("Base").asText("0.00"), 26, out);
            out.write(izquierda);
            conEspacio("INC", datos.path("INC").asText("0.00"), 26, out);
            out.write(izquierda);
            conEspacio("Total", datos.path("Subtotal").asText("0.00"), 26, out);
            out.write(izquierda);
            conEspacio("Propina", datos.path("Propina").asText("0.00"), 26, out);
            out.write(izquierda);
            conEspacio("Total", datos.path("Total").asText("0.00"), 26, out);
            out.write("\n_________________________________________\n".getBytes("IBM850"));


//------------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles del forma de pago                                                       */
            JsonNode pagosNode = datos.path("pagos");
            if (pagosNode.isArray()) {
                for (JsonNode pago : pagosNode) {
                    out.write(izquierda);
                    imprimirLineaTexto("Forma de Pago", pago.path("Forma de Pago").asText("N/A"), 26, out);
                    imprimirLineaTexto("Medio de Pago", pago.path("tenderName").asText("N/A"), 26, out);
                }
            }
            out.write("\n_________________________________________\n".getBytes("IBM850"));

//------------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles de la resolucion de la DIAN                                             */
            out.write(izquierda);
            imprimirLineaTexto("Resolucion DIAN", datos.path("Resolucion").asText("N/A"),26, out);
            imprimirLineaTexto("Fecha Resolucion",datos.path("Fecha Resolucion").asText("N/A"),26, out);
            imprimirLineaTexto("Fecha Inicial", datos.path("Fecha Inicial").asText("N/A"), 26, out);
            imprimirLineaTexto("Fecha Final", datos.path("Fecha Final").asText("N/A"), 26, out);
            imprimirLineaTexto("Rango Inicial", datos.path("Rango Inicial").asText("N/A"), 26, out);
            imprimirLineaTexto("Rango Final", datos.path("Rango Final").asText("N/A"), 26, out);
            out.write("\n_________________________________________\n".getBytes("IBM850"));
//------------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles de impuestos Incluidos                                                  */
            out.write(centro);
            sinLinea("Impuestos Incluidos", 24, out);
            out.write(centro);
            sinLinea( "Advertencia de Propina", 26, out);
            out.write(centro);
            sinLinea("En la parte posterior de este documento", 26, out);
            out.write(("\n").getBytes("IBM850"));
            out.write(centro);
            sinLinea("Implementado Por Inverleoka SAS", 26, out);
            out.write(centro);
            sinLinea("NIT 8605108638", 26, out);
            out.write(centro);
            sinLinea("Integrador: Hospitality", 26, out);
            out.write(centro);
            sinLinea("Restaurants Automation SAS", 26, out);
            out.write(centro);
            sinLinea("NIT 901518527-2", 26, out);

//------------------------------------------------------------------------------------------------------------------------------------------------------
/*                                                                   Detalles del CUFE Y QR                                                           */

            String cufe = datos.path("cufeGenerado").asText("N/A");
            if(!"N/A".equals(cufe)){
            BufferedImage qrImage = generarQr.generateQRCodeCufe(cufe, 250, 250);
            out.write(centro);
            ImagenFactura.imprimirImagen(qrImage, out);
            out.write(("\n").getBytes("IBM850"));
            }
            out.write(izquierda);
            imprimirCufeFragmentado(cufe, 24, out);

            out.write(cortarPapel);
            out.flush();
            logger.info("Factura impresa correctamente en {}:{}", ipImpresora, puerto);

        } catch (Exception e) {
            logger.error("Error al imprimir la factura", e);
            e.printStackTrace();
        }
    }
}
