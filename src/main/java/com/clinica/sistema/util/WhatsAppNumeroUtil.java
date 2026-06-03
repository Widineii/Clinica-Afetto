package com.clinica.sistema.util;

import java.util.Optional;

/**
 * Normaliza telefones brasileiros para envio na API da Meta (somente digitos, DDI 55).
 */
public final class WhatsAppNumeroUtil {

    private static final String DDI_BRASIL = "55";

    private WhatsAppNumeroUtil() {
    }

    public static Optional<String> normalizarDestinatario(String numeroBruto) {
        if (numeroBruto == null || numeroBruto.isBlank()) {
            return Optional.empty();
        }
        String digitos = numeroBruto.replaceAll("\\D", "");
        if (digitos.length() < 10) {
            return Optional.empty();
        }
        if (digitos.startsWith(DDI_BRASIL) && digitos.length() >= 12) {
            return Optional.of(digitos);
        }
        if (digitos.length() == 10 || digitos.length() == 11) {
            return Optional.of(DDI_BRASIL + digitos);
        }
        return Optional.empty();
    }
}
