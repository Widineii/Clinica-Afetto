package com.clinica.sistema.service;

import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.dto.RelatorioUsoSalaItem;
import com.clinica.sistema.dto.RelatorioUsoSalaProfissional;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class RelatorioMensalPdfService {

    private static final Color TEAL = new Color(27, 77, 92);
    private static final Color PAGE_BG = new Color(241, 245, 249);
    private static final Color CARD_BG = new Color(255, 255, 255);
    private static final Color BORDER = new Color(226, 232, 240);
    private static final Color TEXT_MAIN = new Color(51, 65, 85);
    private static final Color TEXT_MUTED = new Color(100, 116, 139);
    private static final Color TEXT_LIGHT = new Color(148, 163, 184);
    private static final Color WHITE = Color.WHITE;
    private static final Color ZEBRA = new Color(248, 250, 252);

    private static final SalaEstilo[] ESTILOS_SALA = {
            new SalaEstilo("Sala 1", new Color(59, 130, 246), new Color(239, 246, 255), new Color(191, 219, 254)),
            new SalaEstilo("Sala 2", new Color(16, 185, 129), new Color(236, 253, 245), new Color(167, 243, 208)),
            new SalaEstilo("Sala 3", new Color(168, 85, 247), new Color(243, 232, 255), new Color(233, 213, 255)),
            new SalaEstilo("Sala 4", new Color(249, 115, 22), new Color(255, 247, 237), new Color(255, 237, 213))
    };

    public byte[] gerarPdf(RelatorioMensalUsoSalasView relatorio) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            int qtdProfissionais = relatorio.getProfissionais().size();
            EscalaPdf escala = escalaPara(qtdProfissionais);

            Document documento = new Document(PageSize.A4, 18, 18, 20, 24);
            PdfWriter writer = PdfWriter.getInstance(documento, saida);
            writer.setPageEvent(new RodapeAfetto());
            documento.open();

            documento.add(criarBlocoCabecalho(relatorio, escala));
            documento.add(espaco(escala.espacoSecao));
            documento.add(criarCardsResumo(relatorio, escala));
            documento.add(espaco(escala.espacoPequeno));

            String textoAviso = relatorio.isRelatorioSemanal()
                    ? "* Agendamentos cancelados nao entram neste relatorio. "
                            + "Consultas com menos de 24h desde o horario agendado tambem nao entram."
                    : "* Agendamentos cancelados nao entram neste relatorio";
            Paragraph aviso = new Paragraph(textoAviso, escala.fontDisclaimer);
            aviso.setAlignment(Element.ALIGN_CENTER);
            documento.add(aviso);
            documento.add(espaco(escala.espacoAntesTabela));

            documento.add(criarTabelaProfissionais(relatorio, escala));

            documento.close();
            return saida.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new RuntimeException("Nao foi possivel gerar o PDF do relatorio.", e);
        }
    }

    private EscalaPdf escalaPara(int qtdProfissionais) {
        int linhas = Math.max(qtdProfissionais, 1);
        float alturaPagina = PageSize.A4.getHeight();
        float areaUtil = alturaPagina - 44f;

        float blocoTopo = 96f;
        float alturaCabecalhoTabela = 22f;
        float areaTabelaPrimeiraPagina = areaUtil - blocoTopo - alturaCabecalhoTabela;
        float areaTabelaDuasPaginas = areaTabelaPrimeiraPagina + (alturaPagina - 48f);

        float alturaLinha = areaTabelaPrimeiraPagina / linhas;
        if (alturaLinha > 24f) {
            alturaLinha = 24f;
        } else if (alturaLinha < 17f) {
            alturaLinha = Math.min(24f, areaTabelaDuasPaginas / linhas);
            alturaLinha = Math.max(16f, alturaLinha);
        }

        float fator = 1f;
        if (linhas > 20) {
            fator = 0.9f;
        } else if (linhas > 15) {
            fator = 0.94f;
        }

        int tamanhoIconeCard = linhas <= 15 ? 32 : 28;
        boolean compacto = linhas > 18;

        return new EscalaPdf(
                fator,
                alturaLinha,
                tamanhoIconeCard,
                compacto ? 18 : 22,
                compacto ? 5f : 6f,
                compacto ? 4f : 5f,
                compacto
        );
    }

    private PdfPTable criarBlocoCabecalho(RelatorioMensalUsoSalasView relatorio, EscalaPdf escala) throws IOException {
        PdfPTable painel = tabelaPainel();
        PdfPCell conteudo = celulaPainel();

        PdfPTable topo = new PdfPTable(new float[]{2.2f, 2.8f});
        topo.setWidthPercentage(100);
        topo.addCell(celulaLogoMarca(escala));
        topo.addCell(celulaTituloRelatorio(relatorio, escala));
        conteudo.addElement(topo);

        painel.addCell(conteudo);
        return painel;
    }

    private PdfPCell celulaLogoMarca(EscalaPdf escala) throws IOException {
        PdfPCell wrapper = new PdfPCell();
        wrapper.setBorder(PdfPCell.NO_BORDER);
        wrapper.setPadding(0);
        wrapper.setVerticalAlignment(Element.ALIGN_MIDDLE);
        wrapper.setHorizontalAlignment(Element.ALIGN_LEFT);

        Image logo = carregarLogo();
        if (logo != null) {
            logo.scaleToFit(escala.logoLarguraExibicao, escala.logoAlturaExibicao);
            logo.setAlignment(Image.ALIGN_LEFT);
            wrapper.addElement(logo);
            return wrapper;
        }

        PdfPTable marca = new PdfPTable(new float[]{0.75f, 2.25f});
        marca.setWidthPercentage(100);
        PdfPCell icone = new PdfPCell();
        icone.setBorder(PdfPCell.NO_BORDER);
        icone.setPadding(0);
        icone.setVerticalAlignment(Element.ALIGN_MIDDLE);
        icone.addElement(criarLogoPlaceholder(escala));
        marca.addCell(icone);

        PdfPCell textos = new PdfPCell();
        textos.setBorder(PdfPCell.NO_BORDER);
        textos.setPaddingLeft(4f);
        textos.setVerticalAlignment(Element.ALIGN_MIDDLE);
        textos.addElement(new Paragraph("Clinica Afetto", escala.fontBrand));
        textos.addElement(new Paragraph("Psicologia & Saude", escala.fontBrandSub));
        marca.addCell(textos);

        wrapper.addElement(marca);
        return wrapper;
    }

    private PdfPCell celulaTituloRelatorio(RelatorioMensalUsoSalasView relatorio, EscalaPdf escala) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        String titulo = relatorio.getTituloRelatorio() != null && !relatorio.getTituloRelatorio().isBlank()
                ? relatorio.getTituloRelatorio()
                : "RELATORIO MENSAL DE USO DE SALAS";
        cell.addElement(paragrafo(titulo, escala.fontReportTitle, Element.ALIGN_RIGHT));
        cell.addElement(paragrafo("Periodo: " + relatorio.getMesReferenciaLabel(), escala.fontBody, Element.ALIGN_RIGHT));
        cell.addElement(paragrafo(
                "Gerado em: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                escala.fontSmall,
                Element.ALIGN_RIGHT
        ));
        return cell;
    }

    private PdfPTable criarLogoPlaceholder(EscalaPdf escala) {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(28);
        int tamanhoFonteLogo = Math.max(10, escala.logoAltura / 2);
        PdfPCell cell = new PdfPCell(new Phrase("A", FontFactory.getFont(FontFactory.HELVETICA_BOLD, tamanhoFonteLogo, Font.BOLD, WHITE)));
        cell.setBackgroundColor(TEAL);
        cell.setFixedHeight(escala.logoAltura + 4f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4f);
        box.addCell(cell);
        return box;
    }

    private PdfPTable criarCardsResumo(RelatorioMensalUsoSalasView relatorio, EscalaPdf escala) {
        PdfPTable painel = tabelaPainel();
        PdfPCell conteudo = celulaPainel();

        PdfPTable cards = new PdfPTable(2);
        cards.setWidthPercentage(100);
        String rotuloHorarios = relatorio.isRelatorioSemanal()
                ? "Total de Horarios Agendados na Semana"
                : "Total de Horarios Agendados no Mes";
        String rotuloProfissionais = relatorio.isRelatorioSemanal()
                ? "Profissionais Ativos na Semana"
                : "Profissionais Ativos no Mes";
        cards.addCell(cardResumo(rotuloHorarios, String.valueOf(relatorio.getTotalGeral()), "calendario", escala));
        cards.addCell(cardResumo(rotuloProfissionais, String.valueOf(relatorio.getProfissionais().size()), "profissional", escala));
        conteudo.addElement(cards);

        painel.addCell(conteudo);
        return painel;
    }

    private PdfPCell cardResumo(String rotulo, String valor, String tipoIcone, EscalaPdf escala) {
        PdfPTable card = new PdfPTable(new float[]{0.55f, 2.45f});
        card.setWidthPercentage(100);

        PdfPCell iconeCell = new PdfPCell();
        iconeCell.setBorder(PdfPCell.NO_BORDER);
        iconeCell.setBackgroundColor(CARD_BG);
        iconeCell.setPadding(escala.paddingCard);
        iconeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        iconeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        try {
            Image icone = criarIconeCard(tipoIcone, escala.tamanhoIconeCard);
            iconeCell.addElement(icone);
        } catch (Exception e) {
            iconeCell.addElement(new Paragraph("•", escala.fontCardValue));
        }
        card.addCell(iconeCell);

        PdfPCell info = new PdfPCell();
        info.setBorder(PdfPCell.NO_BORDER);
        info.setBackgroundColor(CARD_BG);
        info.setPaddingTop(escala.paddingCard);
        info.setPaddingBottom(escala.paddingCard);
        info.setPaddingRight(8f);
        info.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph label = new Paragraph(rotulo, escala.fontCardLabel);
        label.setSpacingAfter(2f);
        info.addElement(label);
        info.addElement(new Paragraph(valor, escala.fontCardValue));
        card.addCell(info);

        PdfPCell wrapper = new PdfPCell(card);
        wrapper.setBorder(Rectangle.BOX);
        wrapper.setBorderColor(BORDER);
        wrapper.setBorderWidth(0.8f);
        wrapper.setBackgroundColor(CARD_BG);
        wrapper.setPadding(2f);
        return wrapper;
    }

    private Image criarIconeCard(String tipo, int size) throws IOException, DocumentException {
        BufferedImage imagem = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagem.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(TEAL);
        g.fill(new RoundRectangle2D.Float(0, 0, size, size, 6, 6));
        g.setColor(WHITE);
        g.setStroke(new BasicStroke(1.5f));
        float s = size / 45f;
        if ("calendario".equals(tipo)) {
            g.drawRoundRect((int) (10 * s), (int) (9 * s), (int) (25 * s), (int) (24 * s), 4, 4);
            g.fillRect((int) (10 * s), (int) (9 * s), (int) (25 * s), (int) (7 * s));
            g.drawLine((int) (16 * s), (int) (20 * s), (int) (16 * s), (int) (29 * s));
            g.drawLine((int) (22 * s), (int) (20 * s), (int) (22 * s), (int) (29 * s));
            g.drawLine((int) (28 * s), (int) (20 * s), (int) (28 * s), (int) (29 * s));
        } else {
            g.fill(new Ellipse2D.Float(16 * s, 11 * s, 13 * s, 13 * s));
            g.fill(new RoundRectangle2D.Float(12 * s, 24 * s, 21 * s, 12 * s, 8, 8));
        }
        g.dispose();
        Image pdfImg = Image.getInstance(imagem, null);
        pdfImg.scaleToFit(size, size);
        return pdfImg;
    }

    private PdfPTable criarTabelaProfissionais(RelatorioMensalUsoSalasView relatorio, EscalaPdf escala) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1.9f, 3.9f, 1.8f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSplitRows(true);
        table.setSplitLate(true);

        adicionarCabecalhoColuna(table, "Profissional", escala);
        adicionarCabecalhoColuna(table, "Distribuicao por Sala", escala);
        adicionarCabecalhoColuna(table, "Total de sala usada", escala);

        if (relatorio.getProfissionais().isEmpty()) {
            PdfPCell vazio = new PdfPCell(new Phrase("Nenhum agendamento registrado neste periodo.", escala.fontBody));
            vazio.setColspan(3);
            vazio.setPadding(12f);
            vazio.setHorizontalAlignment(Element.ALIGN_CENTER);
            vazio.setBorderColor(BORDER);
            table.addCell(vazio);
            return table;
        }

        boolean zebra = false;
        for (RelatorioUsoSalaProfissional profissional : relatorio.getProfissionais()) {
            Map<String, Long> porSala = mapaSalas(profissional);
            Color fundo = zebra ? ZEBRA : WHITE;
            zebra = !zebra;

            String nome = profissional.getProfissionalNome();
            if (nome == null || nome.isBlank()) {
                nome = "Profissional";
            }
            table.addCell(celulaProfissional(nome, fundo, escala));
            table.addCell(celulaTagsSalas(porSala, fundo, escala));
            table.addCell(celulaTotalSalasUsadas(porSala, fundo, escala));
        }

        return table;
    }

    private PdfPCell celulaProfissional(String nome, Color fundo, EscalaPdf escala) {
        PdfPTable linha = new PdfPTable(new float[]{0.4f, 1.6f});
        linha.setWidthPercentage(100);

        PdfPCell avatar = new PdfPCell(new Phrase("P", escala.fontAvatar));
        avatar.setBackgroundColor(new Color(241, 245, 249));
        avatar.setFixedHeight(escala.alturaAvatar);
        avatar.setHorizontalAlignment(Element.ALIGN_CENTER);
        avatar.setVerticalAlignment(Element.ALIGN_MIDDLE);
        avatar.setBorder(Rectangle.BOX);
        avatar.setBorderColor(BORDER);
        avatar.setPadding(escala.paddingTag);
        linha.addCell(avatar);

        PdfPCell nomeCell = new PdfPCell(new Phrase(nome, escala.fontProf));
        nomeCell.setBorder(PdfPCell.NO_BORDER);
        nomeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        nomeCell.setPaddingLeft(6f);
        linha.addCell(nomeCell);

        PdfPCell cell = new PdfPCell(linha);
        cell.setBackgroundColor(fundo);
        cell.setPadding(escala.paddingLinha);
        cell.setBorderColor(BORDER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setMinimumHeight(escala.alturaLinha);
        return cell;
    }

    private PdfPCell celulaTagsSalas(Map<String, Long> porSala, Color fundo, EscalaPdf escala) {
        PdfPTable tags = new PdfPTable(4);
        tags.setWidthPercentage(100);
        for (SalaEstilo estilo : ESTILOS_SALA) {
            long qtd = porSala.getOrDefault(estilo.nome(), 0L);
            tags.addCell(tagSala(estilo, qtd, escala));
        }

        PdfPCell cell = new PdfPCell(tags);
        cell.setBackgroundColor(fundo);
        cell.setPadding(escala.paddingLinha - 1f);
        cell.setBorderColor(BORDER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setMinimumHeight(escala.alturaLinha);
        return cell;
    }

    private PdfPCell tagSala(SalaEstilo estilo, long quantidade, EscalaPdf escala) {
        boolean ativo = quantidade > 0;
        String texto = estilo.nome() + ": " + (ativo ? quantidade + "x" : "-");
        Font fonte = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD,
                escala.fontTagSize,
                Font.BOLD,
                ativo ? estilo.texto() : TEXT_LIGHT
        );
        PdfPCell cell = new PdfPCell(new Phrase(texto, fonte));
        cell.setBackgroundColor(ativo ? estilo.fundo() : new Color(248, 250, 252));
        cell.setBorderColor(ativo ? estilo.borda() : BORDER);
        cell.setBorderWidth(0.8f);
        cell.setPadding(escala.paddingTag);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private PdfPCell celulaTotalSalasUsadas(Map<String, Long> porSala, Color fundo, EscalaPdf escala) {
        long salasUsadas = contarSalasUsadas(porSala);
        String texto = salasUsadas == 1 ? "1 sala usada" : salasUsadas + " salas usadas";

        PdfPCell badge = new PdfPCell(new Phrase(texto, escala.fontTotalBadge));
        badge.setBackgroundColor(TEAL);
        badge.setBorderColor(TEAL);
        badge.setPadding(escala.paddingTag + 2f);
        badge.setHorizontalAlignment(Element.ALIGN_CENTER);
        badge.setVerticalAlignment(Element.ALIGN_MIDDLE);

        PdfPTable centro = new PdfPTable(1);
        centro.setWidthPercentage(100);
        PdfPCell wrapBadge = new PdfPCell(badge);
        wrapBadge.setBorder(PdfPCell.NO_BORDER);
        wrapBadge.setHorizontalAlignment(Element.ALIGN_CENTER);
        wrapBadge.setPadding(2f);
        centro.addCell(wrapBadge);

        PdfPCell cell = new PdfPCell(centro);
        cell.setBackgroundColor(fundo);
        cell.setPadding(escala.paddingLinha);
        cell.setBorderColor(BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setMinimumHeight(escala.alturaLinha);
        return cell;
    }

    private long contarSalasUsadas(Map<String, Long> porSala) {
        long usadas = 0;
        for (SalaEstilo estilo : ESTILOS_SALA) {
            if (porSala.getOrDefault(estilo.nome(), 0L) > 0) {
                usadas++;
            }
        }
        return usadas;
    }

    private PdfPTable tabelaPainel() {
        PdfPTable painel = new PdfPTable(1);
        painel.setWidthPercentage(100);
        return painel;
    }

    private PdfPCell celulaPainel() {
        PdfPCell conteudo = new PdfPCell();
        conteudo.setBorder(Rectangle.BOX);
        conteudo.setBorderColor(BORDER);
        conteudo.setBorderWidth(0.8f);
        conteudo.setBackgroundColor(CARD_BG);
        conteudo.setPadding(6f);
        return conteudo;
    }

    private Map<String, Long> mapaSalas(RelatorioUsoSalaProfissional profissional) {
        Map<String, Long> porSala = new HashMap<>();
        for (RelatorioUsoSalaItem item : profissional.getSalas()) {
            porSala.put(item.getSalaNome(), item.getQuantidade());
        }
        return porSala;
    }

    private void adicionarCabecalhoColuna(PdfPTable table, String texto, EscalaPdf escala) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, escala.fontHeader));
        cell.setBackgroundColor(TEAL);
        cell.setPadding(escala.paddingCabecalho);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setBorderColor(TEAL);
        cell.setBorderWidth(0f);
        cell.setFixedHeight(escala.alturaCabecalho);
        table.addCell(cell);
    }

    private Paragraph paragrafo(String texto, Font fonte, int alinhamento) {
        Paragraph p = new Paragraph(texto, fonte);
        p.setAlignment(alinhamento);
        p.setSpacingAfter(1f);
        return p;
    }

    private Paragraph espaco(float altura) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(altura);
        return p;
    }

    private static final String[] LOGO_CLASSPATH = {
            "images/logo-afetto.png",
            "static/images/logo-afetto.png"
    };

    private Image carregarLogo() {
        for (String caminho : LOGO_CLASSPATH) {
            ClassPathResource resource = new ClassPathResource(caminho);
            if (!resource.exists()) {
                continue;
            }
            try (InputStream entrada = resource.getInputStream()) {
                Image imagem = Image.getInstance(entrada.readAllBytes());
                imagem.setInterpolation(true);
                return imagem;
            } catch (IOException ignored) {
                // tenta o proximo caminho
            }
        }
        return null;
    }

    public String nomeArquivo(RelatorioMensalUsoSalasView relatorio) {
        if (relatorio.isRelatorioSemanal()) {
            return nomeArquivoSemanal(relatorio);
        }
        return "relatorio-salas-"
                + relatorio.getAnoReferencia()
                + "-"
                + String.format("%02d", relatorio.getMesReferencia())
                + ".pdf";
    }

    public String nomeArquivoSemanal(RelatorioMensalUsoSalasView relatorio) {
        String slug = relatorio.getMesReferenciaLabel() != null
                ? relatorio.getMesReferenciaLabel()
                        .toLowerCase(Locale.ROOT)
                        .replace("semana de ", "semana-")
                        .replace(" a ", "-a-")
                        .replace("/", "-")
                        .replaceAll("\\s+", "")
                : "semana-atual";
        return "relatorio-semanal-" + slug + ".pdf";
    }

    private record SalaEstilo(String nome, Color texto, Color fundo, Color borda) {
    }

    private static final class EscalaPdf {
        final float espacoSecao;
        final float espacoPequeno;
        final float espacoAntesTabela;
        final float paddingCard;
        final float paddingCabecalho;
        final float alturaCabecalho;
        final float alturaLinha;
        final float alturaAvatar;
        final int logoLargura;
        final int logoAltura;
        final float logoLarguraExibicao;
        final float logoAlturaExibicao;
        final int tamanhoIconeCard;
        final int tamanhoAvatar;
        final float paddingLinha;
        final float paddingTag;
        final float fontTagSize;
        final boolean ultraCompacto;
        final Font fontBrand;
        final Font fontBrandSub;
        final Font fontReportTitle;
        final Font fontBody;
        final Font fontSmall;
        final Font fontDisclaimer;
        final Font fontHeader;
        final Font fontCardValue;
        final Font fontCardLabel;
        final Font fontProf;
        final Font fontAvatar;
        final Font fontTotalBadge;

        EscalaPdf(float fator, float alturaLinha, int tamanhoIconeCard, int tamanhoAvatar, float paddingLinha, float paddingTag, boolean ultraCompacto) {
            this.alturaLinha = alturaLinha;
            this.tamanhoIconeCard = tamanhoIconeCard;
            this.tamanhoAvatar = tamanhoAvatar;
            this.paddingLinha = paddingLinha;
            this.paddingTag = paddingTag;
            this.ultraCompacto = ultraCompacto;
            this.espacoSecao = 6f * fator;
            this.espacoPequeno = 4f * fator;
            this.espacoAntesTabela = 6f * fator;
            this.paddingCard = 8f * fator;
            this.paddingCabecalho = 6f * fator + 2f;
            this.alturaCabecalho = 22f;
            this.alturaAvatar = Math.max(20f, alturaLinha * 0.72f);
            this.logoLargura = (int) (56 * fator);
            this.logoAltura = (int) (44 * fator);
            this.logoLarguraExibicao = 118f * fator;
            this.logoAlturaExibicao = 72f * fator;
            this.fontTagSize = (ultraCompacto ? 6.5f : 7.5f) * fator;
            this.fontBrand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12 * fator + 2, Font.BOLD, TEAL);
            this.fontBrandSub = FontFactory.getFont(FontFactory.HELVETICA, 8 * fator + 1, Font.NORMAL, TEXT_MUTED);
            this.fontReportTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10 * fator + 2, Font.BOLD, new Color(15, 23, 42));
            this.fontBody = FontFactory.getFont(FontFactory.HELVETICA, 8 * fator + 1, Font.NORMAL, TEXT_MAIN);
            this.fontSmall = FontFactory.getFont(FontFactory.HELVETICA, 7 * fator + 1, Font.NORMAL, TEXT_MUTED);
            this.fontDisclaimer = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7 * fator + 1, Font.ITALIC, TEXT_LIGHT);
            this.fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8 * fator + 1, Font.BOLD, WHITE);
            this.fontCardValue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18 * fator + 4, Font.BOLD, TEAL);
            this.fontCardLabel = FontFactory.getFont(FontFactory.HELVETICA, 7 * fator + 1, Font.NORMAL, TEXT_MUTED);
            this.fontProf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8 * fator + 1, Font.BOLD, TEXT_MAIN);
            this.fontAvatar = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7 * fator + 1, Font.BOLD, TEXT_LIGHT);
            this.fontTotalBadge = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7 * fator + 1, Font.BOLD, WHITE);
        }
    }

    private static class RodapeAfetto extends PdfPageEventHelper {
        private static final Font F_FOOTER = FontFactory.getFont(FontFactory.HELVETICA, 7, Font.NORMAL, TEXT_LIGHT);

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContentUnder();
            Rectangle page = document.getPageSize();
            canvas.setColorFill(PAGE_BG);
            canvas.rectangle(page.getLeft(), page.getBottom(), page.getWidth(), page.getHeight());
            canvas.fill();

            ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    new Phrase("Clinica Afetto — Relatorio mensal de uso de salas", F_FOOTER),
                    (page.getLeft() + page.getRight()) / 2,
                    document.bottomMargin() - 10,
                    0
            );
        }
    }
}
