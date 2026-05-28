package com.clinica.sistema.dto;

import java.time.LocalDateTime;

/** Cabecalho do relatorio arquivado sem carregar o PDF. */
public interface RelatorioArquivadoCabecalhoProjection {
    LocalDateTime getGeradoEm();

    long getAgendamentosRemovidos();
}
