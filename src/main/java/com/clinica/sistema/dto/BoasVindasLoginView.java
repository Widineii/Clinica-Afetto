package com.clinica.sistema.dto;

import java.util.List;

public record BoasVindasLoginView(
        String saudacao,
        String primeiroNome,
        String dataHojeFormatada,
        boolean atendimentosDeAmanha,
        boolean primeiroLogin,
        boolean apenasApresentacao,
        List<AtendimentoSalaHojeView> salasComAtendimentos,
        int totalAtendimentos
) {
    public boolean semAtendimentos() {
        return totalAtendimentos <= 0;
    }
}
