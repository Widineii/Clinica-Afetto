package com.clinica.sistema.dto;

import java.time.LocalDateTime;

public record ProfissionalUsoSiteLinha(
        Long id,
        String nome,
        String login,
        boolean donaClinica,
        LocalDateTime ultimoAcessoEm,
        long totalAgendamentos,
        boolean jaAcessouSite,
        boolean jaAgendou
) {
}
