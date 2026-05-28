package com.clinica.sistema.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Resumo de uso do banco para acompanhamento do limite Neon (armazenamento).
 * Classe com getters para compatibilidade total com Thymeleaf.
 */
@Getter
@AllArgsConstructor
public class UsoBancoView {

    private final long totalAgendamentos;
    private final long agendamentosAvulsos;
    private final long agendamentosFixosOuQuinzenais;
    private final long agendamentosJaEncerrados;
    private final long agendamentosMesAtual;
    private final long agendamentosHoje;
    private final long totalUsuarios;
    private final long totalProfissionais;
    private final long totalSalas;
    private final long relatoriosMensaisArquivados;
    private final long relatoriosComPdfLegado;
    private final String bytesJsonLabel;
    private final String bytesPdfLegadoLabel;
    private final Long bytesBancoReal;
    private final String tamanhoBancoRealLabel;
    private final String tamanhoEstimadoLabel;
    private final int limiteNeonMb;
    private final String percentualLabel;
    private final int barraPercentual;
    private final String nivelAlerta;
    private final boolean postgresComTamanhoReal;
}
