package com.clinica.sistema.dto;

import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.ValorConsultaService;
import com.clinica.sistema.util.MoedaBrasilUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.BiFunction;

public record ProfissionalValoresConsultaLinhaView(
        Long id,
        String nome,
        String login,
        BigDecimal valorAvulso,
        BigDecimal valorSemanal,
        BigDecimal valorQuinzenal,
        BigDecimal valorMensal,
        BigDecimal percentualTaxaIndicacaoExibicao,
        BigDecimal percentualTaxaIndicacaoSalvo
) {
    private static final NumberFormat MOEDA_BR = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public static ProfissionalValoresConsultaLinhaView from(Usuario usuario) {
        return from(usuario, (profissionalId, recorrencia) -> null);
    }

    public static ProfissionalValoresConsultaLinhaView from(
            Usuario usuario,
            BiFunction<Long, String, BigDecimal> ultimoValorAgendamento
    ) {
        Long profissionalId = usuario.getId();
        return new ProfissionalValoresConsultaLinhaView(
                profissionalId,
                usuario.getNome(),
                usuario.getLogin(),
                resolverValor(usuario.getValorConsultaAvulso(), ultimoValorAgendamento.apply(profissionalId, "AVULSO"), "AVULSO"),
                resolverValor(usuario.getValorConsultaSemanal(), ultimoValorAgendamento.apply(profissionalId, "SEMANAL"), "SEMANAL"),
                resolverValor(usuario.getValorConsultaQuinzenal(), ultimoValorAgendamento.apply(profissionalId, "QUINZENAL"), "QUINZENAL"),
                resolverValor(usuario.getValorConsultaMensal(), ultimoValorAgendamento.apply(profissionalId, "MENSAL"), "MENSAL"),
                percentualIndicacaoParaExibicao(usuario.getPercentualTaxaIndicacao()),
                percentualIndicacaoSalvo(usuario.getPercentualTaxaIndicacao())
        );
    }

    private static BigDecimal percentualIndicacaoSalvo(BigDecimal salvo) {
        if (positivo(salvo)) {
            return salvo.setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    private static BigDecimal percentualIndicacaoParaExibicao(BigDecimal salvo) {
        if (positivo(salvo)) {
            return salvo.setScale(2, RoundingMode.HALF_UP);
        }
        return ValorConsultaService.percentualTaxaIndicacaoPadrao();
    }

    private static BigDecimal resolverValor(BigDecimal salvo, BigDecimal historico, String recorrencia) {
        if (positivo(salvo)) {
            return salvo.setScale(2, RoundingMode.HALF_UP);
        }
        return ValorConsultaService.taxaSalaPadraoSistema(recorrencia);
    }

    private static boolean positivo(BigDecimal valor) {
        return valor != null && valor.signum() > 0;
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

    public String valorAvulsoInput() {
        return formatarInput(valorAvulso);
    }

    public String valorSemanalInput() {
        return formatarInput(valorSemanal);
    }

    public String valorQuinzenalInput() {
        return formatarInput(valorQuinzenal);
    }

    public String valorMensalInput() {
        return formatarInput(valorMensal);
    }

    public String percentualIndicacaoResumo() {
        if (!positivo(percentualTaxaIndicacaoExibicao)) {
            return "—";
        }
        return percentualTaxaIndicacaoExibicao.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }

    public String percentualIndicacaoInput() {
        if (!positivo(percentualTaxaIndicacaoSalvo)) {
            return "";
        }
        return MoedaBrasilUtil.formatarDecimal(percentualTaxaIndicacaoSalvo);
    }

    private static String formatarInput(BigDecimal valor) {
        return MoedaBrasilUtil.formatarDecimal(valor);
    }

    private static String formatar(BigDecimal valor) {
        if (!positivo(valor)) {
            return "—";
        }
        return MOEDA_BR.format(valor);
    }
}
