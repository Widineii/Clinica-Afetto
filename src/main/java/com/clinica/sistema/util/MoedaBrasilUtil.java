package com.clinica.sistema.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class MoedaBrasilUtil {

    private MoedaBrasilUtil() {
    }

    public static BigDecimal parse(String valorTexto) {
        if (valorTexto == null || valorTexto.isBlank()) {
            throw new IllegalArgumentException("Informe o valor em reais.");
        }
        String texto = valorTexto.trim();
        if (texto.matches(".*,\\d{1,2}$")) {
            texto = texto.replace(".", "").replace(",", ".");
        } else {
            texto = texto.replace(",", ".");
        }
        try {
            BigDecimal valor = new BigDecimal(texto);
            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("O valor deve ser maior que zero.");
            }
            return valor.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Valor inválido. Use o formato 1500,00.");
        }
    }

    public static String formatar(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        NumberFormat formato = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        return formato.format(valor);
    }

    /** Formato para campo de edicao: 35,00 ou 1.500,00 (sem simbolo R$). */
    public static BigDecimal parsePercentual(String valorTexto) {
        if (valorTexto == null || valorTexto.isBlank()) {
            throw new IllegalArgumentException("Informe o percentual.");
        }
        String texto = valorTexto.trim().replace("%", "").trim();
        BigDecimal percentual = parse(texto);
        if (percentual.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("O percentual não pode ser maior que 100.");
        }
        return percentual.setScale(2, RoundingMode.HALF_UP);
    }

    public static String formatarDecimal(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            return "";
        }
        NumberFormat formato = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        formato.setGroupingUsed(true);
        return formato.format(valor.setScale(2, RoundingMode.HALF_UP));
    }
}
