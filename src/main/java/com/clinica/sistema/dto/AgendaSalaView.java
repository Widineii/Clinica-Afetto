package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Sala;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class AgendaSalaView {
    private Sala sala;
    private LocalDate inicioSemana;
    private List<LocalDate> diasSemana;
    private List<AgendaSalaLinha> linhas;
}
