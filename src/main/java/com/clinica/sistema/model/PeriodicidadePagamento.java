package com.clinica.sistema.model;

public enum PeriodicidadePagamento {
    DIARIO("Diário", "Paga na véspera de cada consulta (D-1)"),
    SEMANAL("Semanal", "Semana liberada até domingo; paga sábado ou domingo"),
    MENSAL("Mensal", "Mês liberado para agendar; paga até dia 10 do mês seguinte");

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
