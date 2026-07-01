package com.clinica.sistema.dto;

public record PendenciasDonaIndicacaoItemView(
        Long agendamentoId,
        String nomeProfissional,
        String nomeCliente,
        String dataHoraFormatada
) {
}
