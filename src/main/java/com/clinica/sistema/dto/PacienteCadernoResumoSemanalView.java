package com.clinica.sistema.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class PacienteCadernoResumoSemanalView {

    private final String periodoRotulo;
    private final List<String> linhas;
    private final int totalAnotacoes;

    public PacienteCadernoResumoSemanalView(String periodoRotulo, List<String> linhas, int totalAnotacoes) {
        this.periodoRotulo = periodoRotulo != null ? periodoRotulo : "";
        this.linhas = linhas != null ? linhas : List.of();
        this.totalAnotacoes = Math.max(0, totalAnotacoes);
    }

    public boolean isVazio() {
        return totalAnotacoes <= 0 || linhas.isEmpty();
    }

    public static PacienteCadernoResumoSemanalView vazio() {
        return new PacienteCadernoResumoSemanalView("", List.of(), 0);
    }
}
