package com.clinica.sistema.service;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.security.ClinicaAuthenticationSuccessHandler;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoasVindasLoginServiceTest {

    private static final ZoneId FUSO_CLINICA = ZoneId.of("America/Sao_Paulo");

    @Mock
    private AuthService authService;

    @Mock
    private AgendamentoService agendamentoService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private HttpSession session;

    @InjectMocks
    private BoasVindasLoginService service;

    @Test
    void podeExibirBoasVindasHoje_primeiroLogin_sempreExibe() {
        Usuario usuario = usuarioComControleHoje();
        usuario.setBoasVindasPrimeiroLoginConcluido(false);
        usuario.setBoasVindasOcultoHoje(true);

        assertTrue(service.podeExibirBoasVindasHoje(usuario));
    }

    @Test
    void exigeFormaPagamentoPrimeiroAcesso_falseQuandoApenasApresentacao() {
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasPrimeiroLoginConcluido(false);
        profissional.setBoasVindasApenasApresentacao(true);
        when(authService.podeEscolherFormaPagamento(profissional)).thenReturn(true);

        assertFalse(service.exigeFormaPagamentoPrimeiroAcesso(profissional));
    }

    @Test
    void registrarFechamentoBoasVindas_limpaApenasApresentacao() {
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasPrimeiroLoginConcluido(false);
        profissional.setBoasVindasApenasApresentacao(true);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(profissional));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registrarFechamentoBoasVindas(profissional, false);

        assertTrue(profissional.getBoasVindasPrimeiroLoginConcluido());
        assertFalse(Boolean.TRUE.equals(profissional.getBoasVindasApenasApresentacao()));
        assertTrue(Boolean.TRUE.equals(profissional.getBoasVindasApresentacaoExibida()));
    }

    @Test
    void registrarFechamentoBoasVindas_marcaPrimeiroLoginConcluido() {
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasPrimeiroLoginConcluido(false);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(profissional));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registrarFechamentoBoasVindas(profissional, false);

        assertTrue(profissional.getBoasVindasPrimeiroLoginConcluido());
    }

    @Test
    void marcarBoasVindasPendenteNoLogin_marcaNoPrimeiroLogin() {
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasPrimeiroLoginConcluido(false);
        profissional.setBoasVindasControleData(LocalDate.now(FUSO_CLINICA));
        when(authService.podeAcessarMeusPacientes(profissional)).thenReturn(true);
        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(profissional));

        service.marcarBoasVindasPendenteNoLogin(session, profissional);

        verify(session).setAttribute(ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_BOAS_VINDAS, Boolean.TRUE);
    }

    @Test
    void podeExibirBoasVindasHoje_quandoOcultoDia_bloqueiaAntesDas21h() {
        assumeFalse(service.emPeriodoNoite());
        assumeTrue(service.emPeriodoDia());
        Usuario usuario = usuarioComControleHoje();
        usuario.setBoasVindasOcultoHoje(true);

        assertFalse(service.podeExibirBoasVindasHoje(usuario));
    }

    @Test
    void podeExibirBoasVindasHoje_quandoOcultoDia_aNoiteAindaPodeExibirAmanha() {
        assumeTrue(service.emPeriodoNoite());
        Usuario usuario = usuarioComControleHoje();
        usuario.setBoasVindasOcultoHoje(true);
        usuario.setBoasVindasExibicoesNoite(0);
        usuario.setBoasVindasOcultoNoite(false);

        assertTrue(service.podeExibirBoasVindasHoje(usuario));
    }

    @Test
    void podeExibirBoasVindasHoje_ateQuatroVezesNoPeriodoDia() {
        assumeFalse(service.emPeriodoNoite());
        assumeTrue(service.emPeriodoDia());
        Usuario usuario = usuarioComControleHoje();
        usuario.setBoasVindasExibicoesHoje(3);

        assertTrue(service.podeExibirBoasVindasHoje(usuario));

        usuario.setBoasVindasExibicoesHoje(4);
        assertFalse(service.podeExibirBoasVindasHoje(usuario));
    }

    @Test
    void podeExibirBoasVindasHoje_ateDuasVezesNoPeriodoNoite() {
        assumeTrue(service.emPeriodoNoite());
        Usuario usuario = usuarioComControleHoje();
        usuario.setBoasVindasExibicoesNoite(1);

        assertTrue(service.podeExibirBoasVindasHoje(usuario));

        usuario.setBoasVindasExibicoesNoite(2);
        assertFalse(service.podeExibirBoasVindasHoje(usuario));
    }

    @Test
    void podeExibirBoasVindasHoje_foraDoPeriodoAtivo_naoExibe() {
        assumeFalse(service.emPeriodoAtivo());
        Usuario usuario = usuarioComControleHoje();

        assertFalse(service.podeExibirBoasVindasHoje(usuario));
    }

    @Test
    void marcarBoasVindasPendenteNoLogin_marcaQuandoPodeExibir() {
        assumeTrue(service.emPeriodoAtivo());
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasControleData(LocalDate.now(FUSO_CLINICA));
        profissional.setBoasVindasExibicoesHoje(0);
        profissional.setBoasVindasOcultoHoje(false);
        profissional.setBoasVindasPrimeiroLoginConcluido(true);

        when(authService.podeAcessarMeusPacientes(profissional)).thenReturn(true);
        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(profissional));

        service.marcarBoasVindasPendenteNoLogin(session, profissional);

        verify(session).setAttribute(ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_BOAS_VINDAS, Boolean.TRUE);
    }

    @Test
    void marcarBoasVindasPendenteNoLogin_naoMarcaQuandoOcultoNoPeriodoAtual() {
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasControleData(LocalDate.now(FUSO_CLINICA));
        profissional.setBoasVindasPrimeiroLoginConcluido(true);
        if (service.emPeriodoNoite()) {
            profissional.setBoasVindasOcultoNoite(true);
        } else {
            profissional.setBoasVindasOcultoHoje(true);
        }

        when(authService.podeAcessarMeusPacientes(profissional)).thenReturn(true);
        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(profissional));

        service.marcarBoasVindasPendenteNoLogin(session, profissional);

        verify(session, never()).setAttribute(
                ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_BOAS_VINDAS,
                Boolean.TRUE
        );
    }

    @Test
    void registrarFechamentoBoasVindas_incrementaExibicoesDoPeriodoDia() {
        assumeFalse(service.emPeriodoNoite());
        assumeTrue(service.emPeriodoDia());
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasControleData(LocalDate.now(FUSO_CLINICA));
        profissional.setBoasVindasExibicoesHoje(1);
        profissional.setBoasVindasPrimeiroLoginConcluido(true);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(profissional));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registrarFechamentoBoasVindas(profissional, false);

        assertEquals(2, profissional.getBoasVindasExibicoesHoje());
        assertFalse(profissional.getBoasVindasOcultoHoje());
    }

    @Test
    void registrarFechamentoBoasVindas_ocultaSomentePeriodoDia() {
        assumeFalse(service.emPeriodoNoite());
        assumeTrue(service.emPeriodoDia());
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasControleData(LocalDate.now(FUSO_CLINICA));
        profissional.setBoasVindasPrimeiroLoginConcluido(true);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(profissional));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registrarFechamentoBoasVindas(profissional, true);

        assertTrue(profissional.getBoasVindasOcultoHoje());
        assertFalse(Boolean.TRUE.equals(profissional.getBoasVindasOcultoNoite()));
    }

    @Test
    void montar_agrupaAtendimentosPorSala() {
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasPrimeiroLoginConcluido(true);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(profissional));
        when(agendamentoService.listarAgendamentosDoDia(eq(profissional), eq(false), any(LocalDate.class)))
                .thenReturn(List.of(
                        agendamento(1L, "Sala 1", "Teste 1", 10, 0),
                        agendamento(1L, "Sala 1", "Teste 2", 11, 0),
                        agendamento(2L, "Sala 2", "Maria", 14, 0)
                ));

        var view = service.montar(profissional);

        assertEquals(3, view.totalAtendimentos());
        assertEquals(2, view.salasComAtendimentos().size());
        assertEquals("Sala 1", view.salasComAtendimentos().get(0).sala());
        assertEquals(2, view.salasComAtendimentos().get(0).clientes().size());
    }

    private static Usuario usuarioComControleHoje() {
        Usuario usuario = new Usuario();
        usuario.setBoasVindasControleData(LocalDate.now(FUSO_CLINICA));
        usuario.setBoasVindasExibicoesHoje(0);
        usuario.setBoasVindasOcultoHoje(false);
        usuario.setBoasVindasPrimeiroLoginConcluido(true);
        return usuario;
    }

    private static Agendamento agendamento(Long salaId, String salaNome, String cliente, int hora, int minuto) {
        Sala sala = new Sala();
        sala.setId(salaId);
        sala.setNome(salaNome);
        Agendamento agendamento = new Agendamento();
        agendamento.setSala(sala);
        agendamento.setNomeCliente(cliente);
        agendamento.setDataHoraInicio(LocalDateTime.of(2026, 6, 23, hora, minuto));
        agendamento.setDataHoraFim(agendamento.getDataHoraInicio().plusHours(1));
        return agendamento;
    }

    private static Usuario profissionalElegivel() {
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setNome("Maria Silva");
        usuario.setCargo("ROLE_PROFISSIONAL");
        return usuario;
    }
}
