package com.clinica.sistema.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class AgendamentoForm {
    private Long profissionalId;
    private Long salaId;
    private String nomeCliente;
    private LocalDate dataAtendimento;
    private LocalTime horarioAtendimento;
    private boolean fixo;
    private String recorrencia = "AVULSO";
    private BigDecimal valorProfissionalRecebe;
    private BigDecimal valorClinicaCobra;
    private boolean indicacaoDona;
}
