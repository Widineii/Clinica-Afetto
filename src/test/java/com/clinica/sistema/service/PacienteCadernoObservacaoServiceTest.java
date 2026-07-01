package com.clinica.sistema.service;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PacienteCadernoObservacao;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.PacienteCadernoObservacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacienteCadernoObservacaoServiceTest {

    @Mock
    private PacienteCadernoObservacaoRepository repository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private PacienteCadernoObservacaoService service;

    private Usuario profissional;

    @BeforeEach
    void setUp() {
        profissional = new Usuario();
        profissional.setId(10L);
        profissional.setNome("Carol");
    }

    @Test
    void deveCriarAnotacaoNoCaderno() {
        Agendamento agendamento = agendamentoSerie(22L);
        when(authService.podeAcessarMeusPacientes(profissional)).thenReturn(true);
        when(agendamentoRepository.findById(22L)).thenReturn(Optional.of(agendamento));
        when(repository.save(any(PacienteCadernoObservacao.class))).thenAnswer(invocation -> {
            PacienteCadernoObservacao salva = invocation.getArgument(0);
            salva.setId(5L);
            return salva;
        });

        var view = service.criar(profissional, "sr-22", "  Prefere sessão pela manhã.  ", null, null);

        ArgumentCaptor<PacienteCadernoObservacao> captor = ArgumentCaptor.forClass(PacienteCadernoObservacao.class);
        verify(repository).save(captor.capture());
        assertEquals("Prefere sessão pela manhã.", captor.getValue().getTexto());
        assertEquals("Prefere sessão pela manhã.", view.getTexto());
        assertEquals(5L, view.getId());
    }

    @Test
    void deveAtualizarAnotacaoExistente() {
        Agendamento agendamento = agendamentoSerie(22L);
        PacienteCadernoObservacao existente = new PacienteCadernoObservacao();
        existente.setId(7L);
        existente.setProfissional(profissional);
        existente.setChaveCaderno("sr-22");
        existente.setTexto("Antiga");
        existente.setCriadoEm(LocalDateTime.now().minusDays(1));
        existente.setAtualizadoEm(LocalDateTime.now().minusDays(1));

        when(repository.findByIdAndProfissionalId(7L, 10L)).thenReturn(Optional.of(existente));
        when(authService.podeAcessarMeusPacientes(profissional)).thenReturn(true);
        when(agendamentoRepository.findById(22L)).thenReturn(Optional.of(agendamento));
        when(repository.save(any(PacienteCadernoObservacao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var view = service.atualizar(profissional, "sr-22", 7L, "Texto atualizado", null, null);

        assertEquals("Texto atualizado", view.getTexto());
    }

    @Test
    void deveListarAnotacoesDoCaderno() {
        PacienteCadernoObservacao observacao = new PacienteCadernoObservacao();
        observacao.setId(1L);
        observacao.setTexto("Lembrete");
        observacao.setCriadoEm(LocalDateTime.now());
        observacao.setAtualizadoEm(LocalDateTime.now());
        when(repository.findByProfissionalIdAndChaveCadernoOrderByCriadoEmAsc(10L, "sr-22"))
                .thenReturn(List.of(observacao));

        var anotacoes = service.listar(profissional, "sr-22");

        assertEquals(1, anotacoes.size());
        assertEquals("Lembrete", anotacoes.get(0).getTexto());
    }

    @Test
    void deveRecusarCadernoDeOutroProfissional() {
        Usuario outro = new Usuario();
        outro.setId(99L);
        Agendamento agendamento = agendamentoSerie(22L);
        agendamento.setProfissional(outro);
        when(authService.podeAcessarMeusPacientes(profissional)).thenReturn(true);
        when(agendamentoRepository.findById(22L)).thenReturn(Optional.of(agendamento));

        RuntimeException erro = assertThrows(RuntimeException.class,
                () -> service.criar(profissional, "sr-22", "Teste", null, null));

        assertEquals("Sem permissão para anotar neste caderno.", erro.getMessage());
    }

    @Test
    void deveRecusarTextoVazio() {
        RuntimeException erro = assertThrows(RuntimeException.class,
                () -> service.criar(profissional, "sr-22", "   ", null, null));

        assertEquals("Escreva algo antes de salvar a anotação.", erro.getMessage());
        verify(repository, never()).save(any());
    }

    private Agendamento agendamentoSerie(Long id) {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(id);
        agendamento.setProfissional(profissional);
        agendamento.setFixo(true);
        agendamento.setSerieFixaId("semanal-10-cliente");
        agendamento.setRecorrencia("SEMANAL");
        return agendamento;
    }
}
