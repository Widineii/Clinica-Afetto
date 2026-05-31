package com.clinica.sistema.dto;

import lombok.Getter;

@Getter
public class ContagemGraficoView {

    private final String rotulo;
    private final long quantidade;

    public ContagemGraficoView(String rotulo, long quantidade) {
        this.rotulo = rotulo != null && !rotulo.isBlank() ? rotulo : "—";
        this.quantidade = quantidade;
    }
}
