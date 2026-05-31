package com.clinica.sistema.service;

import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioProfissionalDemoSeedServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private SalaRepository salaRepository;

    private RelatorioProfissionalDemoSeedService service;

    @BeforeEach
    void setUp() {
        service = new RelatorioProfissionalDemoSeedService(agendamentoRepository, salaRepository);
    }

    @Test
    void semearDemonstracaoProfissional_criaAtendimentosNasSalas123() {
        Usuario julia = new Usuario();
        julia.setId(3L);
        julia.setNome("Julia");

        when(agendamentoRepository.deleteByProfissionalIdAndNomeClienteLike(3L, RelatorioProfissionalDemoSeedService.PREFIXO_CLIENTE + "%"))
                .thenReturn(0);
        when(salaRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(
                sala("Sala 1"),
                sala("Sala 2"),
                sala("Sala 3")
        ));

        int criados = service.semearDemonstracaoProfissional(julia);

        assertEquals(5, criados);
        ArgumentCaptor<List<com.clinica.sistema.model.Agendamento>> captor = ArgumentCaptor.forClass(List.class);
        verify(agendamentoRepository).saveAll(captor.capture());
        assertTrue(captor.getValue().stream().anyMatch(item -> "Sala 1".equals(item.getSala().getNome())));
        assertTrue(captor.getValue().stream().anyMatch(item -> "Sala 2".equals(item.getSala().getNome())));
        assertTrue(captor.getValue().stream().allMatch(item -> item.getNomeCliente().startsWith(RelatorioProfissionalDemoSeedService.PREFIXO_CLIENTE)));
    }

    @Test
    void limparDemonstracaoProfissional_removeSomenteDoProfissional() {
        Usuario julia = new Usuario();
        julia.setId(3L);
        when(agendamentoRepository.deleteByProfissionalIdAndNomeClienteLike(eq(3L), any())).thenReturn(2);

        int removidos = service.limparDemonstracaoProfissional(julia);

        assertEquals(2, removidos);
    }

    private Sala sala(String nome) {
        Sala sala = new Sala();
        sala.setNome(nome);
        return sala;
    }
}
