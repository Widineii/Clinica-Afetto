package com.clinica.sistema.dto;

public record SuporteContratoView(
        boolean ativo,
        boolean expirado,
        String contadorTexto
) {
    public static SuporteContratoView inativo() {
        return new SuporteContratoView(false, false, null);
    }

    public static SuporteContratoView encerrado() {
        return new SuporteContratoView(false, true, null);
    }
}
