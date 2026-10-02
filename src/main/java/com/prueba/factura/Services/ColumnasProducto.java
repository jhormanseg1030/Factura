package com.prueba.factura.Services;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class ColumnasProducto {

    public record Producto(String cant, String codigo, String nombre, String precio, String total) {}

    private static final int ANCHO_PAPEL_PX = 512;
    private static final int X_CANT = 0;
    private static final int X_DESC = 60;
    private static final int X_PRECIO_DER = 370; 
    private static final int X_TOTAL_DER = 512;  

    public static BufferedImage crearLineaCuatroColumnas(String cant, String desc, String precio, String total, float tamanoLetra) {
        Font font = TextoImagen.obtenerFuente(tamanoLetra);

        BufferedImage imgTemp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gTemp = imgTemp.createGraphics();
        gTemp.setFont(font);
        FontMetrics fm = gTemp.getFontMetrics();

        int alto = fm.getHeight();
        int ascent = fm.getAscent();

        int wPrecio = (precio != null) ? fm.stringWidth(precio) : 0;
        int wTotal = (total != null) ? fm.stringWidth(total) : 0;
        gTemp.dispose();

        BufferedImage imagen = new BufferedImage(ANCHO_PAPEL_PX, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagen.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, ANCHO_PAPEL_PX, alto);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(Color.BLACK);

        if (cant != null && !cant.isEmpty()) {
            g.drawString(cant, X_CANT, ascent);
        }

        if (desc != null && !desc.isEmpty()) {
            g.drawString(desc, X_DESC, ascent);
        }

        if (precio != null && !precio.isEmpty()) {
            g.drawString(precio, X_PRECIO_DER - wPrecio, ascent);
        }

        if (total != null && !total.isEmpty()) {
            g.drawString(total, X_TOTAL_DER - wTotal, ascent);
        }

        g.dispose();
        return imagen;
    }

    public static BufferedImage formatearEncabezado() {
        return crearLineaCuatroColumnas("Cant", "Descripcion", "Precio", "Total", 30);
    }

    public static BufferedImage formatoLinea1(Producto producto) {
        boolean tieneCodigo = producto.codigo() != null && !producto.codigo().trim().isEmpty();
        if (tieneCodigo) {
            return crearLineaCuatroColumnas(producto.cant(), producto.codigo().trim(), "", "", 28);
        } else {
            return crearLineaCuatroColumnas(producto.cant(), producto.nombre(), producto.precio(), producto.total(), 28);
        }
    }

    public static BufferedImage formatoLinea2(Producto producto) {
        boolean tieneCodigo = producto.codigo() != null && !producto.codigo().trim().isEmpty();
        if (!tieneCodigo || producto.nombre() == null || producto.nombre().trim().isEmpty()) {
            return null; 
        }
        return crearLineaCuatroColumnas("", producto.nombre(), producto.precio(), producto.total(), 26);
    }
}