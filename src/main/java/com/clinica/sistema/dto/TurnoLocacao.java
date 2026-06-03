package com.clinica.sistema.dto;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Locale;

public enum TurnoLocacao {

    TURNO_MANHA(LocalTime.of(8, 0), LocalTime.of(13, 0), "Manhã (08:00 às 13:00)"),
    TURNO_TARDE(LocalTime.of(13, 0), LocalTime.of(18, 0), "Tarde (13:00 às 18:00)");

    private final LocalTime inicio;
    private final LocalTime fim;
    private final String rotulo;

    TurnoLocacao(LocalTime inicio, LocalTime fim, String rotulo) {
        this.inicio = inicio;
        this.fim = fim;
        this.rotulo = rotulo;
    }

    public LocalTime getInicio() {
        return inicio;
    }

    public LocalTime getFim() {
        return fim;
    }

    public String getRotulo() {
        return rotulo;
    }

    public String getCodigo() {
        return name();
    }

    public static boolean isTurno(String codigo) {
        return fromCodigo(codigo) != null;
    }

    public static TurnoLocacao fromCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        String normalizado = codigo.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(turno -> turno.name().equals(normalizado))
                .findFirst()
                .orElse(null);
    }
}
