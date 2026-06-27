package com.clinica.sistema.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ArquivoSistemaBreadcrumbView {

    private final String nome;
    private final String caminho;
}
