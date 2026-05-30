package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.pagamento")
public class PagamentoProperties {

    private int prazoConfirmacaoMinutos = 8;
    /** Hora limite no dia anterior a consulta (padrao 23:59). */
    private String horaLimitePagamentoVespera = "23:59";

    /**
     * Segredo opcional do header {@code X-Webhook-Secret}. A InfinitePay nao envia esse header;
     * a confirmacao em producao usa a API payment_check da InfinitePay.
     */
    private String webhookSecret = "";

    public int getPrazoConfirmacaoMinutos() {
        return prazoConfirmacaoMinutos;
    }

    public void setPrazoConfirmacaoMinutos(int prazoConfirmacaoMinutos) {
        this.prazoConfirmacaoMinutos = prazoConfirmacaoMinutos;
    }

    public String getHoraLimitePagamentoVespera() {
        return horaLimitePagamentoVespera;
    }

    public void setHoraLimitePagamentoVespera(String horaLimitePagamentoVespera) {
        this.horaLimitePagamentoVespera = horaLimitePagamentoVespera;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}
