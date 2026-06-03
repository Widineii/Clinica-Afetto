package com.clinica.sistema.dto;

public record ResultadoAtualizacaoValoresConsulta(
        int profissionaisAtualizados,
        int consultasAtualizadas,
        int profissionaisExcluidos
) {
}
