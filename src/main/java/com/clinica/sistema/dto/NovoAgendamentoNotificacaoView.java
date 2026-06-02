package com.clinica.sistema.dto;

/**
 * Item do sino: novo agendamento registrado na clínica.
 */
public class NovoAgendamentoNotificacaoView {

    private final long registroId;
    private final String mensagemResumo;
    private final String mensagemDetalhe;
    private final String rotuloDataHora;
    private final String urlAgenda;

    public NovoAgendamentoNotificacaoView(
            long registroId,
            String mensagemResumo,
            String mensagemDetalhe,
            String rotuloDataHora,
            String urlAgenda
    ) {
        this.registroId = registroId;
        this.mensagemResumo = mensagemResumo;
        this.mensagemDetalhe = mensagemDetalhe;
        this.rotuloDataHora = rotuloDataHora;
        this.urlAgenda = urlAgenda;
    }

    public long getRegistroId() {
        return registroId;
    }

    public String getMensagemResumo() {
        return mensagemResumo;
    }

    public String getMensagemDetalhe() {
        return mensagemDetalhe;
    }

    public String getRotuloDataHora() {
        return rotuloDataHora;
    }

    public String getUrlAgenda() {
        return urlAgenda;
    }
}
