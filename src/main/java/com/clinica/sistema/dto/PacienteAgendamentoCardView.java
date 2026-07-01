package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.util.WhatsAppNumeroUtil;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Getter
public class PacienteAgendamentoCardView {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public enum TipoCard {
        AVULSO,
        FIXO,
        QUINZENAL,
        MENSAL
    }

    private final String cardId;
    private final String nomeExibicao;
    private final String telefoneRotulo;
    private final String whatsappUrl;
    private final String recorrenciaRotulo;
    private final int totalConsultas;
    private final String proximaConsultaRotulo;
    private final String ultimaConsultaRotulo;
    private final TipoCard tipo;
    private final SerieAgendamentoLinha serie;
    private final MensalAgendamentoLinha mensal;
    private final List<Agendamento> avulsos;
    private final String textoBuscaAnotacoes;
    private final String telefoneBruto;
    private final LocalDateTime proximaConsulta;
    private final LocalDateTime ultimaConsulta;

    private PacienteAgendamentoCardView(
            String cardId,
            String nomeExibicao,
            String telefone,
            String recorrenciaRotulo,
            int totalConsultas,
            LocalDateTime proxima,
            LocalDateTime ultima,
            TipoCard tipo,
            SerieAgendamentoLinha serie,
            MensalAgendamentoLinha mensal,
            List<Agendamento> avulsos,
            String textoBuscaAnotacoes
    ) {
        this.cardId = cardId;
        this.nomeExibicao = nomeExibicao != null ? nomeExibicao.trim() : "—";
        this.telefoneRotulo = formatarTelefoneExibicao(telefone);
        this.whatsappUrl = WhatsAppNumeroUtil.normalizarDestinatario(telefone)
                .map(digitos -> "https://wa.me/" + digitos)
                .orElse(null);
        this.recorrenciaRotulo = recorrenciaRotulo;
        this.totalConsultas = totalConsultas;
        this.proximaConsultaRotulo = formatarDataHora(proxima);
        this.ultimaConsultaRotulo = formatarDataHora(ultima);
        this.tipo = tipo;
        this.serie = serie;
        this.mensal = mensal;
        this.avulsos = avulsos != null ? avulsos : List.of();
        this.textoBuscaAnotacoes = textoBuscaAnotacoes != null ? textoBuscaAnotacoes.trim() : "";
        this.telefoneBruto = telefone;
        this.proximaConsulta = proxima;
        this.ultimaConsulta = ultima;
    }

    public PacienteAgendamentoCardView comTextoBuscaAnotacoes(String texto) {
        return new PacienteAgendamentoCardView(
                cardId,
                nomeExibicao,
                telefoneBruto,
                recorrenciaRotulo,
                totalConsultas,
                proximaConsulta,
                ultimaConsulta,
                tipo,
                serie,
                mensal,
                avulsos,
                texto
        );
    }

    public static PacienteAgendamentoCardView deAvulso(Agendamento agendamento, LocalDateTime agora) {
        LocalDateTime inicio = agendamento.getDataHoraInicio();
        LocalDateTime proxima = inicio != null && inicio.isAfter(agora) ? inicio : null;
        LocalDateTime ultima = inicio != null && !inicio.isAfter(agora) ? inicio : null;
        return new PacienteAgendamentoCardView(
                "av-" + agendamento.getId(),
                agendamento.getNomeCliente(),
                agendamento.getTelefoneCliente(),
                agendamento.getRecorrenciaLabel(),
                1,
                proxima,
                ultima,
                TipoCard.AVULSO,
                null,
                null,
                List.of(agendamento),
                ""
        );
    }

    public static PacienteAgendamentoCardView deSerie(
            SerieAgendamentoLinha serie,
            TipoCard tipo,
            String telefone,
            LocalDateTime proxima,
            LocalDateTime ultima
    ) {
        String recorrencia = tipo == TipoCard.FIXO ? "Semanal" : "Quinzenal";
        return new PacienteAgendamentoCardView(
                "sr-" + serie.getAgendamentoReferenciaId(),
                serie.getRotuloCabecalho(),
                telefone,
                recorrencia,
                serie.getProximasOcorrencias() != null ? serie.getProximasOcorrencias().size() : 0,
                proxima,
                ultima,
                tipo,
                serie,
                null,
                null,
                ""
        );
    }

    public static PacienteAgendamentoCardView deMensal(
            MensalAgendamentoLinha mensal,
            String telefone,
            LocalDateTime proxima,
            LocalDateTime ultima
    ) {
        return new PacienteAgendamentoCardView(
                "mn-" + mensal.getAgendamentoReferenciaId(),
                mensal.getRotuloCabecalho(),
                telefone,
                "Mensal",
                mensal.getDatasHistorico() != null ? mensal.getDatasHistorico().size() : 0,
                proxima,
                ultima,
                TipoCard.MENSAL,
                null,
                mensal,
                null,
                ""
        );
    }

    public String getBuscaTexto() {
        String base = nomeExibicao + " " + telefoneRotulo + " " + recorrenciaRotulo;
        if (textoBuscaAnotacoes.isBlank()) {
            return base;
        }
        return base + " " + textoBuscaAnotacoes;
    }

    public List<SerieAgendamentoLinha> getSerieComoLista() {
        return serie != null ? List.of(serie) : List.of();
    }

    public List<MensalAgendamentoLinha> getMensalComoLista() {
        return mensal != null ? List.of(mensal) : List.of();
    }

    public String getRadioPrefix() {
        return "pac-" + cardId + "-";
    }

    private static String formatarDataHora(LocalDateTime dataHora) {
        return dataHora != null ? DATA_HORA.format(dataHora) : null;
    }

    private static String formatarTelefoneExibicao(String numero) {
        return WhatsAppNumeroUtil.normalizarDestinatario(numero)
                .map(digitos -> {
                    String local = digitos.length() > 2 ? digitos.substring(2) : digitos;
                    if (local.length() == 11) {
                        return String.format(
                                Locale.ROOT,
                                "(%s) %s-%s",
                                local.substring(0, 2),
                                local.substring(2, 7),
                                local.substring(7)
                        );
                    }
                    if (local.length() == 10) {
                        return String.format(
                                Locale.ROOT,
                                "(%s) %s-%s",
                                local.substring(0, 2),
                                local.substring(2, 6),
                                local.substring(6)
                        );
                    }
                    return numero;
                })
                .orElse("—");
    }
}
