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
    private final String commitShaCurto;
    private final String commitMensagem;
    private final String commitDataLabel;
    private final int totalArquivos;
    private final int totalPastas;
    private final boolean arvoreTruncada;
    private final List<ArquivoSistemaItemView> arquivos;
}
