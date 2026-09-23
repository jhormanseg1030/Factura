package com.prueba.factura.Services;

import java.io.IOException;
import java.io.OutputStream;
import java.awt.image.*;

public class ImagenFactura {
    
    public static void imprimirImagen(BufferedImage img, OutputStream out) throws IOException{
        if(img == null) return;

        int width = img.getWidth();
        int height = img.getHeight();

        out.write(new byte[]{0x1B, 0x61, 0x01});

        int bytesPerRow = (width + 7) / 8;

        int dataLenght = 10 + (bytesPerRow * height);
        byte pL = (byte) (dataLenght & 0xFF);
        byte pH = (byte) ((dataLenght >> 8) & 0xFF);

        byte[] header = new byte[]{
            0x1D, 0x28, 0x4C, 
            pL, pH, 
            0x30, 0x43, 0x30, 
            0x01, 0x01, 
            0x31,
            (byte) (bytesPerRow & 0xFF), (byte) ((bytesPerRow >> 8) & 0xFF),
            (byte) (height & 0xFF), (byte) ((height >> 8) & 0xFF)
        };
        out.write(header);

        byte[] buffer = new byte[bytesPerRow * height];
        int index = 0;

        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x += 8){
                int bit = 0;

                if( x < width){
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    int luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                    
                    if(luminance < 128){
                        bit = 1;
                    }
                }
                int bytePos = index + (x / 8);
                int bitPos = 7 - (x % 8);
                if (bit == 1) {
                    buffer[bytePos] |= (1 << bitPos);
                }
            }
            index += bytesPerRow;
        }
        out.write(buffer);
        out.flush();

        out.write(new byte[] {0x1B, 0x61, 0x00});
    }
}
