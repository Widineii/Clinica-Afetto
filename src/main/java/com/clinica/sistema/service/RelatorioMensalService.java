package com.clinica.sistema.service;

import com.clinica.sistema.dto.RelatorioArquivadoCabecalhoProjection;
import com.clinica.sistema.dto.RelatorioHistoricoResumo;
import com.clinica.sistema.dto.RelatorioMensalNotificacaoView;
import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.model.RelatorioMensalArquivado;
import com.clinica.sistema.repository.RelatorioMensalArquivadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RelatorioMensalService {

    private static final Logger log = LoggerFactory.getLogger(RelatorioMensalService.class);

    private final AgendamentoService agendamentoService;
    private final RelatorioMensalPdfService relatorioMensalPdfService;
    private final RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository;
    private final JsonMapper jsonMapper;

    @Value("${app.relatorio-mensal.dia-fechamento:3}")
    private int diaFechamento;

    @Value("${app.relatorio-mensal.dia-remocao-pdf:10}")
    private int diaRemocaoPdf;

    public RelatorioMensalService(
            AgendamentoService agendamentoService,
            RelatorioMensalPdfService relatorioMensalPdfService,
            RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository,
            JsonMapper jsonMapper
    ) {
        this.agendamentoService = agendamentoService;
        this.relatorioMensalPdfService = relatorioMensalPdfService;
        this.relatorioMensalArquivadoRepository = relatorioMensalArquivadoRepository;
        this.jsonMapper = jsonMapper;
    }

    /** Sempre o mes anterior ao calendario de hoje (nunca o mes atual). */
    public YearMonth mesPassadoReferencia() {
        return YearMonth.now().minusMonths(1);
    }

    public String formatarMesReferencia(YearMonth mesReferencia) {
        String mes = mesReferencia.getMonth()
                .getDisplayName(TextStyle.FULL_STANDALONE, new Locale("pt", "BR"));
        if (mes != null && !mes.isBlank()) {
            mes = Character.toUpperCase(mes.charAt(0)) + mes.substring(1);
        }
        return mes + " de " + mesReferencia.getYear();
    }

    public String mensagemRelatorioDisponivelAposDia3() {
        return "O relatorio de "
                + formatarMesReferencia(mesPassadoReferencia())
                + " (mes passado) so fica disponivel a partir do dia "
                + diaFechamento
                + " deste mes. Nesse dia o sistema arquiva os dados, exibe o PDF na tela (gerado na hora) "
                + "e remove agendamentos avulsos daquele mes.";
    }

    public String mensagemRelatorioSaiuDaTela() {
        return "O relatorio do mes passado saiu da tela no dia "
                + diaRemocaoPdf
                + " deste mes. Volte no dia "
                + diaFechamento
                + " do proximo mes para o novo relatorio.";
    }

    /**
     * Relatorio mensal na interface: do dia de fechamento ate o dia anterior a remocao
     * (ex.: dias 3 a 9), com JSON arquivado. A partir do dia de remocao some da tela.
     */
    public boolean relatorioMensalVisivelNaTela(YearMonth mesReferencia) {
        LocalDate hoje = LocalDate.now();
        if (!podeExecutarFechamentoAutomatico(hoje)) {
            return false;
        }
        if (!temDadosArquivados(mesReferencia)) {
            return false;
        }
        return !pdfExpiradoParaRelatorio(
                mesReferencia.getYear(),
                mesReferencia.getMonthValue(),
                hoje
        );
    }

    public boolean relatorioSaiuDaTela(YearMonth mesReferencia) {
        return temDadosArquivados(mesReferencia)
                && pdfExpiradoParaRelatorio(
                        mesReferencia.getYear(),
                        mesReferencia.getMonthValue(),
                        LocalDate.now()
                );
    }

    public boolean podeExecutarFechamentoAutomatico() {
        return podeExecutarFechamentoAutomatico(LocalDate.now());
    }

    boolean podeExecutarFechamentoAutomatico(LocalDate referencia) {
        return referencia.getDayOfMonth() >= diaFechamento;
    }

    public static final String URL_RELATORIO_VIA_NOTIFICACAO =
            "/agendamentos/relatorio/mensal?viaNotificacao=1";

    public static final String SESSAO_NOTIFICACAO_PDF_MENSAL_BAIXADO =
            "notificacaoRelatorioPdfMensalBaixado";

    /**
     * Sino na agenda: a partir do dia de fechamento, enquanto o relatorio do mes passado
     * ainda pode ser gerado ou baixado em PDF.
     */
    public Optional<RelatorioMensalNotificacaoView> avaliarNotificacaoMensal() {
        return avaliarNotificacaoMensal(LocalDate.now(), null);
    }

    Optional<RelatorioMensalNotificacaoView> avaliarNotificacaoMensal(LocalDate referencia) {
        return avaliarNotificacaoMensal(referencia, null);
    }

    Optional<RelatorioMensalNotificacaoView> avaliarNotificacaoMensal(
            LocalDate referencia,
            jakarta.servlet.http.HttpSession session
    ) {
        if (!podeExecutarFechamentoAutomatico(referencia)) {
            return Optional.empty();
        }

        YearMonth mesPassado = mesPassadoReferencia();
        String mesLabel = formatarMesReferencia(mesPassado);
        int ano = mesPassado.getYear();
        int mes = mesPassado.getMonthValue();
        boolean arquivadoExiste = relatorioMensalArquivadoRepository.existsByAnoAndMes(ano, mes);

        if (arquivadoExiste && !relatorioMensalArquivadoRepository.existsComDadosJson(ano, mes)) {
            return Optional.empty();
        }

        boolean pendente = !arquivadoExiste;
        boolean temDadosSalvos = arquivadoExiste
                && relatorioMensalArquivadoRepository.existsComDadosJson(ano, mes);
        boolean pdfDisponivel = temDadosSalvos && !pdfExpiradoParaRelatorio(ano, mes, referencia);

        if (!pendente && !pdfDisponivel) {
            return Optional.empty();
        }

        if (pendente && agendamentoService.contarAgendamentosNoMes(mesPassado) == 0) {
            return Optional.empty();
        }

        String mensagemPainel = pendente
                ? "O relatorio de " + mesLabel + " sera gerado no dia " + diaFechamento
                        + " deste mes. Ate la, a agenda do mes passado permanece como esta."
                : "O relatorio de " + mesLabel + " foi gerado no dia " + diaFechamento
                        + " (fechamento automatico). Confira os numeros e baixe o PDF mensal. "
                        + "Os avulsos daquele mes ja foram removidos da agenda.";

        String mensagemResumo = pendente
                ? "Relatorio de " + mesLabel + " aguarda o dia " + diaFechamento
                : "Relatorio de " + mesLabel + " gerado — aguardando download";

        return Optional.of(new RelatorioMensalNotificacaoView(
                mesLabel,
                mensagemResumo,
                mensagemPainel,
                pendente,
                URL_RELATORIO_VIA_NOTIFICACAO
        ));
    }

    public static String chaveMesReferencia(YearMonth mesReferencia) {
        return mesReferencia.getYear() + "-" + mesReferencia.getMonthValue();
    }

    public boolean jaBaixouPdfMensalDaNotificacao(
            jakarta.servlet.http.HttpSession session,
            YearMonth mesReferencia
    ) {
        if (session == null) {
            return false;
        }
        Object valor = session.getAttribute(SESSAO_NOTIFICACAO_PDF_MENSAL_BAIXADO);
        return valor != null && chaveMesReferencia(mesReferencia).equals(valor.toString());
    }

    public void marcarPdfMensalBaixadoNaNotificacao(
            jakarta.servlet.http.HttpSession session,
            YearMonth mesReferencia
    ) {
        if (session != null) {
            session.setAttribute(SESSAO_NOTIFICACAO_PDF_MENSAL_BAIXADO, chaveMesReferencia(mesReferencia));
        }
        relatorioMensalArquivadoRepository.findByAnoAndMes(
                mesReferencia.getYear(),
                mesReferencia.getMonthValue()
        ).ifPresent(arquivado -> {
            if (arquivado.getPdfNotificacaoBaixadoEm() == null) {
                arquivado.setPdfNotificacaoBaixadoEm(LocalDateTime.now());
                relatorioMensalArquivadoRepository.save(arquivado);
            }
        });
    }

    public boolean notificacaoMensalJaFoiAtendida(
            jakarta.servlet.http.HttpSession session,
            YearMonth mesReferencia
    ) {
        if (jaBaixouPdfMensalDaNotificacao(session, mesReferencia)) {
            return true;
        }
        return relatorioMensalArquivadoRepository.findPdfNotificacaoBaixadoEmByAnoAndMes(
                mesReferencia.getYear(),
                mesReferencia.getMonthValue()
        ).isPresent();
    }

    public boolean deveExibirBolinhaNotificacao(jakarta.servlet.http.HttpSession session) {
        YearMonth mesPassado = mesPassadoReferencia();
        return avaliarNotificacaoMensal(LocalDate.now()).isPresent()
                && !notificacaoMensalJaFoiAtendida(session, mesPassado);
    }

    public void adicionarNotificacaoAoModelSeAplicavel(
            org.springframework.ui.Model model,
            jakarta.servlet.http.HttpSession session
    ) {
        Optional<RelatorioMensalNotificacaoView> notificacao =
                avaliarNotificacaoMensal(LocalDate.now());
        YearMonth mesPassado = mesPassadoReferencia();
        boolean exibirBolinha = notificacao.isPresent()
                && !notificacaoMensalJaFoiAtendida(session, mesPassado);

        model.addAttribute("notificacaoRelatorioMensal", exibirBolinha ? notificacao.orElse(null) : null);
        model.addAttribute("exibirBolinhaNotificacaoRelatorio", exibirBolinha);
    }

    public int getDiaRemocaoPdf() {
        return diaRemocaoPdf;
    }

    public boolean temPdfDisponivel(RelatorioMensalArquivado arquivado) {
        return arquivado != null && arquivado.temPdfDisponivel();
    }

    public boolean podeExportarPdf(YearMonth mesReferencia) {
        return relatorioMensalVisivelNaTela(mesReferencia);
    }

    /** Pode baixar se o relatorio foi arquivado e ainda esta no periodo visivel na tela. */
    public boolean podeExportarPdf(RelatorioMensalArquivado arquivado) {
        if (arquivado == null
                || arquivado.getDadosJson() == null
                || arquivado.getDadosJson().isBlank()) {
            return false;
        }
        return relatorioMensalVisivelNaTela(YearMonth.of(arquivado.getAno(), arquivado.getMes()));
    }

    public boolean pdfRemovidoDoBanco(RelatorioMensalArquivado arquivado) {
        return arquivado != null && !arquivado.temPdfDisponivel() && podeExportarPdf(arquivado);
    }

    public byte[] obterPdfParaDownload(YearMonth mesReferencia) {
        if (!podeExportarPdf(mesReferencia)) {
            throw new RuntimeException(
                    "Relatorio de " + formatarMesReferencia(mesReferencia)
                            + " nao esta disponivel para download neste periodo."
            );
        }
        RelatorioMensalArquivado arquivado = buscarArquivado(mesReferencia)
                .orElseThrow(() -> new RuntimeException("Relatorio nao encontrado para o periodo informado."));
        return gerarPdfDoArquivado(arquivado);
    }

    /** Gera PDF a partir do JSON arquivado, sem persistir bytes no banco. */
    public byte[] gerarPdfDoArquivado(RelatorioMensalArquivado arquivado) {
        RelatorioMensalUsoSalasView relatorio = desserializarRelatorio(arquivado.getDadosJson());
        byte[] pdf = relatorioMensalPdfService.gerarPdf(relatorio);
        log.debug("PDF do relatorio {} gerado sob demanda ({} bytes).",
                relatorio.getMesReferenciaLabel(), pdf.length);
        return pdf;
    }

    /** @deprecated PDF nao e mais gravado no banco; use {@link #gerarPdfDoArquivado}. */
    @Deprecated
    @Transactional
    public byte[] regenerarESalvarPdf(RelatorioMensalArquivado arquivado) {
        return gerarPdfDoArquivado(arquivado);
    }

    /**
     * PDF do relatorio de um mes fica disponivel ate o dia anterior ao diaRemocaoPdf
     * do mes seguinte (ex.: abril some no dia 10 de maio).
     */
    boolean pdfExpiradoParaRelatorio(int ano, int mes, LocalDate referencia) {
        LocalDate inicioRemocao = YearMonth.of(ano, mes).plusMonths(1).atDay(diaRemocaoPdf);
        return !referencia.isBefore(inicioRemocao);
    }

    /** Remove bytes legados de PDF ainda gravados em versoes antigas; JSON permanece arquivado. */
    @Transactional
    public int removerPdfsExpiradosSeDevido() {
        LocalDate hoje = LocalDate.now();
        if (hoje.getDayOfMonth() < diaRemocaoPdf) {
            return 0;
        }

        int removidos = 0;
        for (RelatorioMensalArquivado arquivado : relatorioMensalArquivadoRepository.findByPdfIsNotNull()) {
            if (!pdfExpiradoParaRelatorio(arquivado.getAno(), arquivado.getMes(), hoje)) {
                continue;
            }
            arquivado.setPdf(null);
            arquivado.setPdfRemovidoEm(LocalDateTime.now());
            relatorioMensalArquivadoRepository.save(arquivado);
            removidos++;
        }

        if (removidos > 0) {
            log.info("Removidos {} PDF(s) de relatorios mensais para liberar espaco no banco.", removidos);
        }
        return removidos;
    }

    /** @return true se gerou/arquivou o relatorio nesta execucao */
    public boolean executarFechamentoAutomaticoSeDevido() {
        if (!podeExecutarFechamentoAutomatico()) {
            return false;
        }
        try {
            return arquivarMesSeNecessario(mesPassadoReferencia()).isPresent();
        } catch (RuntimeException e) {
            log.error("Fechamento automatico do relatorio mensal falhou", e);
            return false;
        }
    }

    /**
     * Fechamento do mes passado: monta relatorio, arquiva JSON e so depois
     * apaga agendamentos avulsos daquele mes (semanal/quinzenal permanecem).
     */
    @Transactional
    public Optional<RelatorioMensalArquivado> arquivarMesSeNecessario(YearMonth mesReferencia) {
        if (relatorioMensalArquivadoRepository.existsByAnoAndMes(
                mesReferencia.getYear(),
                mesReferencia.getMonthValue()
        )) {
            Optional<RelatorioMensalArquivado> existente = relatorioMensalArquivadoRepository.findByAnoAndMes(
                    mesReferencia.getYear(),
                    mesReferencia.getMonthValue()
            );
            existente.ifPresent(ignored -> garantirRemocaoAvulsosDoMesArquivado(mesReferencia));
            return existente;
        }

        RelatorioMensalUsoSalasView relatorio = agendamentoService.montarRelatorioMensalUsoSalas(mesReferencia);

        RelatorioMensalArquivado arquivado = new RelatorioMensalArquivado();
        arquivado.setAno(mesReferencia.getYear());
        arquivado.setMes(mesReferencia.getMonthValue());
        arquivado.setMesLabel(relatorio.getMesReferenciaLabel());
        arquivado.setTotalGeral(relatorio.getTotalGeral());
        arquivado.setGeradoEm(LocalDateTime.now());
        arquivado.setPdf(null);
        arquivado.setPdfRemovidoEm(null);
        arquivado.setDadosJson(serializarRelatorio(relatorio));

        LocalDateTime inicio = mesReferencia.atDay(1).atStartOfDay();
        LocalDateTime fim = mesReferencia.plusMonths(1).atDay(1).atStartOfDay();
        long removidos = agendamentoService.limparAgendamentosNoPeriodo(inicio, fim);
        arquivado.setAgendamentosRemovidos(removidos);

        RelatorioMensalArquivado salvo = relatorioMensalArquivadoRepository.save(arquivado);
        log.info(
                "Relatorio mensal arquivado para {}: {} horarios, {} agendamentos removidos.",
                relatorio.getMesReferenciaLabel(),
                relatorio.getTotalGeral(),
                removidos
        );
        return Optional.of(salvo);
    }

    /**
     * Se o mes ja foi arquivado mas ainda restam avulsos no periodo (ex.: relatorio criado antes
     * da limpeza ou falha parcial), remove agora. Idempotente quando nao ha mais avulsos.
     */
    @Transactional
    public long garantirRemocaoAvulsosDoMesArquivado(YearMonth mesReferencia) {
        Optional<RelatorioMensalArquivado> arquivado = buscarArquivado(mesReferencia);
        if (arquivado.isEmpty()) {
            return 0;
        }

        LocalDateTime inicio = mesReferencia.atDay(1).atStartOfDay();
        LocalDateTime fim = mesReferencia.plusMonths(1).atDay(1).atStartOfDay();
        long removidosAgora = agendamentoService.limparAgendamentosNoPeriodo(inicio, fim);
        if (removidosAgora <= 0) {
            return 0;
        }

        RelatorioMensalArquivado registro = arquivado.get();
        registro.setAgendamentosRemovidos(registro.getAgendamentosRemovidos() + removidosAgora);
        relatorioMensalArquivadoRepository.save(registro);
        log.info(
                "Limpeza de avulsos do mes {}: {} removido(s) (relatorio ja estava arquivado).",
                formatarMesReferencia(mesReferencia),
                removidosAgora
        );
        return removidosAgora;
    }

    public Optional<RelatorioMensalArquivado> buscarArquivado(YearMonth mesReferencia) {
        return relatorioMensalArquivadoRepository.findByAnoAndMes(
                mesReferencia.getYear(),
                mesReferencia.getMonthValue()
        );
    }

    public List<RelatorioMensalArquivado> listarArquivados() {
        return relatorioMensalArquivadoRepository.findAllByOrderByAnoDescMesDesc();
    }

    public List<RelatorioHistoricoResumo> listarHistoricoResumo() {
        return relatorioMensalArquivadoRepository.listarHistoricoMetadados().stream()
                .map(item -> new RelatorioHistoricoResumo(
                        item.getAno(),
                        item.getMes(),
                        item.getMesLabel(),
                        relatorioMensalVisivelNaTela(YearMonth.of(item.getAno(), item.getMes()))
                ))
                .toList();
    }

    @Transactional
    public boolean removerArquivoMesPassadoSeExistir() {
        YearMonth mesPassado = mesPassadoReferencia();
        Optional<RelatorioMensalArquivado> arquivado = relatorioMensalArquivadoRepository.findByAnoAndMes(
                mesPassado.getYear(),
                mesPassado.getMonthValue()
        );
        if (arquivado.isEmpty()) {
            return false;
        }
        relatorioMensalArquivadoRepository.delete(arquivado.get());
        log.info("Arquivo de relatorio removido para teste: {}", mesPassado);
        return true;
    }

    public RelatorioMensalUsoSalasView carregarRelatorioParaExibicao(YearMonth mesReferencia) {
        return relatorioMensalArquivadoRepository.findDadosJsonByAnoAndMes(
                        mesReferencia.getYear(),
                        mesReferencia.getMonthValue()
                )
                .filter(json -> json != null && !json.isBlank())
                .map(json -> {
                    try {
                        return desserializarRelatorio(json);
                    } catch (RuntimeException e) {
                        log.warn(
                                "Relatorio arquivado de {} com JSON invalido; remontando a partir dos agendamentos.",
                                formatarMesReferencia(mesReferencia),
                                e
                        );
                        return agendamentoService.montarRelatorioMensalUsoSalas(mesReferencia);
                    }
                })
                .orElseGet(() -> agendamentoService.montarRelatorioMensalUsoSalas(mesReferencia));
    }

    public boolean temPdfSalvoNoBanco(YearMonth mesReferencia) {
        return relatorioMensalArquivadoRepository.temPdfByAnoAndMes(
                mesReferencia.getYear(),
                mesReferencia.getMonthValue()
        ).orElse(false);
    }

    public boolean temDadosArquivados(YearMonth mesReferencia) {
        return relatorioMensalArquivadoRepository.existsComDadosJson(
                mesReferencia.getYear(),
                mesReferencia.getMonthValue()
        );
    }

    public Optional<RelatorioArquivadoCabecalhoProjection> buscarCabecalhoArquivado(
            YearMonth mesReferencia
    ) {
        return relatorioMensalArquivadoRepository.findCabecalhoByAnoAndMes(
                mesReferencia.getYear(),
                mesReferencia.getMonthValue()
        );
    }

    public void regenerarPdfDoMesSePossivel(YearMonth mesReferencia) {
        // PDF e gerado sob demanda na visualizacao/download; nada a persistir.
    }

    public String nomeArquivoPdf(YearMonth mesReferencia) {
        RelatorioMensalUsoSalasView relatorio = carregarRelatorioParaExibicao(mesReferencia);
        return relatorioMensalPdfService.nomeArquivo(relatorio);
    }

    private String serializarRelatorio(RelatorioMensalUsoSalasView relatorio) {
        try {
            return jsonMapper.writeValueAsString(relatorio);
        } catch (JacksonException e) {
            throw new RuntimeException("Nao foi possivel salvar o relatorio mensal.", e);
        }
    }

    private RelatorioMensalUsoSalasView desserializarRelatorio(String dadosJson) {
        try {
            return jsonMapper.readValue(dadosJson, RelatorioMensalUsoSalasView.class);
        } catch (JacksonException e) {
            throw new RuntimeException("Nao foi possivel ler o relatorio mensal arquivado.", e);
        }
    }
}
