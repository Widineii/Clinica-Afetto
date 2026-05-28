package com.clinica.sistema.dto;

import com.clinica.sistema.model.TipoDespesa;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DespesaForm {

    private TipoDespesa tipo = TipoDespesa.MENSAL;
    private String descricao;
    private String valor;
    private LocalDate data;
    private Integer mes;
    private Integer ano;
}
