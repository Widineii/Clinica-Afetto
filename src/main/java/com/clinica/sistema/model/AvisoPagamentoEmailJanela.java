package com.clinica.sistema.model;

/**
 * Janela de envio do aviso de pagamento pendente por e-mail.
 */
public enum AvisoPagamentoEmailJanela {

    DIARIO_MANHA(
            PeriodicidadePagamento.DIARIO,
            "Lembrete de pagamento — manhã",
            """
                    Você tem taxa(s) de sala pendente(s) no total de {total}.
                    Quite hoje em Meus pagamentos para manter a agenda liberada.
                    Se não regularizar, o sistema pode bloquear novos agendamentos."""
    ),
    DIARIO_TARDE(
            PeriodicidadePagamento.DIARIO,
            "Lembrete de pagamento — fim do dia",
            """
                    Ainda consta pendência de {total} em taxas de sala.
                    Este é o segundo aviso do dia: acesse Meus pagamentos e quite até o fim do dia
                    para evitar bloqueio da agenda."""
    ),
    SEMANAL_SABADO_TARDE(
            PeriodicidadePagamento.SEMANAL,
            "Pagamento semanal — sábado",
            """
                    Você tem pendência de {total} na taxa de sala desta semana.
                    Pode quitar hoje (sábado) ou amanhã (domingo), último dia do prazo semanal.
                    Acesse Meus pagamentos na Agenda Afetto."""
    ),
    SEMANAL_DOMINGO_MANHA(
            PeriodicidadePagamento.SEMANAL,
            "Pagamento semanal — domingo (manhã)",
            """
                    Lembrete: ainda há pendência de {total} na taxa de sala da semana.
                    Hoje (domingo) é o último dia para pagar e evitar bloqueio na segunda-feira."""
    ),
    SEMANAL_DOMINGO_TARDE(
            PeriodicidadePagamento.SEMANAL,
            "Pagamento semanal — último aviso",
            """
                    Último aviso do domingo: pendência de {total}.
                    Se não quitar até o fim do dia, a agenda bloqueia na segunda-feira.
                    Acesse Meus pagamentos agora."""
    ),
    MENSAL_DIA1(
            PeriodicidadePagamento.MENSAL,
            "Pagamento mensal — início do mês",
            """
                    Há pendência de {total} referente ao mês vigente.
                    Você pode pagar do dia 1 ao dia 10 em Meus pagamentos."""
    ),
    MENSAL_DIA5(
            PeriodicidadePagamento.MENSAL,
            "Pagamento mensal — dia 5",
            """
                    Lembrete: pendência de {total} no mês vigente.
                    O prazo para quitar vai até o dia 10. Acesse Meus pagamentos."""
    ),
    MENSAL_DIA10_MANHA(
            PeriodicidadePagamento.MENSAL,
            "Pagamento mensal — último dia (manhã)",
            """
                    Hoje é o último dia (10) para pagar {total} do mês vigente.
                    Quite em Meus pagamentos para evitar bloqueio da agenda."""
    ),
    MENSAL_DIA10_TARDE(
            PeriodicidadePagamento.MENSAL,
            "Pagamento mensal — último aviso",
            """
                    Último aviso: pendência de {total}.
                    Se não quitar até o fim do dia 10, a agenda será bloqueada.
                    Acesse Meus pagamentos agora."""
    );

    private final PeriodicidadePagamento periodicidade;
    private final String tituloAssunto;
    private final String corpoTemplate;

    AvisoPagamentoEmailJanela(
            PeriodicidadePagamento periodicidade,
            String tituloAssunto,
            String corpoTemplate
    ) {
        this.periodicidade = periodicidade;
        this.tituloAssunto = tituloAssunto;
        this.corpoTemplate = corpoTemplate;
    }

    public PeriodicidadePagamento getPeriodicidade() {
        return periodicidade;
    }

    public String getTituloAssunto() {
        return tituloAssunto;
    }

    public String montarCorpo(String nome, String total) {
        String nomeAplicado = nome == null || nome.isBlank() ? "Profissional" : nome.trim();
        String totalAplicado = total == null || total.isBlank() ? "R$ 0,00" : total.trim();
        return ("Olá " + nomeAplicado + ",\n\n"
                + corpoTemplate.replace("{total}", totalAplicado)
                + "\n\nClínica Afetto — Agenda Afetto")
                .replaceAll("(?m)^\\s+", "");
    }
}
