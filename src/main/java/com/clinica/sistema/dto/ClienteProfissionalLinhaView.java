package com.clinica.sistema.dto;

import com.clinica.sistema.util.WhatsAppNumeroUtil;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Getter
public class ClienteProfissionalLinhaView {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String nome;
    private final String nomeChave;
    private final String telefoneRotulo;
    private final String whatsappUrl;
    private final int totalConsultas;
    private final String recorrenciaRotulo;
    private final LocalDateTime ultimaConsulta;
    private final String ultimaConsultaRotulo;
    private final LocalDateTime proximaConsulta;
    private final String proximaConsultaRotulo;

    public ClienteProfissionalLinhaView(
            String nome,
            String telefone,
            int totalConsultas,
            String recorrenciaRotulo,
            LocalDateTime ultimaConsulta,
            LocalDateTime proximaConsulta
    ) {
        this.nome = nome != null ? nome.trim() : "—";
        this.nomeChave = normalizarChave(this.nome);
        this.telefoneRotulo = formatarTelefoneExibicao(telefone);
        this.whatsappUrl = WhatsAppNumeroUtil.normalizarDestinatario(telefone)
                .map(digitos -> "https://wa.me/" + digitos)
                .orElse(null);
        this.totalConsultas = totalConsultas;
        this.recorrenciaRotulo = recorrenciaRotulo != null ? recorrenciaRotulo : "—";
        this.ultimaConsulta = ultimaConsulta;
        this.ultimaConsultaRotulo = formatarDataHora(ultimaConsulta);
        this.proximaConsulta = proximaConsulta;
        this.proximaConsultaRotulo = formatarDataHora(proximaConsulta);
    }

    public static ClienteProfissionalLinhaView agregar(List<AgendamentoClienteAgrupado> agendamentos, LocalDateTime agora) {
        AgendamentoClienteAgrupado referencia = agendamentos.stream()
                .max(Comparator.comparing(AgendamentoClienteAgrupado::dataHoraInicio, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow();
        String telefone = agendamentos.stream()
                .map(AgendamentoClienteAgrupado::telefoneCliente)
                .filter(Objects::nonNull)
                .filter(t -> !t.isBlank())
                .reduce((primeiro, segundo) -> segundo)
                .orElse(referencia.telefoneCliente());
        LocalDateTime ultima = agendamentos.stream()
                .map(AgendamentoClienteAgrupado::dataHoraInicio)
                .filter(Objects::nonNull)
                .filter(data -> !data.isAfter(agora))
                .max(Comparator.naturalOrder())
                .orElse(null);
        LocalDateTime proxima = agendamentos.stream()
                .map(AgendamentoClienteAgrupado::dataHoraInicio)
                .filter(Objects::nonNull)
                .filter(data -> data.isAfter(agora))
                .min(Comparator.naturalOrder())
                .orElse(null);
        String recorrencia = resolverRecorrenciaPrincipal(agendamentos, proxima, ultima);
        return new ClienteProfissionalLinhaView(
                referencia.nomeCliente(),
                telefone,
                agendamentos.size(),
                recorrencia,
                ultima,
                proxima
        );
    }

    private static String resolverRecorrenciaPrincipal(
            List<AgendamentoClienteAgrupado> agendamentos,
            LocalDateTime proxima,
            LocalDateTime ultima
    ) {
        LocalDateTime referencia = proxima != null ? proxima : ultima;
        if (referencia != null) {
            return agendamentos.stream()
                    .filter(item -> referencia.equals(item.dataHoraInicio()))
                    .map(AgendamentoClienteAgrupado::recorrenciaRotulo)
                    .findFirst()
                    .orElse("—");
        }
        if (agendamentos.stream().anyMatch(AgendamentoClienteAgrupado::fixoSemanal)) {
            return "Fixo";
        }
        if (agendamentos.stream().anyMatch(AgendamentoClienteAgrupado::mensal)) {
            return "Mensal";
        }
        if (agendamentos.stream().anyMatch(AgendamentoClienteAgrupado::quinzenal)) {
            return "Quinzenal";
        }
        return "Avulso";
    }

    private static String formatarDataHora(LocalDateTime dataHora) {
        return dataHora != null ? DATA_HORA.format(dataHora) : "—";
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

    private static String normalizarChave(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.trim().toLowerCase(Locale.ROOT);
    }

    public record AgendamentoClienteAgrupado(
            String nomeCliente,
            String telefoneCliente,
            LocalDateTime dataHoraInicio,
            String recorrenciaRotulo,
            boolean fixoSemanal,
            boolean mensal,
            boolean quinzenal
    ) {
    }
}
