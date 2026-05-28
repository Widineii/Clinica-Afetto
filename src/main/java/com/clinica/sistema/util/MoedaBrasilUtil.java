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
            throw new IllegalArgumentException("Valor invalido. Use o formato 1500,00.");
        }
    }

    public static String formatar(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        NumberFormat formato = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        return formato.format(valor);
    }
}
