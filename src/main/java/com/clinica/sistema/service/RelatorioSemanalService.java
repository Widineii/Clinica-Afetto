package com.clinica.sistema.service;

import com.clinica.sistema.dto.PeriodoSemanal;
import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

@Service
public class RelatorioSemanalService {

    public static final String SESSAO_RELATORIO_SEMANAL = "relatorioSemanalTemporario";

    private final AgendamentoService agendamentoService;
    private final RelatorioMensalPdfService relatorioMensalPdfService;

    public RelatorioSemanalService(
            AgendamentoService agendamentoService,
            RelatorioMensalPdfService relatorioMensalPdfService
    ) {
        this.agendamentoService = agendamentoService;
        this.relatorioMensalPdfService = relatorioMensalPdfService;
    }

    public PeriodoSemanal calcularPeriodoSemanaAtual(LocalDate referencia) {
        LocalDate inicio;
        LocalDate fim;

        if (referencia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            inicio = referencia.minusDays(6);
            fim = inicio.plusDays(5);
        } else {
            inicio = referencia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            fim = referencia;
        }

        String label = "Semana de "
                + inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " a "
                + fim.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return new PeriodoSemanal(inicio, fim, label);
    }

    public RelatorioMensalUsoSalasView montarRelatorioSemanalAtual() {
        PeriodoSemanal periodo = calcularPeriodoSemanaAtual(LocalDate.now());
        RelatorioMensalUsoSalasView relatorio = agendamentoService.montarRelatorioUsoSalasNoPeriodoAposRegra24h(
                periodo.inicio(),
                periodo.fim(),
                periodo.label()
        );
        relatorio.setRelatorioSemanal(true);
        relatorio.setTituloRelatorio("RELATORIO SEMANAL DE USO DE SALAS");
        return relatorio;
    }

    public void armazenarNaSessao(HttpSession session, RelatorioMensalUsoSalasView relatorio) {
        PeriodoSemanal periodo = calcularPeriodoSemanaAtual(LocalDate.now());
        session.setAttribute(SESSAO_RELATORIO_SEMANAL, new CacheSemanal(relatorio, periodo.inicio(), periodo.fim()));
    }

    public Optional<RelatorioMensalUsoSalasView> obterDaSessao(HttpSession session) {
        Object valor = session.getAttribute(SESSAO_RELATORIO_SEMANAL);
        if (valor instanceof CacheSemanal cache) {
            return Optional.of(cache.relatorio());
        }
        return Optional.empty();
    }

    public byte[] gerarPdfDaSessao(HttpSession session) {
        RelatorioMensalUsoSalasView relatorio = obterDaSessao(session)
                .orElseThrow(() -> new RuntimeException("Relatorio semanal nao encontrado na sessao."));
        return relatorioMensalPdfService.gerarPdf(relatorio);
    }

    public String nomeArquivoPdf(RelatorioMensalUsoSalasView relatorio) {
        return relatorioMensalPdfService.nomeArquivoSemanal(relatorio);
    }

    public void limparSessao(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSAO_RELATORIO_SEMANAL);
        }
    }

    public record CacheSemanal(
            RelatorioMensalUsoSalasView relatorio,
            LocalDate inicio,
            LocalDate fim
    ) implements Serializable {
    }
}
