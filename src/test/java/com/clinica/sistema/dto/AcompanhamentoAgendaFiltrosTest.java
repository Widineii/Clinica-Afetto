package com.clinica.sistema.dto;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcompanhamentoAgendaFiltrosTest {

    @Test
    void resolverIntervalo_proximos3Dias_incluiHojeMaisDois() {
        LocalDate hoje = LocalDate.of(2026, 6, 4);
        var intervalo = AcompanhamentoAgendaFiltros.resolverIntervalo(
                AcompanhamentoAgendaFiltros.Periodo.PROXIMOS_3_DIAS,
                hoje
        );
        assertEquals(hoje, intervalo.inicio());
        assertEquals(hoje.plusDays(2), intervalo.fim());
    }

    @Test
    void filtroProfissional_aceitaIdOuTodos() {
        assertTrue(AcompanhamentoAgendaFiltros.FiltroProfissional.fromParam("todos").todos());
        assertEquals(7L, AcompanhamentoAgendaFiltros.FiltroProfissional.fromParam("7").profissionalId());
        assertEquals("7", AcompanhamentoAgendaFiltros.FiltroProfissional.fromParam("7").parametroUrl());
    }

    @Test
    void resolverIntervalo_semana_usaSegundaADomingoDaReferencia() {
        LocalDate quarta = LocalDate.of(2026, 6, 4);
        LocalDate segunda = quarta.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var intervalo = AcompanhamentoAgendaFiltros.resolverIntervalo(
                AcompanhamentoAgendaFiltros.Periodo.SEMANA,
                quarta
        );
        assertEquals(segunda, intervalo.inicio());
        assertEquals(segunda.plusDays(6), intervalo.fim());
    }
}
