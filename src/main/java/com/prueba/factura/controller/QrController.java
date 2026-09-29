package com.prueba.factura.controller;

import java.awt.image.*;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.prueba.factura.Services.GenerarQr;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

@RestController 
public class QrController {
    
    @Autowired 
    private GenerarQr generarQr;

    @GetMapping("v1/qrcode")
    public void  generarQr(HttpServletResponse response,
        @RequestParam String text,
        @RequestParam (defaultValue = "350") int width,
        @RequestParam (defaultValue = "350") int height) throws Exception {
            BufferedImage qrImage = generarQr.generateQRCode(text, width, height);
            ServletOutputStream out = response.getOutputStream();
            ImageIO.write(qrImage, "PNG", out);
            out.flush();
            out.close();
        }
    //        
}
