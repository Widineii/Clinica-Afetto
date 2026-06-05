package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.util.MoedaBrasilUtil;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Getter
public class ReceitaPendenteLinhaView {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Long agendamentoId;
    private final String profissionalNome;
    private final String profissionalChave;
    private final String nomeCliente;
    private final String salaNome;
    private final String salaChave;
    private final String tipoRecorrencia;
    private final String tipoRecorrenciaRotulo;
    private final String consultaRotulo;
    private final LocalDate dataConsulta;
    private final String dataConsultaRotulo;
    private final String statusPagamentoRotulo;
    private final BigDecimal valorTaxa;
    private final String valorTaxaFormatado;

    public ReceitaPendenteLinhaView(Agendamento agendamento, BigDecimal valorTaxa) {
        this.agendamentoId = agendamento.getId();
        this.profissionalNome = agendamento.getProfissional() != null
                ? agendamento.getProfissional().getNome()
                : "—";
        this.profissionalChave = normalizarChave(this.profissionalNome);
        this.nomeCliente = agendamento.getNomeCliente() != null ? agendamento.getNomeCliente() : "—";
        this.salaNome = agendamento.getSala() != null ? agendamento.getSala().getNome() : "—";
        this.salaChave = normalizarChave(this.salaNome);
        this.tipoRecorrencia = resolverTipoRecorrencia(agendamento);
        this.tipoRecorrenciaRotulo = rotularTipoRecorrencia(this.tipoRecorrencia);
        this.consultaRotulo = agendamento.getDataHoraInicio() != null
                ? agendamento.getDataHoraInicio().format(DATA_HORA)
                : "—";
        this.dataConsulta = agendamento.getDataHoraInicio() != null
                ? agendamento.getDataHoraInicio().toLocalDate()
                : null;
        this.dataConsultaRotulo = this.dataConsulta != null
                ? this.dataConsulta.format(DATA)
                : "—";
        this.statusPagamentoRotulo = rotularStatusPagamento(agendamento.getStatusPagamento());
        this.valorTaxa = valorTaxa;
        this.valorTaxaFormatado = MoedaBrasilUtil.formatar(valorTaxa);
    }

    private static String rotularStatusPagamento(PagamentoStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case PAGAMENTO_FUTURO -> "Pagamento futuro";
            case ESPERANDO_CONFIRMACAO -> "PIX aguardando confirmação";
            case AGUARDANDO_PAGAMENTO -> "Aguardando pagamento";
            case AGUARDANDO_APROVACAO_INDICACAO -> "Indicação — aguard. aprovação";
            case AGUARDANDO_CONFIRMACAO_DINHEIRO -> "Aguardando confirmação";
            case LIBERADO_FALTA_PAGAMENTO -> "Prazo expirado (vaga liberada)";
            case PAGO -> "Pago";
        };
    }

    private static String resolverTipoRecorrencia(Agendamento agendamento) {
        if (agendamento.getTipoRecorrencia() != null && !agendamento.getTipoRecorrencia().isBlank()) {
            return agendamento.getTipoRecorrencia().trim().toUpperCase();
        }
        return Boolean.TRUE.equals(agendamento.getFixo()) ? "SEMANAL" : "AVULSO";
    }

    private static String rotularTipoRecorrencia(String tipo) {
        return switch (tipo) {
            case "SEMANAL" -> "Fixo semanal";
            case "QUINZENAL" -> "Quinzenal";
            default -> "Avulso";
        };
    }

    private static String normalizarChave(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
