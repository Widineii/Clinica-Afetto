package com.clinica.sistema.dto;

public record PendenciasDonaContaItemView(
        Long usuarioId,
        String nome,
        String login,
        String solicitadoEmFormatado
) {
}
