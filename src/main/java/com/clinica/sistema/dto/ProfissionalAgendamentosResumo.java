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
    private final long totalAvulsos;
    private final long totalFixos;
    private final long totalQuinzenais;

    public ProfissionalAgendamentosResumo(
            Long profissionalId,
            String profissionalNome,
            List<Agendamento> agendamentosAvulsos,
            List<SerieAgendamentoLinha> seriesFixas,
            List<SerieAgendamentoLinha> seriesQuinzenais,
            long totalAvulsos,
            long totalFixos,
            long totalQuinzenais
    ) {
        this.profissionalId = profissionalId;
        this.profissionalNome = profissionalNome;
        this.agendamentosAvulsos = agendamentosAvulsos;
        this.seriesFixas = seriesFixas;
        this.seriesQuinzenais = seriesQuinzenais;
        this.totalAvulsos = totalAvulsos;
        this.totalFixos = totalFixos;
        this.totalQuinzenais = totalQuinzenais;
    }

    public long getTotalGeral() {
        return totalAvulsos + totalFixos + totalQuinzenais;
    }
}
