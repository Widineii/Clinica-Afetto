package com.clinica.sistema.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AtualizarValoresConsultaProfissionalForm {
    private Long usuarioId;
    private BigDecimal valorAvulso;
    private BigDecimal valorSemanal;
    private BigDecimal valorQuinzenal;
    private BigDecimal valorMensal;
}
