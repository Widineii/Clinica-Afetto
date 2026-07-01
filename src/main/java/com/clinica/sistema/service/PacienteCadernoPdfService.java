package com.clinica.sistema.service;

import com.clinica.sistema.dto.PacienteCadernoAnotacaoView;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PacienteCadernoPdfService {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final DateTimeFormatter ARQUIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public byte[] gerarPdf(String nomePaciente, String profissionalNome, List<PacienteCadernoAnotacaoView> anotacoes) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Document documento = new Document(PageSize.A4, 42, 42, 42, 42);
            PdfWriter.getInstance(documento, saida);
            documento.open();

            Paragraph titulo = new Paragraph("Caderno de anotacoes - " + paraPdf(nomePaciente), TITLE);
            titulo.setSpacingAfter(4);
            documento.add(titulo);

            Paragraph sub = new Paragraph(
                    "Profissional: " + paraPdf(profissionalNome)
                            + " | Gerado em " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                            + " | Uso interno",
                    SUBTITLE
            );
            sub.setSpacingAfter(14);
            documento.add(sub);

            if (anotacoes == null || anotacoes.isEmpty()) {
                documento.add(new Paragraph("Nenhuma anotacao registrada neste caderno.", BODY));
            } else {
                for (PacienteCadernoAnotacaoView anotacao : anotacoes) {
                    Paragraph cabecalho = new Paragraph(paraPdf(anotacao.getDataRotulo()), BODY_BOLD);
                    cabecalho.setSpacingBefore(8);
                    documento.add(cabecalho);
                    if (anotacao.getEvolucaoRotulo() != null && !anotacao.getEvolucaoRotulo().isBlank()) {
                        documento.add(new Paragraph("Evolucao: " + paraPdf(anotacao.getEvolucaoRotulo()), BODY));
                    }
                    if (anotacao.getLembreteRotulo() != null && !anotacao.getLembreteRotulo().isBlank()) {
                        documento.add(new Paragraph("Lembrete: " + paraPdf(anotacao.getLembreteRotulo()), BODY));
                    }
                    Paragraph texto = new Paragraph(paraPdf(anotacao.getTexto()), BODY);
                    texto.setSpacingAfter(4);
                    documento.add(texto);
                }
            }

            documento.close();
            return saida.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Não foi possível gerar o PDF do caderno.", e);
        }
    }

    public String nomeArquivoPdf(String nomePaciente) {
        String base = textoSeguro(nomePaciente).replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase();
        if (base.isBlank()) {
            base = "paciente";
        }
        return "caderno-" + base + "-" + LocalDateTime.now().format(ARQUIVO) + ".pdf";
    }

    private String textoSeguro(String valor) {
        return valor != null && !valor.isBlank() ? valor.trim() : "paciente";
    }

    private String paraPdf(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        String normalizado = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalizado
                .replace('—', '-')
                .replace('–', '-');
    }
}
