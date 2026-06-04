package com.clinica.sistema.model;

public enum PeriodicidadePagamento {
    DIARIO("Diário", "Paga na véspera de cada consulta (D-1)."),
    SEMANAL("Semanal", "Semana liberada até domingo; paga sábado ou domingo."),
    MENSAL("Mensal", "Mês liberado para agendar; paga do dia 01 ao 10 do mesmo mês.");

    private final String rotulo;
    private final String descricao;

    PeriodicidadePagamento(String rotulo, String descricao) {
        this.rotulo = rotulo;
        this.descricao = descricao;
    }

    public String getRotulo() {
        return rotulo;
    }

    public String getDescricao() {
        return descricao;
    }
}
