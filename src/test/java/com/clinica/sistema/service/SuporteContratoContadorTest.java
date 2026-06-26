package com.clinica.sistema.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SuporteContratoContadorTest {

    @Test
    void exibeMesesQuandoRestamMaisDeTrintaDias() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        assertEquals("3 meses", SuporteContratoContador.formatarContador(90));
        assertEquals("2 meses", SuporteContratoContador.formatarContador(61));
        assertEquals("2 meses", SuporteContratoContador.formatarContador(60));
        assertEquals("1 mes", SuporteContratoContador.formatarContador(31));
        assertEquals("1 mes", SuporteContratoContador.formatarContador(59));
        var status = SuporteContratoContador.calcular(
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDate.of(2026, 1, 1)
        );
        assertEquals("3 meses", status.contadorTexto());
    }

    @Test
    void exibeDiasNosUltimosTrintaDias() {
        assertEquals("30 dias", SuporteContratoContador.formatarContador(30));
        assertEquals("29 dias", SuporteContratoContador.formatarContador(29));
        assertEquals("1 dia", SuporteContratoContador.formatarContador(1));
    }

    @Test
    void marcaSuporteEncerradoAposNoventaDias() {
        var status = SuporteContratoContador.calcular(
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDate.of(2026, 4, 2)
        );
        assertEquals(true, status.expirado());
        assertEquals(null, status.contadorTexto());
    }
}
