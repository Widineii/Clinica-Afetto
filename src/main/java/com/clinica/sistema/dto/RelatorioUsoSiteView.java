package com.clinica.sistema.dto;

import java.util.List;

public record RelatorioUsoSiteView(
        int totalProfissionais,
        int totalJaAcessaram,
        int totalNuncaAcessaram,
        int totalJaAgendaram,
        int totalNaoAgendaram,
        List<ProfissionalUsoSiteLinha> profissionais
) {
}
