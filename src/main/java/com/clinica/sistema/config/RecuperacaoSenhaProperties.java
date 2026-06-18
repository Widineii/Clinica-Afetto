package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.recuperacao-senha")
public class RecuperacaoSenhaProperties {

    private boolean enabled = true;
    /** Em local sem SMTP: imprime o codigo no log em vez de enviar e-mail. */
    private boolean modoConsola = false;
    private int codigoExpiracaoMinutos = 15;
    private int intervaloReenvioMinutos = 2;
    private String remetenteNome = "Agenda Afetto";
    private String remetenteEmail = "";
    /** URL publica do site (logo e links no e-mail HTML). */
    private String urlSite = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isModoConsola() {
        return modoConsola;
    }

    public void setModoConsola(boolean modoConsola) {
        this.modoConsola = modoConsola;
    }

    public int getCodigoExpiracaoMinutos() {
        return codigoExpiracaoMinutos;
    }

    public void setCodigoExpiracaoMinutos(int codigoExpiracaoMinutos) {
        this.codigoExpiracaoMinutos = codigoExpiracaoMinutos;
    }

    public int getIntervaloReenvioMinutos() {
        return intervaloReenvioMinutos;
    }

    public void setIntervaloReenvioMinutos(int intervaloReenvioMinutos) {
        this.intervaloReenvioMinutos = intervaloReenvioMinutos;
    }

    public String getRemetenteNome() {
        return remetenteNome;
    }

    public void setRemetenteNome(String remetenteNome) {
        this.remetenteNome = remetenteNome;
    }

    public String getRemetenteEmail() {
        return remetenteEmail;
    }

    public void setRemetenteEmail(String remetenteEmail) {
        this.remetenteEmail = remetenteEmail;
    }

    public String getUrlSite() {
        return urlSite;
    }

    public void setUrlSite(String urlSite) {
        this.urlSite = urlSite;
    }
}
