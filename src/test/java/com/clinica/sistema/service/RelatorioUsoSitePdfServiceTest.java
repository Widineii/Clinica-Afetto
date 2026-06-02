package com.clinica.sistema.service;

import com.clinica.sistema.dto.ProfissionalUsoSiteLinha;
import com.clinica.sistema.dto.RelatorioUsoSiteView;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RelatorioUsoSitePdfServiceTest {

    private final RelatorioUsoSitePdfService pdfService = new RelatorioUsoSitePdfService();

    @Test
    void gerarPdfRetornaBytesValidos() {
        var relatorio = new RelatorioUsoSiteView(
                1,
                1,
                0,
                1,
                0,
                List.of(new ProfissionalUsoSiteLinha(
                        1L,
                        "Julia",
                        "julia",
                        false,
                        LocalDateTime.now(),
                        2L,
                        true,
                        true
                ))
        );

        byte[] pdf = pdfService.gerarPdf(relatorio);

        assertTrue(pdf.length > 500);
        assertTrue(pdfService.nomeArquivoPdf().startsWith("relatorio-uso-site-"));
        assertTrue(pdfService.nomeArquivoPdf().endsWith(".pdf"));
    }
}
