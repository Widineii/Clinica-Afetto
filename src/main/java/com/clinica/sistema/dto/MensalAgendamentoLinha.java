package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import lombok.Getter;

import java.util.List;

@Getter
public class MensalAgendamentoLinha {

    private final Agendamento agendamentoReferencia;
    private final List<SerieAgendamentoOcorrencia> datasHistorico;
    private final String valoresConsultaResumo;
    private final String valorProfissionalRecebeInput;

    public MensalAgendamentoLinha(
            Agendamento agendamentoReferencia,
            List<SerieAgendamentoOcorrencia> datasHistorico,
            String valoresConsultaResumo
    ) {
        this(agendamentoReferencia, datasHistorico, valoresConsultaResumo, null);
    }

    public MensalAgendamentoLinha(
            Agendamento agendamentoReferencia,
            List<SerieAgendamentoOcorrencia> datasHistorico,
            String valoresConsultaResumo,
            String valorProfissionalRecebeInput
    ) {
        this.agendamentoReferencia = agendamentoReferencia;
        this.datasHistorico = datasHistorico != null ? datasHistorico : List.of();
        this.valoresConsultaResumo = valoresConsultaResumo;
        this.valorProfissionalRecebeInput = valorProfissionalRecebeInput;
    }

    public Long getAgendamentoReferenciaId() {
        return agendamentoReferencia != null ? agendamentoReferencia.getId() : null;
    }

    public String getRotuloCabecalho() {
        if (agendamentoReferencia == null) {
            return "-";
        }
        String cliente = agendamentoReferencia.getNomeCliente() != null && !agendamentoReferencia.getNomeCliente().isBlank()
                ? agendamentoReferencia.getNomeCliente()
                : "-";
        String sala = agendamentoReferencia.getSala() != null && agendamentoReferencia.getSala().getNome() != null
                ? agendamentoReferencia.getSala().getNome()
                : "-";
        String horario = agendamentoReferencia.getDataHoraInicio() != null
                ? agendamentoReferencia.getDataHoraInicio().toLocalTime().toString().substring(0, 5)
                : "";
        return cliente + " - " + sala + (horario.isBlank() ? "" : " - " + horario);
    }
}
