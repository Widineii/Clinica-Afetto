package com.clinica.sistema.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AtualizarPercentualIndicacaoProfissionalForm {
    private Long usuarioId;
    private BigDecimal percentualTaxaIndicacao;
}
