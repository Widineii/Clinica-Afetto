package com.clinica.sistema.dto;

import com.clinica.sistema.model.PeriodicidadePagamento;

/**
 * Profissional com taxa de sala em aberto, para aviso via WhatsApp na Central.
 */
public record ProfissionalPendenciaPagamentoWhatsappView(
        Long profissionalId,
        String nome,
        String login,
        String telefoneWhatsapp,
        String email,
        int quantidadePendencias,
        String valorTotalFormatado,
        String mensagemPreview,
        String urlWhatsapp,
        PeriodicidadePagamento periodicidade,
        String periodicidadeRotulo,
        boolean bloqueado,
        boolean temWhatsapp,
        boolean temEmail
) {
}
