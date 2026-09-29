package com.prueba.factura.Services;

import org.springframework.stereotype.Service;
import java.awt.image.*;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

@Service 
public class GenerarQr {

    private static final String  QR_DIAN = "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=";

    public BufferedImage generateQRCode(String contenido, int width, int height) throws Exception {
        QRCodeWriter codeWrite = new QRCodeWriter();
        BitMatrix matrix = codeWrite.encode(contenido, BarcodeFormat.QR_CODE,width, height);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    public BufferedImage generateQRCodeCufe(String cufe, int width, int height) throws Exception{
        String qrContent = QR_DIAN + cufe;
        return generateQRCode(qrContent,width, height);
    }
    
}
