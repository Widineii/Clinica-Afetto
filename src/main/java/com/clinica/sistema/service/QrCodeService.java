package com.clinica.sistema.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

@Service
public class QrCodeService {

    public byte[] gerarPng(String conteudo, int tamanho) {
        if (conteudo == null || conteudo.isBlank()) {
            throw new RuntimeException("Conteudo do QR Code vazio.");
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(conteudo, BarcodeFormat.QR_CODE, tamanho, tamanho, hints);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Nao foi possivel gerar o QR Code.", ex);
        }
    }
}
