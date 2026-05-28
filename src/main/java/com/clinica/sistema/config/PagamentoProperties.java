package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.pagamento")
public class PagamentoProperties {

    private int prazoConfirmacaoMinutos = 5;
    /** Hora limite no dia anterior a consulta (padrao 23:59). */
    private String horaLimitePagamentoVespera = "23:59";

    /**
     * Segredo do header {@code X-Webhook-Secret} para confirmar pagamentos via InfinitePay.
     * Em producao deve ser definido; em local pode ficar vazio apenas com modo teste ativo.
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
