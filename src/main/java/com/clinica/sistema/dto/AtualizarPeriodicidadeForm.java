package com.clinica.sistema.dto;

import com.clinica.sistema.model.PeriodicidadePagamento;
import lombok.Data;

@Data
public class AtualizarPeriodicidadeForm {
    private Long usuarioId;
    private PeriodicidadePagamento periodicidade;
}
