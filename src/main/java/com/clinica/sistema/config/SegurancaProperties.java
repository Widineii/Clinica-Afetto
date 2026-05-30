package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.seguranca")
public class SegurancaProperties {

    /**
     * Exige troca de senha no primeiro acesso (usuarios novos ou senha redefinida pela gestao).
     */
    private boolean exigirTrocaSenhaPrimeiroAcesso = true;

    public boolean isExigirTrocaSenhaPrimeiroAcesso() {
        return exigirTrocaSenhaPrimeiroAcesso;
    }

    public void setExigirTrocaSenhaPrimeiroAcesso(boolean exigirTrocaSenhaPrimeiroAcesso) {
        this.exigirTrocaSenhaPrimeiroAcesso = exigirTrocaSenhaPrimeiroAcesso;
    }
}
