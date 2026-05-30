package com.clinica.sistema.dto;

/**
 * Alerta no sino da agenda: alguém encerrou uma série semanal ou quinzenal.
 */
public class EncerramentoSerieNotificacaoView {

    private final long quantidadePendentes;
    private final String mensagemResumo;
    private final String mensagemPainel;
    private final String urlEncerramentos;

    public EncerramentoSerieNotificacaoView(
            long quantidadePendentes,
            String mensagemResumo,
            String mensagemPainel,
            String urlEncerramentos
    ) {
        this.quantidadePendentes = quantidadePendentes;
        this.mensagemResumo = mensagemResumo;
        this.mensagemPainel = mensagemPainel;
        this.urlEncerramentos = urlEncerramentos;
    }

    public long getQuantidadePendentes() {
        return quantidadePendentes;
    }

    public String getMensagemResumo() {
        return mensagemResumo;
    }

    public String getMensagemPainel() {
        return mensagemPainel;
    }

    public String getUrlEncerramentos() {
        return urlEncerramentos;
    }
}
