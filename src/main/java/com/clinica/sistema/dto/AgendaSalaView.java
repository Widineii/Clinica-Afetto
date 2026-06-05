package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.dto.DiaEspecialAgendaView;
import com.clinica.sistema.model.Sala;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class AgendaSalaView {
    private Sala sala;
    private LocalDate inicioSemana;
    private List<LocalDate> diasSemana;
    private Map<String, DiaEspecialAgendaView> diasEspeciaisPorDia;
    private List<AgendaSalaLinha> linhas;

    public DiaEspecialAgendaView buscarDiaEspecial(LocalDate dia) {
        if (diasEspeciaisPorDia == null || dia == null) {
            return null;
        }
        return diasEspeciaisPorDia.get(dia.toString());
    }

    public LocalDate buscarDiaSemana(int indice) {
        if (diasSemana == null || indice < 0 || indice >= diasSemana.size()) {
            return null;
        }
        return diasSemana.get(indice);
    }
}
