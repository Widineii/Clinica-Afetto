package com.clinica.sistema.dto;

/**
 * Alerta no sino da agenda: lembrete de pagamento para profissionais.
 */
public class PagamentoProfissionalNotificacaoView {

    private final String mensagemResumo;
    private final String mensagemPainel;
    private final String rotuloData;
    private final String urlMeusPagamentos;

    public PagamentoProfissionalNotificacaoView(
            String mensagemResumo,
            String mensagemPainel,
            String rotuloData,
            String urlMeusPagamentos
    ) {
        this.mensagemResumo = mensagemResumo;
        this.mensagemPainel = mensagemPainel;
        this.rotuloData = rotuloData;
        this.urlMeusPagamentos = urlMeusPagamentos;
    }

    public String getMensagemResumo() {
        return mensagemResumo;
    }

    public String getMensagemPainel() {
        return mensagemPainel;
    }

    public String getRotuloData() {
        return rotuloData;
    }

    public String getUrlMeusPagamentos() {
        return urlMeusPagamentos;
    }
}
