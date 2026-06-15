package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WhatsApp Cloud API (Meta). O {@link #phoneNumberId} e o ID que a Meta gera para o
 * numero da clinica ja cadastrado no WhatsApp Manager — nao e outro telefone.
 */
@Component
@ConfigurationProperties(prefix = "app.whatsapp.meta")
public class WhatsAppMetaProperties {

    private boolean enabled = false;
    private String accessToken = "";
    private String phoneNumberId = "";
    private String businessAccountId = "";
    private String apiVersion = "v21.0";
    private String webhookVerifyToken = "";
    private String templateLembreteConsulta = "lembrete_consulta";
    private String templateLanguage = "pt_BR";
    /** Numero da clinica (somente digitos, ex. 5537998550994) para conferencia e logs. */
    private String numeroClinicaReferencia = "";
    /** Envia lembrete automatico na vespera (D-1), alinhado ao pagamento. */
    private boolean lembreteVesperaAtivo = true;
    /** Aviso automatico de pagamento pendente para profissionais (diario/semanal/mensal). */
    private boolean avisoPagamentoAtivo = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getBusinessAccountId() {
        return businessAccountId;
    }

    public void setBusinessAccountId(String businessAccountId) {
        this.businessAccountId = businessAccountId;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getWebhookVerifyToken() {
        return webhookVerifyToken;
    }

    public void setWebhookVerifyToken(String webhookVerifyToken) {
        this.webhookVerifyToken = webhookVerifyToken;
    }

    public String getTemplateLembreteConsulta() {
        return templateLembreteConsulta;
    }

    public void setTemplateLembreteConsulta(String templateLembreteConsulta) {
        this.templateLembreteConsulta = templateLembreteConsulta;
    }

    public String getTemplateLanguage() {
        return templateLanguage;
    }

    public void setTemplateLanguage(String templateLanguage) {
        this.templateLanguage = templateLanguage;
    }

    public String getNumeroClinicaReferencia() {
        return numeroClinicaReferencia;
    }

    public void setNumeroClinicaReferencia(String numeroClinicaReferencia) {
        this.numeroClinicaReferencia = numeroClinicaReferencia;
    }

    public boolean isLembreteVesperaAtivo() {
        return lembreteVesperaAtivo;
    }

    public void setLembreteVesperaAtivo(boolean lembreteVesperaAtivo) {
        this.lembreteVesperaAtivo = lembreteVesperaAtivo;
    }

    public boolean isAvisoPagamentoAtivo() {
        return avisoPagamentoAtivo;
    }

    public void setAvisoPagamentoAtivo(boolean avisoPagamentoAtivo) {
        this.avisoPagamentoAtivo = avisoPagamentoAtivo;
    }

    public boolean lembreteVesperaPronto() {
        return estaProntoParaEnvio() && lembreteVesperaAtivo;
    }

    public boolean avisoPagamentoPronto() {
        return estaProntoParaEnvio() && avisoPagamentoAtivo;
    }

    public String resolverUrlEnvioMensagens() {
        return "https://graph.facebook.com/" + apiVersion + "/" + phoneNumberId + "/messages";
    }

    public boolean estaProntoParaEnvio() {
        return enabled
                && accessToken != null && !accessToken.isBlank()
                && phoneNumberId != null && !phoneNumberId.isBlank();
    }

    public boolean webhookConfigurado() {
        return webhookVerifyToken != null && !webhookVerifyToken.isBlank();
    }
}
