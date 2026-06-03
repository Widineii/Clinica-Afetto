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
    private List<AgendaGradeCelula> celulas;

    public static AgendaSalaLinha comAgendamentoUnico(LocalTime horario, Agendamento agendamento) {
        AgendaGradeCelula celula = agendamento != null ? AgendaGradeCelula.completa(agendamento) : null;
        return new AgendaSalaLinha(horario, List.of(celula));
    }
}
