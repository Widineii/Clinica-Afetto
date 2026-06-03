package com.clinica.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Entity
@Table(
        name = "agendamentos",
        indexes = {
                @Index(name = "idx_agendamento_data_inicio", columnList = "data_hora_inicio"),
                @Index(name = "idx_agendamento_profissional_data", columnList = "id_usuario, data_hora_inicio"),
                @Index(name = "idx_agendamento_sala_data", columnList = "id_sala, data_hora_inicio")
        }
)
@Data
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario profissional;

    @ManyToOne
    @JoinColumn(name = "id_sala")
    private Sala sala; // O Repository usa esse nome 'sala' para o 'BySalaId'

    private String nomeCliente;

    // O Repository usa esse nome 'dataHoraInicio' para o 'AndDataHoraInicio'
    private LocalDateTime dataHoraInicio;

    private LocalDateTime dataHoraFim;

    private Boolean fixo;

    private String serieFixaId;

    @Column(name = "tipo_recorrencia")
    private String tipoRecorrencia;

    @Column(name = "turno_locacao", length = 20)
    private String turnoLocacao;

    @Column(name = "valor_profissional_recebe", precision = 12, scale = 2)
    private BigDecimal valorProfissionalRecebe;

    @Column(name = "valor_clinica_cobra", precision = 12, scale = 2)
    private BigDecimal valorClinicaCobra;

    @Column(name = "valor_liquido_profissional", precision = 12, scale = 2)
    private BigDecimal valorLiquidoProfissional;

    @Column(name = "indicacao_dona")
    private Boolean indicacaoDona;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status_pagamento", length = 40)
    private PagamentoStatus statusPagamento;

    @Column(name = "pagamento_order_nsu")
    private String pagamentoOrderNsu;

    @Column(name = "pagamento_link", length = 512)
    private String pagamentoLink;

    @Column(name = "pagamento_slug")
    private String pagamentoSlug;

    @Column(name = "valor_pagamento", precision = 12, scale = 2)
    private BigDecimal valorPagamento;

    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;

    @Column(name = "pagamento_iniciado_em")
    private LocalDateTime pagamentoIniciadoEm;

    @Column(name = "pagamento_expira_em")
    private LocalDateTime pagamentoExpiraEm;

    @Column(name = "liberado_em")
    private LocalDateTime liberadoEm;

    @Column(name = "confirmacao_dinheiro_limite_em")
    private LocalDateTime confirmacaoDinheiroLimiteEm;

    @Column(name = "indicacao_aprovada_em")
    private LocalDateTime indicacaoAprovadaEm;

    /** Semana de cobranca (modo semanal): nao muda ao realocar o atendimento. */
    @Column(name = "data_referencia_semana_pagamento")
    private LocalDate dataReferenciaSemanaPagamento;

    /** Mes de cobranca (modo mensal, dia 1): nao muda ao realocar o atendimento. */
    @Column(name = "data_referencia_mes_pagamento")
    private LocalDate dataReferenciaMesPagamento;

    @Column(name = "motivo_encerramento_serie", length = 500)
    private String motivoEncerramentoSerie;

    /** Histórico de datas remarcadas (dd/MM separadas por |), máx. 6 visíveis — reseta na 7ª. */
    @Column(name = "historico_datas_mensal", length = 120)
    private String historicoDatasMensal;

    @Column(name = "serie_encerrada_em")
    private LocalDateTime serieEncerradaEm;

    @Transient
    private String recorrencia;

    @Transient
    public boolean isQuinzenal() {
        if ("QUINZENAL".equalsIgnoreCase(recorrencia) || "QUINZENAL".equalsIgnoreCase(tipoRecorrencia)) {
            return true;
        }
        return possuiMarcadorSerie("quinzenal");
    }

    @Transient
    public boolean isMensal() {
        if ("MENSAL".equalsIgnoreCase(recorrencia) || "MENSAL".equalsIgnoreCase(tipoRecorrencia)) {
            return true;
        }
        return possuiMarcadorSerie("mensal");
    }

    @Transient
    public boolean isFixoSemanal() {
        if ("SEMANAL".equalsIgnoreCase(recorrencia) || "SEMANAL".equalsIgnoreCase(tipoRecorrencia)) {
            return true;
        }
        if (isQuinzenal() || isMensal()) {
            return false;
        }
        return Boolean.TRUE.equals(fixo) && possuiMarcadorSerie("semanal");
    }

    private boolean possuiMarcadorSerie(String marcador) {
        if (serieFixaId == null || serieFixaId.isBlank()) {
            return false;
        }
        String id = serieFixaId.toLowerCase(Locale.ROOT);
        return id.startsWith(marcador + "-") || id.contains("-" + marcador + "-");
    }

    @Transient
    public boolean isAvulso() {
        return !Boolean.TRUE.equals(fixo);
    }

    @Transient
    public boolean isAvulsoSemMensal() {
        return isAvulso() && !isMensal();
    }

    @Transient
    public String getRecorrenciaLabel() {
        if (isQuinzenal()) {
            return "Quinzenal";
        }
        if (isMensal()) {
            return "Mensal";
        }
        if (isFixoSemanal()) {
            return "Fixo";
        }
        return "Avulso";
    }

    @Transient
    public boolean isLocacaoTurno() {
        return turnoLocacao != null && !turnoLocacao.isBlank();
    }

    @Transient
    public String getHorarioExibicaoGrade() {
        if (dataHoraInicio == null) {
            return "";
        }
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("HH:mm");
        if (dataHoraFim != null && dataHoraFim.isAfter(dataHoraInicio.plusHours(1))) {
            return dataHoraInicio.format(formato) + "–" + dataHoraFim.format(formato);
        }
        return dataHoraInicio.format(formato);
    }

    @Transient
    public boolean possuiValoresConsulta() {
        return valorProfissionalRecebe != null || valorClinicaCobra != null;
    }

    @Transient
    public String getValorProfissionalRecebeFormatado() {
        return formatarMoeda(valorProfissionalRecebe);
    }

    @Transient
    public String getValorClinicaCobraFormatado() {
        return formatarMoeda(valorClinicaCobra);
    }

    @Transient
    public String getValorLiquidoProfissionalFormatado() {
        return formatarMoeda(valorLiquidoProfissional);
    }

    @Transient
    public String getValoresConsultaResumo() {
        if (!possuiValoresConsulta()) {
            return null;
        }
        String resumo = "Prof. " + getValorProfissionalRecebeFormatado()
                + " | Clin. " + getValorClinicaCobraFormatado()
                + " | Liq. " + getValorLiquidoProfissionalFormatado();
        if (Boolean.TRUE.equals(indicacaoDona)) {
            resumo += " | Indicacao 30%";
        }
        return resumo;
    }

    @Transient
    public String getValorPagamentoFormatado() {
        return formatarMoeda(valorPagamento);
    }

    @Transient
    public boolean isIndicacaoDona() {
        return Boolean.TRUE.equals(indicacaoDona);
    }

    @Transient
    public boolean isAguardandoAprovacaoIndicacao() {
        return statusPagamento == PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO;
    }

    @Transient
    public boolean isIndicacaoAprovadaPelaDona() {
        return indicacaoAprovadaEm != null;
    }

    @Transient
    public boolean isPagamentoPendente() {
        return statusPagamento == PagamentoStatus.AGUARDANDO_PAGAMENTO
                || statusPagamento == PagamentoStatus.ESPERANDO_CONFIRMACAO
                || statusPagamento == PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO
                || statusPagamento == PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO;
    }

    @Transient
    public boolean isEsperandoConfirmacaoPagamento() {
        return statusPagamento == PagamentoStatus.ESPERANDO_CONFIRMACAO;
    }

    @Transient
    public boolean isAguardandoConfirmacaoDinheiro() {
        return statusPagamento == PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO;
    }

    @Transient
    public boolean confirmacaoDinheiroVencida() {
        if (!isAguardandoConfirmacaoDinheiro() || confirmacaoDinheiroLimiteEm == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(confirmacaoDinheiroLimiteEm);
    }

    @Transient
    public boolean isPagamentoPago() {
        return statusPagamento == PagamentoStatus.PAGO;
    }

    @Transient
    public boolean isLiberadoPorFaltaPagamento() {
        return statusPagamento == PagamentoStatus.LIBERADO_FALTA_PAGAMENTO;
    }

    @Transient
    public boolean isReservaPendenteNaGrade() {
        return statusPagamento == PagamentoStatus.ESPERANDO_CONFIRMACAO
                || statusPagamento == PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO
                || statusPagamento == PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO
                || statusPagamento == PagamentoStatus.AGUARDANDO_PAGAMENTO
                || statusPagamento == PagamentoStatus.PAGAMENTO_FUTURO
                || statusPagamento == PagamentoStatus.LIBERADO_FALTA_PAGAMENTO;
    }

    @Transient
    public String rotuloPendenteNaGrade(boolean podeVerPagamento) {
        if (!isReservaPendenteNaGrade()) {
            return "";
        }
        if (!podeVerPagamento) {
            return "Aguardando confirmacoes";
        }
        if (statusPagamento == PagamentoStatus.ESPERANDO_CONFIRMACAO) {
            return "Esperando pagamento";
        }
        if (statusPagamento == PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO) {
            return "Indicação — aguard. aprovação";
        }
        if (statusPagamento == PagamentoStatus.AGUARDANDO_PAGAMENTO) {
            return isIndicacaoDona() && isIndicacaoAprovadaPelaDona()
                    ? "Indicação — aguard. PIX"
                    : "Aguardando pagamento";
        }
        if (statusPagamento == PagamentoStatus.PAGAMENTO_FUTURO) {
            return "Pagamento em " + formatarDiaPagamentoFuturo();
        }
        if (statusPagamento == PagamentoStatus.LIBERADO_FALTA_PAGAMENTO) {
            return podeVerPagamento
                    ? "Vaga liberada - pague para recuperar"
                    : "Aguardando confirmacoes";
        }
        return "";
    }

    @Transient
    public String rotuloPagoNaGrade(boolean podeVerPagamento) {
        if (!isPagamentoPago()) {
            return "";
        }
        return podeVerPagamento ? "Pago" : "Sala confirmada";
    }

    private String formatarDiaPagamentoFuturo() {
        if (dataHoraInicio == null) {
            return "—";
        }
        return dataHoraInicio.toLocalDate().minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    @Transient
    public boolean possuiQrPagamentoAtivo() {
        return isEsperandoConfirmacaoPagamento()
                && pagamentoLink != null
                && !pagamentoLink.isBlank()
                && pagamentoExpiraEm != null
                && pagamentoExpiraEm.isAfter(LocalDateTime.now());
    }

    @Transient
    public long getSegundosRestantesPagamento() {
        if (pagamentoExpiraEm == null) {
            return 0;
        }
        long segundos = java.time.Duration.between(LocalDateTime.now(), pagamentoExpiraEm).getSeconds();
        return Math.max(0, segundos);
    }

    @Transient
    public String getTempoRestantePagamentoFormatado() {
        long segundos = getSegundosRestantesPagamento();
        long minutos = segundos / 60;
        long resto = segundos % 60;
        return String.format("%d:%02d", minutos, resto);
    }

    private String formatarMoeda(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        NumberFormat formato = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        return formato.format(valor);
    }
}
