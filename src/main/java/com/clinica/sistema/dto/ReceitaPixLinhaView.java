package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.util.MoedaBrasilUtil;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Getter
public class ReceitaPixLinhaView {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Long agendamentoId;
    private final String profissionalNome;
    private final String nomeCliente;
    private final String salaNome;
    private final String dataPagamentoRotulo;
    private final String consultaRotulo;
    private final BigDecimal valorTaxa;
    private final String valorTaxaFormatado;

    public ReceitaPixLinhaView(Agendamento agendamento, BigDecimal valorTaxa) {
        this.agendamentoId = agendamento.getId();
        this.profissionalNome = agendamento.getProfissional() != null
                ? agendamento.getProfissional().getNome()
                : "—";
        this.nomeCliente = agendamento.getNomeCliente() != null ? agendamento.getNomeCliente() : "—";
        this.salaNome = agendamento.getSala() != null ? agendamento.getSala().getNome() : "—";
        this.dataPagamentoRotulo = agendamento.getDataPagamento() != null
                ? agendamento.getDataPagamento().format(DATA_HORA)
                : "—";
        this.consultaRotulo = agendamento.getDataHoraInicio() != null
                ? agendamento.getDataHoraInicio().format(DATA_HORA)
                : "—";
        this.valorTaxa = valorTaxa;
        this.valorTaxaFormatado = MoedaBrasilUtil.formatar(valorTaxa);
    }
}
