package com.clinica.sistema.service;

import com.clinica.sistema.config.WhatsAppMetaProperties;
import com.clinica.sistema.exception.WhatsAppMetaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppNotificacaoServiceTest {

    @Mock
    private WhatsAppMetaApiClient apiClient;

    private WhatsAppMetaProperties properties;
    private WhatsAppNotificacaoService service;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppMetaProperties();
        properties.setEnabled(true);
        properties.setAccessToken("token-teste");
        properties.setPhoneNumberId("123456789");
        properties.setTemplateLembreteConsulta("lembrete_consulta");
        properties.setNumeroClinicaReferencia("5537998550994");
        service = new WhatsAppNotificacaoService(properties, apiClient);
    }

    @Test
    void deveEstarAtivoQuandoConfigurado() {
        assertTrue(service.ativo());
        assertTrue(service.numeroClinicaReferencia().isPresent());
    }

    @Test
    void deveEnviarTemplateComParametros() {
        when(apiClient.enviarTemplate(
                eq("5531999887766"),
                eq("lembrete_consulta"),
                eq("pt_BR"),
                eq(List.of("Maria", "10/06/2026", "14:00"))
        )).thenReturn(Map.of("messages", List.of(Map.of("id", "wamid.x"))));

        service.enviarLembreteConsulta(
                "31999887766",
                "Maria",
                LocalDate.of(2026, 6, 10),
                LocalTime.of(14, 0)
        );

        verify(apiClient).enviarTemplate(
                eq("5531999887766"),
                eq("lembrete_consulta"),
                eq("pt_BR"),
                eq(List.of("Maria", "10/06/2026", "14:00"))
        );
    }

    @Test
    void deveFalharQuandoDesligado() {
        properties.setEnabled(false);
        assertFalse(service.ativo());
        assertThrows(WhatsAppMetaException.class, () -> service.enviarLembreteConsulta(
                "31999887766",
                "Maria",
                LocalDate.of(2026, 6, 10),
                LocalTime.of(14, 0)
        ));
    }
}
