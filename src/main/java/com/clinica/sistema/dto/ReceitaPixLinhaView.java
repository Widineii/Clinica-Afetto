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
    private final String profissionalChave;
    private final String nomeCliente;
    private final String salaNome;
    private final String salaChave;
    private final String tipoRecorrencia;
    private final String tipoRecorrenciaRotulo;
    private final String dataPagamentoRotulo;
    private final String consultaRotulo;
    private final BigDecimal valorTaxa;
    private final String valorTaxaFormatado;

    public ReceitaPixLinhaView(Agendamento agendamento, BigDecimal valorTaxa) {
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
        this.dataPagamentoRotulo = agendamento.getDataPagamento() != null
                ? agendamento.getDataPagamento().format(DATA_HORA)
                : "—";
        this.consultaRotulo = agendamento.getDataHoraInicio() != null
                ? agendamento.getDataHoraInicio().format(DATA_HORA)
                : "—";
        this.valorTaxa = valorTaxa;
        this.valorTaxaFormatado = MoedaBrasilUtil.formatar(valorTaxa);
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
