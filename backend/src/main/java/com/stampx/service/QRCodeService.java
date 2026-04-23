package com.stampx.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class QRCodeService {

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Generates a PNG QR code encoding the shop's pass-download URL.
     * Scanning this QR on a new customer's phone will give them a loyalty pass.
     */
    public byte[] generateQRCodeForShop(String slug) {
        String content = baseUrl + "/api/pass/download/" + slug;
        return generateQRCodePNG(content);
    }

    public byte[] generateQRCodePNG(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            return baos.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("QR code generation failed", e);
        }
    }
}
