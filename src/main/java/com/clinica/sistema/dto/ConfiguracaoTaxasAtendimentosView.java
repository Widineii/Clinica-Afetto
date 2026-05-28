package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import lombok.Getter;

import java.util.List;

@Getter
public class ConfiguracaoTaxasAtendimentosView {

    private final List<Agendamento> avulsos;
    private final List<SerieAgendamentoLinha> seriesFixas;
    private final List<SerieAgendamentoLinha> seriesQuinzenais;
    private final int totalNoMes;

    public ConfiguracaoTaxasAtendimentosView(
            List<Agendamento> avulsos,
            List<SerieAgendamentoLinha> seriesFixas,
            List<SerieAgendamentoLinha> seriesQuinzenais,
            int totalNoMes
    ) {
        this.avulsos = avulsos != null ? avulsos : List.of();
        this.seriesFixas = seriesFixas != null ? seriesFixas : List.of();
        this.seriesQuinzenais = seriesQuinzenais != null ? seriesQuinzenais : List.of();
        this.totalNoMes = totalNoMes;
    }

    public static ConfiguracaoTaxasAtendimentosView vazio() {
        return new ConfiguracaoTaxasAtendimentosView(List.of(), List.of(), List.of(), 0);
    }

    public boolean isVazio() {
        return avulsos.isEmpty() && seriesFixas.isEmpty() && seriesQuinzenais.isEmpty();
    }
}
