package com.clinica.sistema.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class SerieAgendamentoLinha {

    private final String nomeCliente;
    private final String salaNome;
    private final Long agendamentoReferenciaId;
    private final String tipoRecorrencia;
    private final String diaSemanaRotulo;
    private final List<SerieAgendamentoOcorrencia> proximasOcorrencias;
    private final String valoresConsultaResumo;

    public SerieAgendamentoLinha(
            String nomeCliente,
            String salaNome,
            Long agendamentoReferenciaId,
            String tipoRecorrencia,
            String diaSemanaRotulo,
            List<SerieAgendamentoOcorrencia> proximasOcorrencias
    ) {
        this(nomeCliente, salaNome, agendamentoReferenciaId, tipoRecorrencia, diaSemanaRotulo, proximasOcorrencias, null);
    }

    public SerieAgendamentoLinha(
            String nomeCliente,
            String salaNome,
            Long agendamentoReferenciaId,
            String tipoRecorrencia,
            String diaSemanaRotulo,
            List<SerieAgendamentoOcorrencia> proximasOcorrencias,
            String valoresConsultaResumo
    ) {
        this.nomeCliente = nomeCliente;
        this.salaNome = salaNome;
        this.agendamentoReferenciaId = agendamentoReferenciaId;
        this.tipoRecorrencia = tipoRecorrencia;
        this.diaSemanaRotulo = diaSemanaRotulo;
        this.proximasOcorrencias = proximasOcorrencias;
        this.valoresConsultaResumo = valoresConsultaResumo;
    }

    public String getRotuloCabecalho() {
        String cliente = nomeCliente != null && !nomeCliente.isBlank() ? nomeCliente : "-";
        String base = cliente + " - " + salaNome;
        if (diaSemanaRotulo == null || diaSemanaRotulo.isBlank()) {
            return base;
        }
        if ("QUINZENAL".equalsIgnoreCase(tipoRecorrencia)) {
            return base + " - Quinzenal (" + diaSemanaRotulo + ")";
        }
        return base + " - " + diaSemanaRotulo;
    }
}
