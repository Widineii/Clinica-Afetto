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

    /**
     * Chave HMAC do cookie remember-me. Em producao defina REMEMBER_ME_KEY no ambiente.
     */
    private String rememberMeKey = "afetto-remember-me-dev-key-trocar-em-producao";

    /** Dias que o usuario permanece logado com "Lembrar meu acesso". */
    private int rememberMeValidadeDias = 14;

    /** Dias para manter o login preenchido na tela de entrada. */
    private int loginSalvoValidadeDias = 365;

    public boolean isExigirTrocaSenhaPrimeiroAcesso() {
        return exigirTrocaSenhaPrimeiroAcesso;
    }

    public void setExigirTrocaSenhaPrimeiroAcesso(boolean exigirTrocaSenhaPrimeiroAcesso) {
        this.exigirTrocaSenhaPrimeiroAcesso = exigirTrocaSenhaPrimeiroAcesso;
    }

    public String getRememberMeKey() {
        return rememberMeKey;
    }

    public void setRememberMeKey(String rememberMeKey) {
        this.rememberMeKey = rememberMeKey;
    }

    public int getRememberMeValidadeDias() {
        return rememberMeValidadeDias;
    }

    public void setRememberMeValidadeDias(int rememberMeValidadeDias) {
        this.rememberMeValidadeDias = rememberMeValidadeDias;
    }

    public int getLoginSalvoValidadeDias() {
        return loginSalvoValidadeDias;
    }

    public void setLoginSalvoValidadeDias(int loginSalvoValidadeDias) {
        this.loginSalvoValidadeDias = loginSalvoValidadeDias;
    }

    public int getRememberMeValiditySeconds() {
        return Math.max(1, rememberMeValidadeDias) * 24 * 60 * 60;
    }

    public int getLoginSalvoValiditySeconds() {
        return Math.max(1, loginSalvoValidadeDias) * 24 * 60 * 60;
    }
}
