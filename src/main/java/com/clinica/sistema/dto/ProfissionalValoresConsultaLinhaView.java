package com.clinica.sistema.dto;

import com.clinica.sistema.model.Usuario;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public record ProfissionalValoresConsultaLinhaView(
        Long id,
        String nome,
        String login,
        BigDecimal valorAvulso,
        BigDecimal valorSemanal,
        BigDecimal valorQuinzenal,
        BigDecimal valorMensal
) {
    private static final NumberFormat MOEDA_BR = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public static ProfissionalValoresConsultaLinhaView from(Usuario usuario) {
        return new ProfissionalValoresConsultaLinhaView(
                usuario.getId(),
                usuario.getNome(),
                usuario.getLogin(),
                usuario.getValorConsultaAvulso(),
                usuario.getValorConsultaSemanal(),
                usuario.getValorConsultaQuinzenal(),
                usuario.getValorConsultaMensal()
        );
    }

    public String valorAvulsoFormatado() {
        return formatar(valorAvulso);
    }

    public String valorSemanalFormatado() {
        return formatar(valorSemanal);
    }

    public String valorQuinzenalFormatado() {
        return formatar(valorQuinzenal);
    }

    public String valorMensalFormatado() {
        return formatar(valorMensal);
    }

    private static String formatar(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        return MOEDA_BR.format(valor);
    }
}
