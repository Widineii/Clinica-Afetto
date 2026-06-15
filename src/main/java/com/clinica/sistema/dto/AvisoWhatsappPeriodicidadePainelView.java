package com.clinica.sistema.dto;

import com.clinica.sistema.model.PeriodicidadePagamento;

import java.util.List;

public record AvisoWhatsappPeriodicidadePainelView(
        PeriodicidadePagamento periodicidade,
        String titulo,
        String icone,
        String horarioEnvio,
        String fraseExemplo,
        List<ProfissionalPendenciaPagamentoWhatsappView> profissionais
) {
    public int quantidadeComPendencia() {
        return (int) profissionais.stream()
                .filter(linha -> linha.quantidadePendencias() > 0)
                .count();
    }
}
