package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@AllArgsConstructor
public class AgendaGradeCelula {

    private Agendamento agendamento;
    private boolean blocoMultiHora;
    private boolean primeiraCelulaDoBloco;
    private boolean ultimaCelulaDoBloco;

    public boolean isOcupada() {
        return agendamento != null;
    }

    public boolean isExibirDetalhesCompletos() {
        return !blocoMultiHora || ultimaCelulaDoBloco;
    }

    public static AgendaGradeCelula resolver(Agendamento agendamento, LocalDate dia, LocalTime horarioCelula) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return null;
        }
        LocalDateTime inicioCelula = LocalDateTime.of(dia, horarioCelula);
        LocalDateTime fimCelula = inicioCelula.plusHours(1);
        LocalDateTime inicioAg = inicioHoraCheia(agendamento.getDataHoraInicio());
        LocalDateTime fimAg = agendamento.getDataHoraFim() != null
                ? agendamento.getDataHoraFim()
                : inicioAg.plusHours(1);

        if (!intervalosSobrepoem(inicioAg, fimAg, inicioCelula, fimCelula)) {
            return null;
        }

        boolean blocoMultiHora = Duration.between(inicioAg, fimAg).toMinutes() > 60
                || agendamento.isLocacaoTurno();
        boolean primeira = inicioCelula.equals(inicioAg);
        boolean ultima = !fimAg.isAfter(fimCelula);

        return new AgendaGradeCelula(agendamento, blocoMultiHora, primeira, ultima);
    }

    public static AgendaGradeCelula completa(Agendamento agendamento) {
        return new AgendaGradeCelula(agendamento, false, true, true);
    }

    private static LocalDateTime inicioHoraCheia(LocalDateTime dataHora) {
        return dataHora.withMinute(0).withSecond(0).withNano(0);
    }

    private static boolean intervalosSobrepoem(
            LocalDateTime inicioA,
            LocalDateTime fimA,
            LocalDateTime inicioB,
            LocalDateTime fimB
    ) {
        return inicioA.isBefore(fimB) && fimA.isAfter(inicioB);
    }
}
