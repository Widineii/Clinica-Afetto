package com.clinica.sistema.dto;

public record ProfissionalBloqueioPagamentoEmailView(
        Long profissionalId,
        String nome,
        String email,
        String mensagemBloqueio,
        String valorTotalFormatado,
        int quantidadePendencias
) {
}
