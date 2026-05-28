package com.clinica.sistema.service;

import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.dto.RelatorioUsoSalaItem;
import com.clinica.sistema.dto.RelatorioUsoSalaProfissional;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RelatorioMensalPdfServiceTest {

    private final RelatorioMensalPdfService pdfService = new RelatorioMensalPdfService();

    @Test
    void pdfCom20ProfissionaisDeveTerNoMaximoDuasPaginas() throws Exception {
        assertPaginasMaximas(20, 2);
    }

    @Test
    void pdfCom25ProfissionaisDeveTerNoMaximoDuasPaginas() throws Exception {
        assertPaginasMaximas(25, 2);
    }

    private void assertPaginasMaximas(int profissionais, int maxPaginas) throws Exception {
        byte[] pdf = pdfService.gerarPdf(relatorioComProfissionais(profissionais));
        PdfReader leitor = new PdfReader(pdf);
        int paginas = leitor.getNumberOfPages();
        leitor.close();
        assertTrue(paginas <= maxPaginas,
                profissionais + " profissionais: esperado no maximo " + maxPaginas + " paginas, obtido " + paginas);
    }

    @Test
    void pdfCom15ProfissionaisDeveTerUmaPagina() throws Exception {
        RelatorioMensalUsoSalasView relatorio = relatorioComProfissionais(15);
        byte[] pdf = pdfService.gerarPdf(relatorio);

        PdfReader leitor = new PdfReader(pdf);
        int paginas = leitor.getNumberOfPages();
        leitor.close();

        assertTrue(paginas <= 1, "Esperado 1 pagina para 15 profissionais, obtido " + paginas);
    }

    private RelatorioMensalUsoSalasView relatorioComProfissionais(int quantidade) {
        List<RelatorioUsoSalaProfissional> profissionais = new ArrayList<>();
        long total = 0;
        for (int i = 1; i <= quantidade; i++) {
            RelatorioUsoSalaProfissional prof = new RelatorioUsoSalaProfissional();
            prof.setProfissionalNome("Profissional " + i);
            for (int sala = 1; sala <= 4; sala++) {
                RelatorioUsoSalaItem item = new RelatorioUsoSalaItem();
                item.setSalaNome("Sala " + sala);
                item.setQuantidade(sala == 1 || sala == 2 ? 1 : 0);
                prof.getSalas().add(item);
                if (item.getQuantidade() > 0) {
                    total++;
                }
            }
            prof.setTotalHorarios(2);
            profissionais.add(prof);
        }

        RelatorioMensalUsoSalasView relatorio = new RelatorioMensalUsoSalasView();
        relatorio.setAnoReferencia(2026);
        relatorio.setMesReferencia(4);
        relatorio.setMesReferenciaLabel("Abril de 2026");
        relatorio.setProfissionais(profissionais);
        relatorio.setTotalGeral(total);
        return relatorio;
    }
}
