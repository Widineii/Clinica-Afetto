package com.clinica.sistema.service;

import com.clinica.sistema.config.WhatsAppMetaProperties;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.repository.AgendamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppLembreteConsultaServiceTest {

    @Mock
    private WhatsAppNotificacaoService notificacaoService;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    private WhatsAppMetaProperties properties;
    private WhatsAppLembreteConsultaService service;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppMetaProperties();
        properties.setEnabled(true);
        properties.setAccessToken("token");
        properties.setPhoneNumberId("id");
        properties.setLembreteVesperaAtivo(true);
        service = new WhatsAppLembreteConsultaService(properties, notificacaoService, agendamentoRepository);
    }

    @Test
    void naoProcessaQuandoApiDesligada() {
        properties.setEnabled(false);
        assertEquals(0, service.processarLembretesVesperaConsulta());
        verify(agendamentoRepository, never()).findPendentesLembreteWhatsappVespera(any(), any(), any());
    }

    @Test
    void processaConsultasDeAmanha() {
        LocalDate amanha = LocalDate.now().plusDays(1);
        Agendamento agendamento = new Agendamento();
        agendamento.setId(10L);
        agendamento.setNomeCliente("Ana");
        agendamento.setTelefoneCliente("5531999887766");
        agendamento.setDataHoraInicio(amanha.atTime(14, 0));

        when(agendamentoRepository.findPendentesLembreteWhatsappVespera(
                eq(amanha.atStartOfDay()),
                eq(amanha.plusDays(1).atStartOfDay()),
                eq(PagamentoStatus.LIBERADO_FALTA_PAGAMENTO)
        )).thenReturn(List.of(agendamento));
        when(notificacaoService.tentarEnviarLembreteAgendamento(agendamento, "5531999887766")).thenReturn(true);

        int enviados = service.processarLembretesVesperaConsulta();

        assertEquals(1, enviados);
        ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(captor.capture());
        assertNotNull(captor.getValue().getWhatsappLembreteEnviadoEm());
    }

    @Test
    void lembreteAutomaticoDependeDeConfigCompleta() {
        assertTrue(service.lembreteAutomaticoAtivo());
        properties.setPhoneNumberId("");
        assertFalse(service.lembreteAutomaticoAtivo());
    }
}
