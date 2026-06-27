package com.clinica.sistema.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ArquivoSistemaItemView {

    private final String nome;
    private final String caminho;
    private final boolean diretorio;
    private final String tamanhoLabel;
    private final String commitMensagem;
    private final String commitRelativo;
    private final String urlGitHub;
}
