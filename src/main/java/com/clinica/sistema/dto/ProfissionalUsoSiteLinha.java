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
    /** Login registrado apos implantacao do controle de acesso. */
    public boolean acessoConfirmadoPorLogin() {
        return ultimoAcessoEm != null;
    }

    /** Quem tem agendamento ja usou a agenda, mesmo sem data de login gravada. */
    public boolean entrouSoPorHistoricoAgenda() {
        return ultimoAcessoEm == null && totalAgendamentos > 0;
    }
}
