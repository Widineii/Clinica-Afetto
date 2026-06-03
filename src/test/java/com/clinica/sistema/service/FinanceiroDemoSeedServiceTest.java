package com.clinica.sistema.service;

import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceiroDemoSeedServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SalaRepository salaRepository;

    @Mock
    private ValorConsultaService valorConsultaService;

    private FinanceiroDemoSeedService service;

    @BeforeEach
    void setUp() {
        service = new FinanceiroDemoSeedService(
                agendamentoRepository,
                usuarioRepository,
                salaRepository,
                valorConsultaService
        );
    }

    @Test
    void semearPixDemonstracaoMesAtual_criaUmPixPorProfissional() {
        when(usuarioRepository.findByCargoOrderByNomeAsc("ROLE_PROFISSIONAL")).thenReturn(List.of(
                profissional("carol", "Carol"),
                profissional("julia", "Julia"),
                profissional("breno", "Breno")
        ));
        when(salaRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(
                sala("Sala 1"),
                sala("Sala 2"),
                sala("Sala 3"),
                sala("Sala 4")
        ));
        when(agendamentoRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        int criados = service.semearPixDemonstracaoMesAtual();

        assertEquals(3 * FinanceiroDemoSeedService.PAGAMENTOS_POR_PROFISSIONAL, criados);
        verify(agendamentoRepository).deleteByNomeClienteLike(anyString());
        verify(agendamentoRepository).saveAll(anyList());
    }

    @Test
    void limparPixDemonstracao_removeRegistrosDemo() {
        when(agendamentoRepository.deleteByNomeClienteLike(anyString())).thenReturn(12);

        int removidos = service.limparPixDemonstracao();

        assertEquals(12, removidos);
        verify(agendamentoRepository).deleteByNomeClienteLike("DEMO-FIN-%");
    }

    private Usuario profissional(String login, String nome) {
        Usuario usuario = new Usuario();
        usuario.setId((long) login.hashCode());
        usuario.setLogin(login);
        usuario.setNome(nome);
        usuario.setCargo("ROLE_PROFISSIONAL");
        usuario.setDonaClinica(false);
        return usuario;
    }

    private Sala sala(String nome) {
        Sala sala = new Sala();
        sala.setId((long) nome.hashCode());
        sala.setNome(nome);
        return sala;
    }
}
