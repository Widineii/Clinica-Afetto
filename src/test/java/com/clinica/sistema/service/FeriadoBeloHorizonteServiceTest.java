package com.clinica.sistema.service;

import com.clinica.sistema.dto.DiaEspecialAgendaView;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeriadoBeloHorizonteServiceTest {

    private final FeriadoBeloHorizonteService service = new FeriadoBeloHorizonteService();

    @Test
    void corpusChristi2026EhFeriado() {
        DiaEspecialAgendaView especial = service.resolverDiaEspecial(LocalDate.of(2026, 6, 4)).orElseThrow();

        assertEquals("Corpus Christi", especial.nome());
        assertFalse(especial.pontoFacultativo());
    }

    @Test
    void carnaval2026EhPontoFacultativo() {
        DiaEspecialAgendaView segunda = service.resolverDiaEspecial(LocalDate.of(2026, 2, 16)).orElseThrow();
        DiaEspecialAgendaView terca = service.resolverDiaEspecial(LocalDate.of(2026, 2, 17)).orElseThrow();

        assertTrue(segunda.pontoFacultativo());
        assertEquals("Segunda de Carnaval", segunda.nome());
        assertTrue(terca.pontoFacultativo());
        assertEquals("Terça de Carnaval", terca.nome());
    }

    @Test
    void vesperasSaoPontoFacultativo() {
        DiaEspecialAgendaView vesperaNatal = service.resolverDiaEspecial(LocalDate.of(2026, 12, 24)).orElseThrow();
        DiaEspecialAgendaView vesperaAnoNovo = service.resolverDiaEspecial(LocalDate.of(2026, 12, 31)).orElseThrow();

        assertTrue(vesperaNatal.pontoFacultativo());
        assertEquals("Véspera de Natal", vesperaNatal.nome());
        assertTrue(vesperaAnoNovo.pontoFacultativo());
        assertEquals("Véspera de Ano Novo", vesperaAnoNovo.nome());
    }

    @Test
    void retornaSomenteDiasEspeciaisDaSemanaInformada() {
        Map<String, DiaEspecialAgendaView> diasEspeciais = service.resolverDiasEspeciaisDaSemana(List.of(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2),
                LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 4),
                LocalDate.of(2026, 6, 5),
                LocalDate.of(2026, 6, 6)
        ));

        assertEquals(1, diasEspeciais.size());
        assertEquals("Corpus Christi", diasEspeciais.get("2026-06-04").nome());
    }

    @Test
    void incluiFeriadosMunicipaisDeBh() {
        assertEquals(
                "Assunção de Nossa Senhora da Boa Viagem",
                service.nomeFeriado(LocalDate.of(2026, 8, 15)).orElseThrow()
        );
        assertEquals("Imaculada Conceição", service.nomeFeriado(LocalDate.of(2026, 12, 8)).orElseThrow());
        assertEquals(
                "Aniversário de Belo Horizonte",
                service.nomeFeriado(LocalDate.of(2026, 12, 12)).orElseThrow()
        );
    }

    @Test
    void semanaSemDiaEspecialRetornaMapaVazio() {
        Map<String, DiaEspecialAgendaView> diasEspeciais = service.resolverDiasEspeciaisDaSemana(List.of(
                LocalDate.of(2026, 6, 8),
                LocalDate.of(2026, 6, 9),
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 11),
                LocalDate.of(2026, 6, 12),
                LocalDate.of(2026, 6, 13)
        ));

        assertTrue(diasEspeciais.isEmpty());
    }
}
