package com.clinica.sistema.dto;

import com.clinica.sistema.model.PagamentoStatus;
import lombok.Getter;

@Getter
public class SerieAgendamentoOcorrencia {

    private final Long agendamentoId;
    private final String dataRotulo;
    private final PagamentoStatus statusPagamento;
    private final boolean exibirBotaoPagar;
    private final boolean pagamentoPago;
    private final boolean podeRealocar;
    private final boolean podeCancelar;
    private final boolean passado;

    public SerieAgendamentoOcorrencia(
            Long agendamentoId,
            String dataRotulo,
            PagamentoStatus statusPagamento,
            boolean exibirBotaoPagar,
            boolean pagamentoPago,
            boolean podeRealocar,
            boolean podeCancelar,
            boolean passado
    ) {
        this.agendamentoId = agendamentoId;
        this.dataRotulo = dataRotulo;
        this.statusPagamento = statusPagamento;
        this.exibirBotaoPagar = exibirBotaoPagar;
        this.pagamentoPago = pagamentoPago;
        this.podeRealocar = podeRealocar;
        this.podeCancelar = podeCancelar;
        this.passado = passado;
    }
}
