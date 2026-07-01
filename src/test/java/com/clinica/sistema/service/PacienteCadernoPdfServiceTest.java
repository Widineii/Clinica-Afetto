package com.clinica.sistema.service;

import com.clinica.sistema.dto.PacienteCadernoAnotacaoView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PacienteCadernoPdfServiceTest {

    private final PacienteCadernoPdfService service = new PacienteCadernoPdfService();

    @Test
    void deveGerarPdfComAcentos() {
        var anotacao = new PacienteCadernoAnotacaoView(
                1L,
                "Observação com acentuação: evolução estável.",
                "01/07/2026 09:00",
                "Melhoria",
                "01/07/2026",
                "2026-07-01"
        );
        byte[] pdf = service.gerarPdf("fde - Sala 1 - Quarta-feira", "Carol", List.of(anotacao));
        assertTrue(pdf.length > 100);
        assertTrue(pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F');
    }
}
