package com.clinica.sistema.model;

import java.util.Locale;

public enum EvolucaoClinica {
    MELHORIA("Melhoria"),
    ESTAVEL("Estável"),
    REGRESSAO("Regressão");

    private final String rotulo;

    EvolucaoClinica(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    public static EvolucaoClinica parse(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.trim().toUpperCase(Locale.ROOT);
        for (EvolucaoClinica item : values()) {
            if (item.name().equals(normalizado)) {
                return item;
            }
        }
        return null;
    }
}
