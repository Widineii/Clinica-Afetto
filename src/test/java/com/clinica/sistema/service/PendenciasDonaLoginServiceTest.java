package com.clinica.sistema.service;

import com.clinica.sistema.dto.PendenciasDonaLoginView;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.security.ClinicaAuthenticationSuccessHandler;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendenciasDonaLoginServiceTest {

    @Mock
    private AuthService authService;

    @Mock
    private IndicacaoReservaService indicacaoReservaService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private HttpSession session;

    private PendenciasDonaLoginService service;

    @BeforeEach
    void setUp() {
        service = new PendenciasDonaLoginService(authService, indicacaoReservaService, usuarioRepository);
    }

    @Test
    void marcarPendenciasDonaNoLoginDeveGravarSessaoQuandoHaPendencias() {
        Usuario polyana = donaClinica();
        when(authService.podeGerenciarEquipe(polyana)).thenReturn(true);
        when(indicacaoReservaService.contarAguardandoAprovacao()).thenReturn(1);
        when(usuarioRepository.findByContaAprovadaFalseOrderByCadastroSolicitadoEmAsc()).thenReturn(List.of());

        service.marcarPendenciasDonaNoLogin(session, polyana);

        verify(session).setAttribute(
                ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_PENDENCIAS_DONA,
                Boolean.TRUE
        );
    }

    @Test
    void montarDeveListarIndicacaoEContaComNomes() {
        Usuario polyana = donaClinica();
        when(authService.podeGerenciarEquipe(polyana)).thenReturn(true);

        Usuario carol = new Usuario();
        carol.setNome("Carol Silva");
        Agendamento indicacao = new Agendamento();
        indicacao.setId(10L);
        indicacao.setNomeCliente("João");
        indicacao.setProfissional(carol);
        indicacao.setDataHoraInicio(LocalDateTime.of(2026, 6, 2, 10, 0));
        when(indicacaoReservaService.listarAguardandoAprovacao()).thenReturn(List.of(indicacao));

        Usuario contaNova = new Usuario();
        contaNova.setId(20L);
        contaNova.setNome("Ana Souza");
        contaNova.setLogin("ana");
        contaNova.setCadastroSolicitadoEm(LocalDateTime.of(2026, 6, 1, 8, 30));
        when(usuarioRepository.findByContaAprovadaFalseOrderByCadastroSolicitadoEmAsc())
                .thenReturn(List.of(contaNova));

        PendenciasDonaLoginView view = service.montar(polyana);

        assertEquals("Polyana", view.primeiroNome());
        assertEquals(1, view.totalIndicacoes());
        assertEquals(1, view.totalContas());
        assertEquals("Carol Silva", view.indicacoes().get(0).nomeProfissional());
        assertEquals("Ana Souza", view.contas().get(0).nome());
    }

    @Test
    void exibirModalPendenciasDonaEntradaRetornaFalseSemFlagNaSessao() {
        Usuario polyana = donaClinica();
        when(authService.podeGerenciarEquipe(polyana)).thenReturn(true);
        when(session.getAttribute(ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_PENDENCIAS_DONA))
                .thenReturn(null);

        assertFalse(service.exibirModalPendenciasDonaEntrada(session, polyana));
    }

    @Test
    void exibirModalPendenciasDonaEntradaRetornaTrueComPendencias() {
        Usuario polyana = donaClinica();
        when(authService.podeGerenciarEquipe(polyana)).thenReturn(true);
        when(session.getAttribute(ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_PENDENCIAS_DONA))
                .thenReturn(Boolean.TRUE);
        when(indicacaoReservaService.contarAguardandoAprovacao()).thenReturn(1);
        when(usuarioRepository.findByContaAprovadaFalseOrderByCadastroSolicitadoEmAsc()).thenReturn(List.of());

        assertTrue(service.exibirModalPendenciasDonaEntrada(session, polyana));
    }

    private Usuario donaClinica() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNome("Polyana Afetto");
        usuario.setDonaClinica(true);
        return usuario;
    }
}
