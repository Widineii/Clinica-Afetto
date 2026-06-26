package com.clinica.sistema.service;

import com.clinica.sistema.dto.SuporteContratoView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Contagem regressiva do suporte contratual (90 dias / 3 meses).
 * Acima de 30 dias restantes exibe meses; nos ultimos 30 dias, exibe dias.
 */
public final class SuporteContratoContador {

    static final int DIAS_SUPORTE = 90;
    static final int LIMITE_EXIBICAO_DIAS = 30;

    private SuporteContratoContador() {
    }

    public static SuporteContratoView calcular(LocalDateTime inicioSuporte, LocalDate hoje) {
        if (inicioSuporte == null) {
            return SuporteContratoView.inativo();
        }
        LocalDate inicio = inicioSuporte.toLocalDate();
        LocalDate fim = inicio.plusDays(DIAS_SUPORTE);
        long diasRestantes = ChronoUnit.DAYS.between(hoje, fim);
        if (diasRestantes < 0) {
            return SuporteContratoView.encerrado();
        }
        return new SuporteContratoView(true, false, formatarContador(diasRestantes));
    }

    static String formatarContador(long diasRestantes) {
        if (diasRestantes <= LIMITE_EXIBICAO_DIAS) {
            if (diasRestantes == 1) {
                return "1 dia";
            }
            return diasRestantes + " dias";
        }
        long meses = diasRestantes / LIMITE_EXIBICAO_DIAS;
        if (meses == 1) {
            return "1 mes";
        }
        return meses + " meses";
    }
}
