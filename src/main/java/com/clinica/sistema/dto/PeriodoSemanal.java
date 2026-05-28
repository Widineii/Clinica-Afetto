package com.clinica.sistema.dto;

import java.time.LocalDate;

public record PeriodoSemanal(LocalDate inicio, LocalDate fim, String label) {
}
