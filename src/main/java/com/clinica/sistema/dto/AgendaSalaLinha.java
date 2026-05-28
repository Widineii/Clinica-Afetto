package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
@AllArgsConstructor
public class AgendaSalaLinha {
    private LocalTime horario;
    private List<Agendamento> agendamentos;
}
