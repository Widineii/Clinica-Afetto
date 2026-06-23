package com.clinica.sistema.dto;

import java.util.List;

public record AtendimentoSalaHojeView(
        String sala,
        List<AtendimentoClienteHojeView> clientes
) {
}
