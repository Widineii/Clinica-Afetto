package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.util.MoedaBrasilUtil;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Getter
public class RelatorioProfissionalAtendimentoView {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String consultaRotulo;
    private final String nomeCliente;
    private final String salaNome;
    private final String tipoRotulo;
    private final String statusPagamentoRotulo;
    private final BigDecimal valorTaxa;
    private final String valorTaxaFormatado;

    public RelatorioProfissionalAtendimentoView(
            Agendamento agendamento,
            String statusPagamentoRotulo,
            BigDecimal valorTaxa
    ) {
        this.consultaRotulo = agendamento.getDataHoraInicio() != null
                ? agendamento.getDataHoraInicio().format(DATA_HORA)
                : "—";
        this.nomeCliente = agendamento.getNomeCliente() != null ? agendamento.getNomeCliente() : "—";
        this.salaNome = agendamento.getSala() != null ? agendamento.getSala().getNome() : "—";
        this.tipoRotulo = rotularTipo(agendamento);
        this.statusPagamentoRotulo = statusPagamentoRotulo != null ? statusPagamentoRotulo : "—";
        this.valorTaxa = valorTaxa != null ? valorTaxa : BigDecimal.ZERO;
        this.valorTaxaFormatado = MoedaBrasilUtil.formatar(this.valorTaxa);
    }

    private static String rotularTipo(Agendamento agendamento) {
        if (agendamento.isQuinzenal()) {
            return "Quinzenal";
        }
        if (agendamento.isFixoSemanal()) {
            return "Fixo semanal";
        }
        return "Avulso";
    }
}
