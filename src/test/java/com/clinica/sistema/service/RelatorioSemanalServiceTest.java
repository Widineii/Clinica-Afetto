package com.clinica.sistema.service;

import com.clinica.sistema.dto.PeriodoSemanal;
import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelatorioSemanalServiceTest {

    private final RelatorioSemanalService service = new RelatorioSemanalService(
            mock(AgendamentoService.class),
            mock(RelatorioMensalPdfService.class)
    );

    @Test
    void quintaFeiraContaSegundaAteHoje() {
        LocalDate quinta = LocalDate.of(2026, 5, 28);
        PeriodoSemanal periodo = service.calcularPeriodoSemanaAtual(quinta);
        assertEquals(LocalDate.of(2026, 5, 25), periodo.inicio());
        assertEquals(LocalDate.of(2026, 5, 28), periodo.fim());
    }

    @Test
    void domingoContaSegundaASabadoDaSemanaAnterior() {
        LocalDate domingo = LocalDate.of(2026, 5, 31);
        PeriodoSemanal periodo = service.calcularPeriodoSemanaAtual(domingo);
        assertEquals(LocalDate.of(2026, 5, 25), periodo.inicio());
        assertEquals(LocalDate.of(2026, 5, 30), periodo.fim());
    }

    @Test
    void sabadoContaSegundaASabado() {
        LocalDate sabado = LocalDate.of(2026, 5, 30);
        PeriodoSemanal periodo = service.calcularPeriodoSemanaAtual(sabado);
        assertEquals(LocalDate.of(2026, 5, 25), periodo.inicio());
        assertEquals(LocalDate.of(2026, 5, 30), periodo.fim());
    }

    @Test
    void montarRelatorioSemanalUsaRegra24Horas() {
        AgendamentoService agendamentoService = mock(AgendamentoService.class);
        RelatorioSemanalService semanal = new RelatorioSemanalService(
                agendamentoService,
                mock(RelatorioMensalPdfService.class)
        );
        when(agendamentoService.montarRelatorioUsoSalasNoPeriodoAposRegra24h(any(), any(), anyString()))
                .thenReturn(new RelatorioMensalUsoSalasView());

        semanal.montarRelatorioSemanalAtual();

        verify(agendamentoService).montarRelatorioUsoSalasNoPeriodoAposRegra24h(any(), any(), anyString());
    }
}
