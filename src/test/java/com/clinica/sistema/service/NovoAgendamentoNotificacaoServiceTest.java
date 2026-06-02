package com.clinica.sistema.service;

import com.clinica.sistema.dto.NovoAgendamentoNotificacaoView;
import com.clinica.sistema.model.NovoAgendamentoNotificacaoRegistro;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.NovoAgendamentoNotificacaoRegistroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovoAgendamentoNotificacaoServiceTest {

    @Mock
    private NovoAgendamentoNotificacaoRegistroRepository repository;

    private NovoAgendamentoNotificacaoService service;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        service = new NovoAgendamentoNotificacaoService(repository);
        session = new MockHttpSession();
    }

    @Test
    void listaNovosAgendamentosDesdeUltimoVisto() {
        NovoAgendamentoNotificacaoRegistro registro = registro(3L, "Julia", "Maria Silva", "Sala 2");
        when(repository.findTop15ByIdGreaterThanOrderByRegistradoEmDescIdDesc(0L))
                .thenReturn(List.of(registro));

        List<NovoAgendamentoNotificacaoView> pendentes = service.listarPendentes(session);

        assertEquals(1, pendentes.size());
        assertTrue(pendentes.get(0).getMensagemResumo().contains("Julia"));
        assertTrue(pendentes.get(0).getMensagemDetalhe().contains("Maria Silva"));
    }

    @Test
    void marcarComoVistoRemoveBolinha() {
        when(repository.findFirstByOrderByRegistradoEmDescIdDesc())
                .thenReturn(Optional.of(registro(9L, "Carol", "João", "Sala 1")));
        when(repository.countByIdGreaterThan(0L)).thenReturn(1L);
        when(repository.countByIdGreaterThan(9L)).thenReturn(0L);

        assertTrue(service.deveExibirBolinha(session));

        service.marcarComoVisto(session);

        assertEquals(9L, service.obterUltimoVistoId(session));
        assertFalse(service.deveExibirBolinha(session));
    }

    private NovoAgendamentoNotificacaoRegistro registro(
            Long id,
            String profissionalNome,
            String cliente,
            String salaNome
    ) {
        Usuario profissional = new Usuario();
        profissional.setNome(profissionalNome);

        Usuario registradoPor = new Usuario();
        registradoPor.setNome(profissionalNome);

        Sala sala = new Sala();
        sala.setId(2L);
        sala.setNome(salaNome);

        NovoAgendamentoNotificacaoRegistro registro = new NovoAgendamentoNotificacaoRegistro();
        registro.setId(id);
        registro.setNomeCliente(cliente);
        registro.setProfissional(profissional);
        registro.setRegistradoPor(registradoPor);
        registro.setSala(sala);
        registro.setDataHoraInicio(LocalDateTime.of(2026, 6, 10, 14, 0));
        registro.setTipoRecorrencia("AVULSO");
        registro.setQuantidadeHorarios(1);
        registro.setRegistradoEm(LocalDateTime.now());
        return registro;
    }
}
