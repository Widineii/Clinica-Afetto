package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.infinitepay")
public class InfinitePayProperties {

    /**
     * Modo teste gera links locais sem chamar a API da InfinitePay.
     */
    private boolean modoTeste = true;

    /**
     * InfiniteTag sem o prefixo $ (ex.: afetto).
     */
    private String handle = "afetto-teste";

    /**
     * URL base publica do sistema (para webhook e links de checkout de teste).
     */
    private String baseUrl = "http://localhost:8081";

    public boolean isModoTeste() {
        return modoTeste;
    }

    public void setModoTeste(boolean modoTeste) {
        this.modoTeste = modoTeste;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
