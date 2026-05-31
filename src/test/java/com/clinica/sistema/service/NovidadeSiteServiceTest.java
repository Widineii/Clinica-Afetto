package com.clinica.sistema.service;

import com.clinica.sistema.model.NovidadeLeituraUsuario;
import com.clinica.sistema.model.NovidadePublicoAlvo;
import com.clinica.sistema.model.NovidadeSite;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.NovidadeLeituraUsuarioRepository;
import com.clinica.sistema.repository.NovidadeSiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovidadeSiteServiceTest {

    @Mock
    private NovidadeSiteRepository novidadeSiteRepository;

    @Mock
    private NovidadeLeituraUsuarioRepository leituraRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private NovidadeSiteService novidadeSiteService;

    private Usuario julia;
    private Usuario polyana;
    private NovidadeSite novidadeProfissional;
    private NovidadeSite novidadeDona;

    @BeforeEach
    void setUp() {
        julia = new Usuario();
        julia.setId(10L);
        julia.setNome("Julia");
        julia.setCargo("ROLE_PROFISSIONAL");
        julia.setDonaClinica(false);

        polyana = new Usuario();
        polyana.setId(99L);
        polyana.setNome("Polyana");
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        novidadeProfissional = criarNovidade(1L, NovidadePublicoAlvo.PROFISSIONAL);
        novidadeDona = criarNovidade(2L, NovidadePublicoAlvo.DONA_CLINICA);
    }

    @Test
    void profissionalVeSomenteNovidadesDeProfissional() {
        when(authService.isDonaClinica(julia)).thenReturn(false);
        when(authService.isAdmin(julia)).thenReturn(false);
        when(novidadeSiteRepository.findAllByOrderByOrdemExibicaoDescPublicadaEmDesc())
                .thenReturn(List.of(novidadeProfissional, novidadeDona));
        when(leituraRepository.findByUsuarioIdAndNovidadeIdIn(10L, List.of(1L)))
                .thenReturn(List.of());

        var novidades = novidadeSiteService.listarVisiveisParaUsuario(julia);

        assertEquals(1, novidades.size());
        assertEquals(1L, novidades.get(0).getId());
    }

    @Test
    void polyanaVeTodasNovidades() {
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(novidadeSiteRepository.findAllByOrderByOrdemExibicaoDescPublicadaEmDesc())
                .thenReturn(List.of(novidadeProfissional, novidadeDona));
        when(leituraRepository.findByUsuarioIdAndNovidadeIdIn(99L, List.of(1L, 2L)))
                .thenReturn(List.of());

        var novidades = novidadeSiteService.listarVisiveisParaUsuario(polyana);

        assertEquals(2, novidades.size());
    }

    @Test
    void ocultarDefinitivoRemoveNovidade() {
        when(authService.isDonaClinica(julia)).thenReturn(false);
        when(authService.isAdmin(julia)).thenReturn(false);
        when(novidadeSiteRepository.findAllByOrderByOrdemExibicaoDescPublicadaEmDesc())
                .thenReturn(List.of(novidadeProfissional));

        NovidadeLeituraUsuario leitura = new NovidadeLeituraUsuario();
        leitura.setNovidadeId(1L);
        leitura.setPrimeiraVisualizacaoEm(LocalDateTime.now());
        leitura.setOcultarDefinitivo(true);
        when(leituraRepository.findByUsuarioIdAndNovidadeIdIn(10L, List.of(1L)))
                .thenReturn(List.of(leitura));

        assertTrue(novidadeSiteService.listarVisiveisParaUsuario(julia).isEmpty());
    }

    @Test
    void expiraAposTresDiasSemMarcarNaoMostrar() {
        when(authService.isDonaClinica(julia)).thenReturn(false);
        when(authService.isAdmin(julia)).thenReturn(false);
        when(novidadeSiteRepository.findAllByOrderByOrdemExibicaoDescPublicadaEmDesc())
                .thenReturn(List.of(novidadeProfissional));

        NovidadeLeituraUsuario leitura = new NovidadeLeituraUsuario();
        leitura.setNovidadeId(1L);
        leitura.setPrimeiraVisualizacaoEm(LocalDateTime.now().minusDays(4));
        leitura.setOcultarDefinitivo(false);
        when(leituraRepository.findByUsuarioIdAndNovidadeIdIn(10L, List.of(1L)))
                .thenReturn(List.of(leitura));

        assertTrue(novidadeSiteService.listarVisiveisParaUsuario(julia).isEmpty());
    }

    @Test
    void dispensarComNaoMostrarMarcaOcultarDefinitivo() {
        when(leituraRepository.findByUsuarioIdAndNovidadeId(10L, 1L)).thenReturn(Optional.empty());
        when(leituraRepository.save(any(NovidadeLeituraUsuario.class))).thenAnswer(inv -> inv.getArgument(0));

        novidadeSiteService.dispensarNovidades(julia, List.of(1L), true);

        verify(leituraRepository).save(org.mockito.ArgumentMatchers.argThat(leitura ->
                leitura.isOcultarDefinitivo() && leitura.getUsuarioId().equals(10L)
        ));
    }

    @Test
    void dispensarSemMarcarNaoOcultaDefinitivo() {
        when(leituraRepository.findByUsuarioIdAndNovidadeId(10L, 1L)).thenReturn(Optional.empty());
        when(leituraRepository.save(any(NovidadeLeituraUsuario.class))).thenAnswer(inv -> inv.getArgument(0));

        novidadeSiteService.dispensarNovidades(julia, List.of(1L), false);

        verify(leituraRepository).save(org.mockito.ArgumentMatchers.argThat(leitura ->
                !leitura.isOcultarDefinitivo()
        ));
    }

    @Test
    void listaVaziaQuandoUsuarioNulo() {
        assertTrue(novidadeSiteService.listarVisiveisParaUsuario(null).isEmpty());
        verify(novidadeSiteRepository, never()).findAllByOrderByOrdemExibicaoDescPublicadaEmDesc();
    }

    private NovidadeSite criarNovidade(Long id, NovidadePublicoAlvo publicoAlvo) {
        NovidadeSite novidade = new NovidadeSite();
        novidade.setId(id);
        novidade.setCodigo("teste-" + id);
        novidade.setVersao("2.480");
        novidade.setTitulo("Teste");
        novidade.setDescricao("Descricao");
        novidade.setPublicoAlvo(publicoAlvo);
        novidade.setPublicadaEm(LocalDateTime.now());
        novidade.setOrdemExibicao(1);
        return novidade;
    }
}
