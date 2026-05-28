package com.clinica.sistema.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class RelocacaoAgendamentoForm {
    private Long salaId;
    private LocalDate dataAtendimento;
    private LocalTime horarioAtendimento;
}
