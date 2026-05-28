package com.clinica.sistema.dto;

/**
 * Alerta no sino da agenda: relatorio do mes passado pronto a partir do dia de fechamento.
 */
public class RelatorioMensalNotificacaoView {

    private final String mesLabel;
    private final String mensagemResumo;
    private final String mensagemPainel;
    private final boolean pendenteArquivamento;
    private final String urlRelatorio;

    public RelatorioMensalNotificacaoView(
            String mesLabel,
            String mensagemResumo,
            String mensagemPainel,
            boolean pendenteArquivamento,
            String urlRelatorio
    ) {
        this.mesLabel = mesLabel;
        this.mensagemResumo = mensagemResumo;
        this.mensagemPainel = mensagemPainel;
        this.pendenteArquivamento = pendenteArquivamento;
        this.urlRelatorio = urlRelatorio;
    }

    public String getMesLabel() {
        return mesLabel;
    }

    public String getMensagemResumo() {
        return mensagemResumo;
    }

    public String getMensagemPainel() {
        return mensagemPainel;
    }

    public boolean isPendenteArquivamento() {
        return pendenteArquivamento;
    }

    public String getUrlRelatorio() {
        return urlRelatorio;
    }
}
