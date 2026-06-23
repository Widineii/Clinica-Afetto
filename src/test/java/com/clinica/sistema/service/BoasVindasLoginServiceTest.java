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
    void podeExibirBoasVindasHoje_quandoOculto_retornaFalse() {
        Usuario usuario = usuarioComControleHoje();
        usuario.setBoasVindasOcultoHoje(true);

        assertFalse(service.podeExibirBoasVindasHoje(usuario));
    }

    @Test
    void podeExibirBoasVindasHoje_ateQuatroVezes() {
        Usuario usuario = usuarioComControleHoje();
        usuario.setBoasVindasExibicoesHoje(3);

        assertTrue(service.podeExibirBoasVindasHoje(usuario));

        usuario.setBoasVindasExibicoesHoje(4);
        assertFalse(service.podeExibirBoasVindasHoje(usuario));
    }

    @Test
    void marcarBoasVindasPendenteNoLogin_marcaQuandoPodeExibir() {
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasControleData(LocalDate.now(FUSO_CLINICA));
        profissional.setBoasVindasExibicoesHoje(0);
        profissional.setBoasVindasOcultoHoje(false);

        when(authService.podeAcessarMeusPacientes(profissional)).thenReturn(true);
        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(profissional));

        service.marcarBoasVindasPendenteNoLogin(session, profissional);

        verify(session).setAttribute(ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_BOAS_VINDAS, Boolean.TRUE);
    }

    @Test
    void marcarBoasVindasPendenteNoLogin_naoMarcaQuandoOcultoHoje() {
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasControleData(LocalDate.now(FUSO_CLINICA));
        profissional.setBoasVindasOcultoHoje(true);

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
    void registrarFechamentoBoasVindas_incrementaExibicoes() {
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasControleData(LocalDate.now(FUSO_CLINICA));
        profissional.setBoasVindasExibicoesHoje(1);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(profissional));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registrarFechamentoBoasVindas(profissional, false);

        assertEquals(2, profissional.getBoasVindasExibicoesHoje());
        assertFalse(profissional.getBoasVindasOcultoHoje());
    }

    @Test
    void registrarFechamentoBoasVindas_ocultaRestanteDoDia() {
        Usuario profissional = profissionalElegivel();
        profissional.setBoasVindasControleData(LocalDate.now(FUSO_CLINICA));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(profissional));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registrarFechamentoBoasVindas(profissional, true);

        assertTrue(profissional.getBoasVindasOcultoHoje());
    }

    @Test
    void montar_agrupaAtendimentosPorSala() {
        Usuario profissional = profissionalElegivel();
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
