package com.clinica.sistema.dto;

import com.clinica.sistema.util.MoedaBrasilUtil;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
public class ReceitaPixFatiaView {

    private final String chave;
    private final String rotulo;
    private final BigDecimal valor;
    private final String valorFormatado;
    private final int quantidade;
    private final double percentual;
    private final String cor;

    public ReceitaPixFatiaView(String chave, String rotulo, BigDecimal valor, int quantidade, BigDecimal total, String cor) {
        this.chave = chave;
        this.rotulo = rotulo;
        this.valor = valor;
        this.valorFormatado = MoedaBrasilUtil.formatar(valor);
        this.quantidade = quantidade;
        this.percentual = calcularPercentual(valor, total);
        this.cor = cor;
    }

    private static double calcularPercentual(BigDecimal valor, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        return valor
                .multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
