package com.prueba.factura.Services;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class TextoImagen {
    
    public static BufferedImage crearTexto(String texto, int tamanoLetra) {
        if (texto == null || texto.isEmpty()) {
            texto = " ";
        }

        BufferedImage imgTemp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gTemp = imgTemp.createGraphics();

        Font font = new Font("Calibri", Font.ITALIC, tamanoLetra);
        gTemp.setFont(font);
        FontMetrics fm = gTemp.getFontMetrics();

        int ancho = fm.stringWidth(texto);
        int alto = fm.getHeight();
        int ascent = fm.getAscent(); 
        gTemp.dispose();

        if (ancho <= 0) ancho = 1;
        if (ancho > 384) ancho = 384;

        BufferedImage imagenFinal = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagenFinal.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, ancho, alto);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(Color.BLACK);

        g.drawString(texto, 0, ascent);
        g.dispose();

        return imagenFinal;
    }
}