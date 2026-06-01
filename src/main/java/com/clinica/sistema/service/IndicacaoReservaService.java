package com.clinica.sistema.service;

import com.clinica.sistema.config.PagamentoProperties;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class IndicacaoReservaService {

    private static final Logger log = LoggerFactory.getLogger(IndicacaoReservaService.class);

    private static final List<PagamentoStatus> STATUS_INCONSISTENTES_INDICACAO = List.of(
            PagamentoStatus.ESPERANDO_CONFIRMACAO,
            PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO
    );

    private final AgendamentoRepository repository;
    private final AuthService authService;
    private final PagamentoProperties pagamentoProperties;
    private final ValorConsultaService valorConsultaService;

    public IndicacaoReservaService(
            AgendamentoRepository repository,
            AuthService authService,
            PagamentoProperties pagamentoProperties,
            ValorConsultaService valorConsultaService
    ) {
        this.repository = repository;
        this.authService = authService;
        this.pagamentoProperties = pagamentoProperties;
        this.valorConsultaService = valorConsultaService;
    }

    public List<Agendamento> listarAguardandoAprovacao() {
        return repository.findByStatusPagamentoOrderByDataHoraInicioAsc(
                PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO
        );
    }

    public int contarAguardandoAprovacao() {
        return (int) repository.countByStatusPagamento(PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO);
    }

    /**
     * Indicações marcadas no formulário que ficaram no fluxo PIX/dinheiro por versão antiga do sistema.
     */
    @Transactional
    public int corrigirIndicacoesComStatusInconsistente() {
        List<Agendamento> inconsistentes = repository.findByIndicacaoDonaTrueAndStatusPagamentoIn(
                STATUS_INCONSISTENTES_INDICACAO
        );
        if (inconsistentes.isEmpty()) {
            return 0;
        }
        int corrigidos = 0;
        for (Agendamento agendamento : inconsistentes) {
            agendamento.setConfirmacaoDinheiroLimiteEm(null);
            agendamento.setPagamentoOrderNsu(null);
            agendamento.setPagamentoLink(null);
            agendamento.setPagamentoSlug(null);
            agendamento.setPagamentoIniciadoEm(null);
            agendamento.setPagamentoExpiraEm(null);
            agendamento.setIndicacaoAprovadaEm(null);
            agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO);
            corrigidos++;
        }
        repository.saveAll(inconsistentes);
        log.info("Corrigidas {} indicação(ões) com status inconsistente para aguardar aprovação.", corrigidos);
        return corrigidos;
    }

    /**
     * Versões antigas marcavam indicação vencida como vaga liberada; volta para aguardar PIX da taxa.
     */
    @Transactional
    public int restaurarIndicacoesLiberadasIncorretamente() {
        List<Agendamento> liberadas = repository.findByIndicacaoDonaTrueAndStatusPagamentoIn(
                List.of(PagamentoStatus.LIBERADO_FALTA_PAGAMENTO)
        );
        if (liberadas.isEmpty()) {
            return 0;
        }
        int restauradas = 0;
        for (Agendamento agendamento : liberadas) {
            if (agendamento.isIndicacaoAprovadaPelaDona() && !agendamento.isPagamentoPago()) {
                agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
                agendamento.setLiberadoEm(null);
                restauradas++;
            }
        }
        if (restauradas > 0) {
            repository.saveAll(liberadas);
            log.info("Restauradas {} indicação(ões) de vaga liberada para aguardar PIX da taxa.", restauradas);
        }
        return restauradas;
    }

    /**
     * Indicação da clínica vale só na 1ª data da série; demais semanas usam taxa normal.
     */
    @Transactional
    public int normalizarIndicacaoApenasPrimeiraConsultaSerie() {
        List<Agendamento> comIndicacao = repository.findByIndicacaoDonaTrueOrderBySerieFixaIdAscDataHoraInicioAsc();
        if (comIndicacao.isEmpty()) {
            return 0;
        }
        int corrigidos = 0;
        for (Agendamento agendamento : comIndicacao) {
            String serieId = agendamento.getSerieFixaId();
            if (serieId == null || serieId.isBlank()) {
                continue;
            }
            Agendamento primeiro = repository.findFirstBySerieFixaIdOrderByDataHoraInicioAsc(serieId)
                    .orElse(agendamento);
            if (agendamento.getId() != null && agendamento.getId().equals(primeiro.getId())) {
                continue;
            }
            agendamento.setIndicacaoDona(false);
            if (agendamento.getSala() != null && agendamento.getValorProfissionalRecebe() != null) {
                String recorrencia = agendamento.getTipoRecorrencia() != null
                        ? agendamento.getTipoRecorrencia()
                        : "SEMANAL";
                BigDecimal taxa = valorConsultaService.calcularTarifaClinicaPadrao(agendamento.getSala(), recorrencia);
                agendamento.setValorClinicaCobra(taxa);
                agendamento.setValorLiquidoProfissional(
                        valorConsultaService.calcularLiquido(agendamento.getValorProfissionalRecebe(), taxa)
                );
            }
            corrigidos++;
        }
        if (corrigidos > 0) {
            repository.saveAll(comIndicacao);
            log.info("Removida indicação de {} consulta(s) que não são a 1ª da série.", corrigidos);
        }
        return corrigidos;
    }

    @Transactional
    public Agendamento aprovarReservaIndicacao(Long agendamentoId, Usuario aprovador) {
        validarAprovador(aprovador);
        Agendamento agendamento = repository.findById(agendamentoId)
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado."));
        if (!agendamento.isIndicacaoDona()) {
            throw new RuntimeException("Este agendamento não é uma indicação.");
        }
        if (!PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Esta indicação não está aguardando aprovação.");
        }
        agendamento.setIndicacaoAprovadaEm(LocalDateTime.now());
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        return repository.save(agendamento);
    }

    public boolean isIndicacaoAguardandoPix(Agendamento agendamento) {
        return agendamento != null
                && agendamento.isIndicacaoDona()
                && agendamento.isIndicacaoAprovadaPelaDona()
                && PagamentoStatus.AGUARDANDO_PAGAMENTO.equals(agendamento.getStatusPagamento());
    }

    public boolean dentroJanelaPagamentoIndicacao(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        LocalDateTime inicio = agendamento.getDataHoraInicio();
        int diasLimite = Math.max(1, pagamentoProperties.getIndicacaoDiasLimitePosAtendimento());
        LocalDateTime limite = agendamento.getDataHoraInicio().toLocalDate()
                .plusDays(diasLimite)
                .atTime(LocalTime.of(23, 59, 59));
        LocalDateTime agora = LocalDateTime.now();
        return !agora.isBefore(inicio) && !agora.isAfter(limite);
    }

    public String rotuloPrazoPagamentoIndicacao(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return "";
        }
        int dias = Math.max(1, pagamentoProperties.getIndicacaoDiasLimitePosAtendimento());
        LocalDateTime limite = agendamento.getDataHoraInicio().toLocalDate()
                .plusDays(dias)
                .atTime(LocalTime.of(23, 59, 59));
        return "Pague pelo site (PIX) após o atendimento, até "
                + limite.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    /**
     * Indicação vencida permanece aguardando pagamento; o bloqueio da agenda usa prazo
     * (2 dias após o atendimento) calculado em PagamentoConsultaService.
     */
    @Transactional
    public int processarIndicacoesNaoPagasVencidas() {
        return 0;
    }

    private void validarAprovador(Usuario aprovador) {
        if (aprovador == null || (!authService.isDonaClinica(aprovador) && !authService.isAdmin(aprovador))) {
            throw new RuntimeException("Apenas a dona da clínica ou administração pode aprovar indicações.");
        }
    }
}
