package com.clinica.sistema.dto;

/**
 * Metadados do historico mensal sem carregar o PDF do banco.
 */
public interface RelatorioHistoricoMetadadosProjection {
    int getAno();

    int getMes();

    String getMesLabel();

    String getDadosJson();
}
