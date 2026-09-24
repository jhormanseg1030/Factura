package com.prueba.factura.Services;

import java.io.IOException;
import java.io.OutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class ImagenFactura {
    
    private static final int ANCHO_MAXIMO_LOGO = 384; 

    public static void imprimirImagen(BufferedImage img, OutputStream out) throws IOException {
        if (img == null) return;

        if (img.getWidth() > ANCHO_MAXIMO_LOGO) {
            int nuevoAncho = ANCHO_MAXIMO_LOGO;
            int nuevoAlto = (img.getHeight() * ANCHO_MAXIMO_LOGO) / img.getWidth();
            
            BufferedImage imgEscalada = new BufferedImage(nuevoAncho, nuevoAlto, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = imgEscalada.createGraphics();
            
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(img, 0, 0, nuevoAncho, nuevoAlto, null);
            g2d.dispose();
            
            img = imgEscalada;
        }

        int width = img.getWidth();
        int height = img.getHeight();

        int bytesPerRow = (width + 7) / 8;

        byte[] header = new byte[] {
            0x1D, 0x76, 0x30, 0x00,
            (byte) (bytesPerRow & 0xFF),
            (byte) ((bytesPerRow >> 8) & 0xFF),
            (byte) (height & 0xFF),
            (byte) ((height >> 8) & 0xFF)
        };
        out.write(header);

        byte[] buffer = new byte[bytesPerRow * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);

                int alpha = (rgb >> 24) & 0xFF;
                if (alpha < 128) continue; 

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                if (luminance < 160) {
                    int bytePos = (y * bytesPerRow) + (x / 8);
                    int bitPos = 7 - (x % 8);
                    buffer[bytePos] |= (1 << bitPos);
                }
            }
        }
        out.write(buffer);
        out.flush();

        out.write(new byte[] {0x1B, 0x61, 0x00});
    }
}