package com.clinica.sistema.dto;

import lombok.Getter;

@Getter
public class PacienteCadernoLembreteView {

    private final Long anotacaoId;
    private final String cardId;
    private final String nomePaciente;
    private final String textoResumo;
    private final String lembreteRotulo;

    public PacienteCadernoLembreteView(
            Long anotacaoId,
            String cardId,
            String nomePaciente,
            String textoResumo,
            String lembreteRotulo
    ) {
        this.anotacaoId = anotacaoId;
        this.cardId = cardId != null ? cardId : "";
        this.nomePaciente = nomePaciente != null ? nomePaciente : "—";
        this.textoResumo = textoResumo != null ? textoResumo : "";
        this.lembreteRotulo = lembreteRotulo != null ? lembreteRotulo : "";
    }
}
