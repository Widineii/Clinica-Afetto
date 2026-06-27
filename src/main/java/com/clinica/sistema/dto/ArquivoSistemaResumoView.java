package com.clinica.sistema.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ArquivoSistemaResumoView {

    private final String repositorioUrl;
    private final String downloadZipUrl;
    private final String branch;
    private final String caminhoAtual;
    private final boolean raiz;
    private final String caminhoPai;
    private final List<ArquivoSistemaBreadcrumbView> breadcrumb;
    private final String commitShaCurto;
    private final String commitMensagem;
    private final String commitAutor;
    private final String commitRelativo;
    private final int totalCommits;
    private final int totalPastas;
    private final int totalArquivos;
    private final String urlPastaAtualGitHub;
    private final List<ArquivoSistemaItemView> itens;
}
