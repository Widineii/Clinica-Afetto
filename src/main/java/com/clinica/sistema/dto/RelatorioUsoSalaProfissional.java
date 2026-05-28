package com.clinica.sistema.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RelatorioUsoSalaProfissional {
    private String profissionalNome;
    private List<RelatorioUsoSalaItem> salas = new ArrayList<>();
    private long totalHorarios;
}
