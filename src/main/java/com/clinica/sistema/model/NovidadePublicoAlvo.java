package com.clinica.sistema.model;

public enum NovidadePublicoAlvo {
    PROFISSIONAL("Profissionais"),
    DONA_CLINICA("Dona da clínica"),
    ADMIN("Administração");

    private final String rotulo;

    NovidadePublicoAlvo(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
