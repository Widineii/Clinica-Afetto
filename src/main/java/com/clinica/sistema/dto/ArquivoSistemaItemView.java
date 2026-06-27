package com.clinica.sistema.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ArquivoSistemaItemView {

    private final String caminho;
    private final String tipo;
    private final long tamanhoBytes;
    private final String tamanhoLabel;
    private final String urlGitHub;
    private final String urlRaw;
}
