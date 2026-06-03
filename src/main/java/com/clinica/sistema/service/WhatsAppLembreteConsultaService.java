package com.clinica.sistema.service;

import com.clinica.sistema.config.WhatsAppMetaProperties;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.repository.AgendamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lembrete WhatsApp na vespera da consulta (D-1), mesma logica de janela do pagamento diario.
 */
@Service
public class WhatsAppLembreteConsultaService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppLembreteConsultaService.class);

    private final WhatsAppMetaProperties properties;
    private final WhatsAppNotificacaoService notificacaoService;
    private final AgendamentoRepository agendamentoRepository;

    public WhatsAppLembreteConsultaService(
            WhatsAppMetaProperties properties,
            WhatsAppNotificacaoService notificacaoService,
            AgendamentoRepository agendamentoRepository
    ) {
        this.properties = properties;
        this.notificacaoService = notificacaoService;
        this.agendamentoRepository = agendamentoRepository;
    }

    public boolean lembreteAutomaticoAtivo() {
        return properties.lembreteVesperaPronto();
    }

    @Transactional
    public int processarLembretesVesperaConsulta() {
        if (!lembreteAutomaticoAtivo()) {
            return 0;
        }
        LocalDate amanha = LocalDate.now().plusDays(1);
        LocalDateTime inicioDia = amanha.atStartOfDay();
        LocalDateTime fimDia = amanha.plusDays(1).atStartOfDay();
        List<Agendamento> pendentes = agendamentoRepository.findPendentesLembreteWhatsappVespera(
                inicioDia,
                fimDia,
                PagamentoStatus.LIBERADO_FALTA_PAGAMENTO
        );
        int enviados = 0;
        for (Agendamento agendamento : pendentes) {
            if (enviarLembreteUnico(agendamento)) {
                enviados++;
            }
        }
        if (enviados > 0) {
            log.info("WhatsApp Meta: {} lembrete(s) enviado(s) para consultas de {}", enviados, amanha);
        }
        return enviados;
    }

    private boolean enviarLembreteUnico(Agendamento agendamento) {
        if (agendamento == null || agendamento.getTelefoneCliente() == null) {
            return false;
        }
        boolean ok = notificacaoService.tentarEnviarLembreteAgendamento(agendamento, agendamento.getTelefoneCliente());
        if (ok) {
            agendamento.setWhatsappLembreteEnviadoEm(LocalDateTime.now());
            agendamentoRepository.save(agendamento);
        }
        return ok;
    }
}
