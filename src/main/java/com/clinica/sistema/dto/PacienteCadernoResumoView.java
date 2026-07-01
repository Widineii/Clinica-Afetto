package com.clinica.sistema.dto;

import lombok.Getter;

@Getter
public class PacienteCadernoResumoView {

    private final int sessoesRealizadas;
    private final String totalLiquidoRecebido;
    private final int sessoesCanceladas;

    public PacienteCadernoResumoView(int sessoesRealizadas, String totalLiquidoRecebido, int sessoesCanceladas) {
        this.sessoesRealizadas = Math.max(0, sessoesRealizadas);
        this.totalLiquidoRecebido = totalLiquidoRecebido != null && !totalLiquidoRecebido.isBlank()
                ? totalLiquidoRecebido
                : "R$ 0,00";
        this.sessoesCanceladas = Math.max(0, sessoesCanceladas);
    }

    public static PacienteCadernoResumoView vazio() {
        return new PacienteCadernoResumoView(0, "R$ 0,00", 0);
    }
}
