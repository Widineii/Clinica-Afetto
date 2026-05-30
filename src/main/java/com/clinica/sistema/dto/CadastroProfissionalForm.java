package com.clinica.sistema.dto;

import com.clinica.sistema.model.PeriodicidadePagamento;
import lombok.Data;

@Data
public class CadastroProfissionalForm {
    private String nome;
    private String login;
    private String senha;
    private PeriodicidadePagamento periodicidade = PeriodicidadePagamento.DIARIO;
}
