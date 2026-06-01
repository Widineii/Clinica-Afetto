package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import lombok.Getter;

import java.util.List;

@Getter
public class ProfissionalAgendamentosResumo {

    private final Long profissionalId;
    private final String profissionalNome;
    private final List<Agendamento> agendamentosAvulsos;
    private final List<SerieAgendamentoLinha> seriesFixas;
    private final List<SerieAgendamentoLinha> seriesQuinzenais;
    private final List<MensalAgendamentoLinha> linhasMensais;
    private final long totalAvulsos;
    private final long totalFixos;
    private final long totalQuinzenais;
    private final long totalMensais;

    public ProfissionalAgendamentosResumo(
            Long profissionalId,
            String profissionalNome,
            List<Agendamento> agendamentosAvulsos,
            List<SerieAgendamentoLinha> seriesFixas,
            List<SerieAgendamentoLinha> seriesQuinzenais,
            List<MensalAgendamentoLinha> linhasMensais,
            long totalAvulsos,
            long totalFixos,
            long totalQuinzenais,
            long totalMensais
    ) {
        this.profissionalId = profissionalId;
        this.profissionalNome = profissionalNome;
        this.agendamentosAvulsos = agendamentosAvulsos;
        this.seriesFixas = seriesFixas;
        this.seriesQuinzenais = seriesQuinzenais;
        this.linhasMensais = linhasMensais;
        this.totalAvulsos = totalAvulsos;
        this.totalFixos = totalFixos;
        this.totalQuinzenais = totalQuinzenais;
        this.totalMensais = totalMensais;
    }

    public long getTotalGeral() {
        return totalAvulsos + totalFixos + totalQuinzenais + totalMensais;
    }
}
