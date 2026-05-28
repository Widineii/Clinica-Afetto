package com.clinica.sistema.dto;

/**
 * Metadados do historico mensal sem carregar PDF nem JSON no banco.
 */
public record RelatorioHistoricoResumo(int ano, int mes, String mesLabel, boolean temDados) {
}
