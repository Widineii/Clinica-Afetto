package com.clinica.sistema.exception;

public class PagamentoWebhookNaoAutorizadoException extends RuntimeException {

    public PagamentoWebhookNaoAutorizadoException() {
        super("Webhook de pagamento nao autorizado.");
    }
}
