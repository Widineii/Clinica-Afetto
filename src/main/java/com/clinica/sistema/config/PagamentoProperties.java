package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.pagamento")
public class PagamentoProperties {

    private int prazoConfirmacaoMinutos = 8;
    /** Prazo do pagamento mensal do mes vigente (dia 1 ate este dia, inclusive). */
    private int mensalDiaLimite = 10;
    /** Hora limite no dia anterior a consulta (padrao 23:59). */
    private String horaLimitePagamentoVespera = "23:59";

    /**
     * Segredo opcional do header {@code X-Webhook-Secret}. A InfinitePay nao envia esse header;
     * a confirmacao em producao usa a API payment_check da InfinitePay.
     */
    private String webhookSecret = "";
    /** Prazo para pagar PIX apos indicao aprovada (dias apos o atendimento, inclusive). */
    private int indicacaoDiasLimitePosAtendimento = 2;

    public int getPrazoConfirmacaoMinutos() {
        return prazoConfirmacaoMinutos;
    }

    public void setPrazoConfirmacaoMinutos(int prazoConfirmacaoMinutos) {
        this.prazoConfirmacaoMinutos = prazoConfirmacaoMinutos;
    }

    public int getMensalDiaLimite() {
        return mensalDiaLimite;
    }

    public void setMensalDiaLimite(int mensalDiaLimite) {
        this.mensalDiaLimite = mensalDiaLimite;
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

    public int getIndicacaoDiasLimitePosAtendimento() {
        return indicacaoDiasLimitePosAtendimento;
    }

    public void setIndicacaoDiasLimitePosAtendimento(int indicacaoDiasLimitePosAtendimento) {
        this.indicacaoDiasLimitePosAtendimento = indicacaoDiasLimitePosAtendimento;
    }
}
