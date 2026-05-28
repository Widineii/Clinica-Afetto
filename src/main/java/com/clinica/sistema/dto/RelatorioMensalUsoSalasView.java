package com.clinica.sistema.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RelatorioMensalUsoSalasView {
    private int anoReferencia;
    private int mesReferencia;
    private String mesReferenciaLabel;
    private String tituloRelatorio;
    private boolean relatorioSemanal;
    private List<RelatorioUsoSalaProfissional> profissionais = new ArrayList<>();
    private long totalGeral;
}
