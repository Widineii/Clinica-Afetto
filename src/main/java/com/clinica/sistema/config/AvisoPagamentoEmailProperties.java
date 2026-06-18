package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.aviso-pagamento.email")
public class AvisoPagamentoEmailProperties {

    private boolean enabled = true;
    /** Em local sem SMTP: apenas loga o aviso em vez de enviar e-mail. */
    private boolean modoConsola = false;
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

    public String getUrlSite() {
        return urlSite;
    }

    public void setUrlSite(String urlSite) {
        this.urlSite = urlSite;
    }
}
