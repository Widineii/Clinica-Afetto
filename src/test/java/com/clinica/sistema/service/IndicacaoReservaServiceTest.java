package com.clinica.sistema.service;

import com.clinica.sistema.config.PagamentoProperties;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndicacaoReservaServiceTest {

    @Mock
    private AgendamentoRepository repository;

    @Mock
    private AuthService authService;

    @Mock
    private PagamentoProperties pagamentoProperties;

    @Mock
    private ValorConsultaService valorConsultaService;

    @InjectMocks
    private IndicacaoReservaService indicacaoReservaService;

    private Agendamento agendamento;
    private Usuario polyana;

    @BeforeEach
    void setUp() {
        polyana = new Usuario();
        polyana.setId(1L);
        polyana.setDonaClinica(true);

        agendamento = new Agendamento();
        agendamento.setId(10L);
        agendamento.setIndicacaoDona(true);
        agendamento.setNomeCliente("Cliente indicação");
        agendamento.setValorClinicaCobra(new BigDecimal("60.00"));
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0));
    }

    @Test
    void deveAprovarIndicacaoEAguardarPix() {
        when(repository.findById(10L)).thenReturn(Optional.of(agendamento));
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento resultado = indicacaoReservaService.aprovarReservaIndicacao(10L, polyana);

        assertEquals(PagamentoStatus.AGUARDANDO_PAGAMENTO, resultado.getStatusPagamento());
        assertTrue(resultado.isIndicacaoAprovadaPelaDona());
        verify(repository).save(agendamento);
    }

    @Test
    void janelaPixIndicacaoComecaNoHorarioDaConsulta() {
        when(pagamentoProperties.getIndicacaoDiasLimitePosAtendimento()).thenReturn(2);
        agendamento.setIndicacaoAprovadaEm(LocalDateTime.now());
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        agendamento.setDataHoraInicio(LocalDateTime.now().minusHours(1));

        assertTrue(indicacaoReservaService.dentroJanelaPagamentoIndicacao(agendamento));
    }
}
