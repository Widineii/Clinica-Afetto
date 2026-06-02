package com.clinica.sistema.service;

import com.clinica.sistema.dto.ProfissionalUsoSiteLinha;
import com.clinica.sistema.dto.RelatorioUsoSiteView;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class RelatorioUsoSitePdfService {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(10, 135, 133));
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter ARQUIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public byte[] gerarPdf(RelatorioUsoSiteView relatorio) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Document documento = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(documento, saida);
            documento.open();

            Paragraph titulo = new Paragraph("Relatorio de uso do site - Agenda Afetto", TITLE);
            titulo.setAlignment(Element.ALIGN_CENTER);
            titulo.setSpacingAfter(4);
            documento.add(titulo);

            Paragraph sub = new Paragraph(
                    "Gerado em " + LocalDateTime.now().format(DATA_HORA) + " · Agenda Afetto",
                    SUBTITLE
            );
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(14);
            documento.add(sub);

            documento.add(secao("Resumo"));
            documento.add(tabelaResumo(relatorio));

            documento.add(secao("Equipe"));
            documento.add(tabelaEquipe(relatorio));

            documento.close();
            return saida.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Nao foi possivel gerar o PDF do relatorio de uso do site.", e);
        }
    }

    public String nomeArquivoPdf() {
        return "relatorio-uso-site-" + LocalDateTime.now().format(ARQUIVO) + ".pdf";
    }

    private Paragraph secao(String texto) {
        Paragraph p = new Paragraph(texto, H2);
        p.setSpacingBefore(12);
        p.setSpacingAfter(6);
        return p;
    }

    private PdfPTable tabelaResumo(RelatorioUsoSiteView relatorio) throws DocumentException {
        PdfPTable tabela = new PdfPTable(new float[]{2.5f, 1f});
        tabela.setWidthPercentage(55);
        tabela.setSpacingAfter(8);
        linhaResumo(tabela, "Total de profissionais", String.valueOf(relatorio.totalProfissionais()));
        linhaResumo(tabela, "Ja acessaram o site", String.valueOf(relatorio.totalJaAcessaram()));
        linhaResumo(tabela, "Nunca acessaram", String.valueOf(relatorio.totalNuncaAcessaram()));
        linhaResumo(tabela, "Ja agendaram", String.valueOf(relatorio.totalJaAgendaram()));
        linhaResumo(tabela, "Ainda nao agendaram", String.valueOf(relatorio.totalNaoAgendaram()));
        return tabela;
    }

    private void linhaResumo(PdfPTable tabela, String rotulo, String valor) {
        PdfPCell rotuloCell = new PdfPCell(new Phrase(rotulo, BODY));
        rotuloCell.setPadding(5);
        rotuloCell.setBackgroundColor(new Color(248, 250, 252));
        tabela.addCell(rotuloCell);
        PdfPCell valorCell = new PdfPCell(new Phrase(valor, BODY_BOLD));
        valorCell.setPadding(5);
        valorCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabela.addCell(valorCell);
    }

    private PdfPTable tabelaEquipe(RelatorioUsoSiteView relatorio) throws DocumentException {
        PdfPTable tabela = new PdfPTable(new float[]{2.5f, 1.3f, 2.5f, 1f});
        tabela.setWidthPercentage(100);
        cabecalho(tabela, "Profissional");
        cabecalho(tabela, "Login");
        cabecalho(tabela, "Entrou no site?");
        cabecalho(tabela, "Qtd. agendamentos");
        for (ProfissionalUsoSiteLinha linha : relatorio.profissionais()) {
            String nome = linha.nome();
            if (linha.donaClinica()) {
                nome += " (Dona)";
            }
            celula(tabela, nome);
            celula(tabela, linha.login());
            String entrou = linha.acessoConfirmadoPorLogin()
                    ? "Ja entrou (" + linha.ultimoAcessoEm().format(DATA_HORA) + ")"
                    : linha.entrouSoPorHistoricoAgenda()
                            ? "Ja entrou (ja usou a agenda)"
                            : "Nunca entrou";
            celula(tabela, entrou);
            celulaCentral(tabela, String.valueOf(linha.totalAgendamentos()));
        }
        return tabela;
    }

    private void celulaCentral(PdfPTable tabela, String texto) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, BODY_BOLD));
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabela.addCell(cell);
    }

    private void cabecalho(PdfPTable tabela, String texto) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, BODY_BOLD));
        cell.setBackgroundColor(new Color(244, 248, 248));
        cell.setPadding(5);
        tabela.addCell(cell);
    }

    private void celula(PdfPTable tabela, String texto) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, BODY));
        cell.setPadding(4);
        tabela.addCell(cell);
    }
}
