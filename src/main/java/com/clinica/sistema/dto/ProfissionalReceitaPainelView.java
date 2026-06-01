package com.clinica.sistema.dto;

import com.clinica.sistema.util.MoedaBrasilUtil;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class ProfissionalReceitaPainelView {

    private final String chave;
    private final String nome;
    private final BigDecimal valorMesAtual;
    private final String valorMesAtualFormatado;
    private final int atendimentosMesAtual;
    private final String melhorMesLabel;
    private final BigDecimal valorMelhorMes;
    private final String valorMelhorMesFormatado;
    private final int atendimentosMelhorMes;
    private final BigDecimal valorAReceberMes;
    private final String valorAReceberMesFormatado;
    private final int quantidadePendentesMes;

    public ProfissionalReceitaPainelView(
            String chave,
            String nome,
            BigDecimal valorMesAtual,
            int atendimentosMesAtual,
            String melhorMesLabel,
            BigDecimal valorMelhorMes,
            int atendimentosMelhorMes
    ) {
        this(chave, nome, valorMesAtual, atendimentosMesAtual, melhorMesLabel, valorMelhorMes,
                atendimentosMelhorMes, BigDecimal.ZERO, 0);
    }

    public ProfissionalReceitaPainelView(
            String chave,
            String nome,
            BigDecimal valorMesAtual,
            int atendimentosMesAtual,
            String melhorMesLabel,
            BigDecimal valorMelhorMes,
            int atendimentosMelhorMes,
            BigDecimal valorAReceberMes,
            int quantidadePendentesMes
    ) {
        this.chave = chave;
        this.nome = nome;
        this.valorMesAtual = valorMesAtual != null ? valorMesAtual : BigDecimal.ZERO;
        this.valorMesAtualFormatado = MoedaBrasilUtil.formatar(this.valorMesAtual);
        this.atendimentosMesAtual = atendimentosMesAtual;
        this.melhorMesLabel = melhorMesLabel != null && !melhorMesLabel.isBlank() ? melhorMesLabel : "—";
        this.valorMelhorMes = valorMelhorMes != null ? valorMelhorMes : BigDecimal.ZERO;
        this.valorMelhorMesFormatado = MoedaBrasilUtil.formatar(this.valorMelhorMes);
        this.atendimentosMelhorMes = atendimentosMelhorMes;
        this.valorAReceberMes = valorAReceberMes != null ? valorAReceberMes : BigDecimal.ZERO;
        this.valorAReceberMesFormatado = MoedaBrasilUtil.formatar(this.valorAReceberMes);
        this.quantidadePendentesMes = Math.max(quantidadePendentesMes, 0);
    }
}
