package com.clinica.sistema.dto;

import java.util.Map;

public record ContratoRascunhoView(
        Map<String, Object> dados,
        boolean salvo,
        String atualizadoEm,
        String atualizadoPorNome,
        long versao,
        boolean contratanteFinalizado,
        String contratanteFinalizadoEm,
        String contratanteFinalizadoPorNome,
        String contratoFinalizadoTipo,
        String contratoFinalizadoTipoRotulo
) {
}
