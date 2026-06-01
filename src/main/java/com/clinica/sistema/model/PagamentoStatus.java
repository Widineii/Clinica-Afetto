package com.clinica.sistema.model;

public enum PagamentoStatus {
    PAGAMENTO_FUTURO,
    /** Link/QR ativo; prazo curto (5 min) para confirmar ou o agendamento e removido. */
    ESPERANDO_CONFIRMACAO,
    /** Legado (fluxo dinheiro removido); migrado para PIX na subida do servidor. */
    AGUARDANDO_CONFIRMACAO_DINHEIRO,
    /** Indicacao da dona: reserva feita, aguarda Polyana aprovar antes do PIX. */
    AGUARDANDO_APROVACAO_INDICACAO,
    /** Dentro da janela de pagamento (1 dia antes), ainda sem QR aberto. */
    AGUARDANDO_PAGAMENTO,
    /**
     * Nao pagou ate 23:59 do dia anterior; vaga liberada na grade.
     * Titular ainda pode pagar se ninguem reservou o horario.
     */
    LIBERADO_FALTA_PAGAMENTO,
    PAGO;

    public static PagamentoStatus fromString(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return PagamentoStatus.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
