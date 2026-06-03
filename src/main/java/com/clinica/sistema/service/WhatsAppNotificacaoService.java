package com.clinica.sistema.service;

import com.clinica.sistema.config.WhatsAppMetaProperties;
import com.clinica.sistema.exception.WhatsAppMetaException;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.util.WhatsAppNumeroUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Envio de mensagens pelo numero oficial da clinica na Cloud API da Meta.
 */
@Service
public class WhatsAppNotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificacaoService.class);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter HORA_BR = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR"));

    private final WhatsAppMetaProperties properties;
    private final WhatsAppMetaApiClient apiClient;

    public WhatsAppNotificacaoService(WhatsAppMetaProperties properties, WhatsAppMetaApiClient apiClient) {
        this.properties = properties;
        this.apiClient = apiClient;
    }

    public boolean ativo() {
        return properties.estaProntoParaEnvio();
    }

    public Optional<String> numeroClinicaReferencia() {
        return WhatsAppNumeroUtil.normalizarDestinatario(properties.getNumeroClinicaReferencia());
    }

    public Map<String, Object> enviarLembreteConsulta(
            String telefoneDestino,
            String nomeCliente,
            LocalDate dataConsulta,
            LocalTime horaConsulta
    ) {
        validarParametrosLembrete(telefoneDestino, nomeCliente, dataConsulta, horaConsulta);
        String destino = WhatsAppNumeroUtil.normalizarDestinatario(telefoneDestino)
                .orElseThrow(() -> new WhatsAppMetaException("Telefone do destinatario invalido."));
        String nome = nomeCliente.trim();
        String data = dataConsulta.format(DATA_BR);
        String hora = horaConsulta.format(HORA_BR);
        return apiClient.enviarTemplate(
                destino,
                properties.getTemplateLembreteConsulta(),
                properties.getTemplateLanguage(),
                List.of(nome, data, hora)
        );
    }

    /**
     * Envia lembrete se {@code telefoneDestino} estiver preenchido e a API estiver ativa.
     */
    public boolean tentarEnviarLembreteAgendamento(Agendamento agendamento, String telefoneDestino) {
        if (!ativo() || agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        if (telefoneDestino == null || telefoneDestino.isBlank()) {
            log.debug("WhatsApp: sem telefone do cliente para agendamento {}", agendamento.getId());
            return false;
        }
        try {
            enviarLembreteConsulta(
                    telefoneDestino,
                    agendamento.getNomeCliente(),
                    agendamento.getDataHoraInicio().toLocalDate(),
                    agendamento.getDataHoraInicio().toLocalTime()
            );
            return true;
        } catch (WhatsAppMetaException ex) {
            log.warn("WhatsApp: falha no lembrete do agendamento {}: {}", agendamento.getId(), ex.getMessage());
            return false;
        }
    }

    private void validarParametrosLembrete(
            String telefoneDestino,
            String nomeCliente,
            LocalDate dataConsulta,
            LocalTime horaConsulta
    ) {
        if (!ativo()) {
            throw new WhatsAppMetaException("Integracao WhatsApp Meta desligada ou incompleta.");
        }
        if (telefoneDestino == null || telefoneDestino.isBlank()) {
            throw new WhatsAppMetaException("Informe o telefone do destinatario.");
        }
        if (nomeCliente == null || nomeCliente.isBlank()) {
            throw new WhatsAppMetaException("Informe o nome do cliente para o template.");
        }
        if (dataConsulta == null || horaConsulta == null) {
            throw new WhatsAppMetaException("Data e horario da consulta sao obrigatorios.");
        }
    }
}
