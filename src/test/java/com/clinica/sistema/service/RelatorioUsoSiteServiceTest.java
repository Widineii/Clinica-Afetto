package com.clinica.sistema.service;

import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioUsoSiteServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @InjectMocks
    private RelatorioUsoSiteService relatorioUsoSiteService;

    @Test
    void montarRelatorioSeparaQuemAcessouEQuemAgendou() {
        Usuario julia = profissional(1L, "Julia", "julia", LocalDateTime.now().minusDays(1));
        Usuario carol = profissional(2L, "Carol", "carol", null);

        when(usuarioRepository.findByCargoOrderByNomeAsc("ROLE_PROFISSIONAL")).thenReturn(List.of(julia, carol));
        List<Object[]> contagem = new java.util.ArrayList<>();
        contagem.add(new Object[]{1L, 3L});
        when(agendamentoRepository.contarAgendamentosPorProfissional()).thenReturn(contagem);

        var relatorio = relatorioUsoSiteService.montarRelatorio();

        assertEquals(2, relatorio.totalProfissionais());
        assertEquals(1, relatorio.totalJaAcessaram());
        assertEquals(1, relatorio.totalNuncaAcessaram());
        assertEquals(1, relatorio.totalJaAgendaram());
        assertEquals(1, relatorio.totalNaoAgendaram());

        var linhaJulia = relatorio.profissionais().get(0);
        assertTrue(linhaJulia.jaAcessouSite());
        assertTrue(linhaJulia.jaAgendou());
        assertEquals(3L, linhaJulia.totalAgendamentos());

        var linhaCarol = relatorio.profissionais().get(1);
        assertFalse(linhaCarol.jaAcessouSite());
        assertFalse(linhaCarol.jaAgendou());
    }

    private Usuario profissional(Long id, String nome, String login, LocalDateTime ultimoAcesso) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome(nome);
        usuario.setLogin(login);
        usuario.setCargo("ROLE_PROFISSIONAL");
        usuario.setUltimoAcessoEm(ultimoAcesso);
        return usuario;
    }
}
