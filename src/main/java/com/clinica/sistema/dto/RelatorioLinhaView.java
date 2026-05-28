package com.clinica.sistema.dto;

import lombok.Data;

@Data
public class RelatorioLinhaView {
    private String profissionalNome;
    private long totalHorarios;
    private long sala1;
    private long sala2;
    private long sala3;
    private long sala4;

    public long quantidadeSala(int numero) {
        return switch (numero) {
            case 1 -> sala1;
            case 2 -> sala2;
            case 3 -> sala3;
            case 4 -> sala4;
            default -> 0L;
        };
    }

    public long getTotalSalasUsadas() {
        long usadas = 0;
        if (sala1 > 0) usadas++;
        if (sala2 > 0) usadas++;
        if (sala3 > 0) usadas++;
        if (sala4 > 0) usadas++;
        return usadas;
    }

    public String getTotalSalasUsadasLabel() {
        long total = getTotalSalasUsadas();
        return total == 1 ? "1 sala usada" : total + " salas usadas";
    }

    /** Ex.: Julia | sala 1 = 2 | sala 2 = 5 | total 2 salas usadas (salas distintas, nao horarios). */
    public String getLinhaDetalhada() {
        String nome = profissionalNome != null && !profissionalNome.isBlank() ? profissionalNome : "Profissional";
        return nome
                + " | sala 1 = " + sala1
                + " | sala 2 = " + sala2
                + " | sala 3 = " + sala3
                + " | sala 4 = " + sala4
                + " | total " + getTotalSalasUsadasLabel();
    }
}
