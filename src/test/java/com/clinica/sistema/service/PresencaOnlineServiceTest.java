package com.clinica.sistema.service;

import com.clinica.sistema.config.PresencaOnlineProperties;
import com.clinica.sistema.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresencaOnlineServiceTest {

    @Mock
    private AuthService authService;

    private PresencaOnlineService service;

    @BeforeEach
    void setUp() {
        PresencaOnlineProperties properties = new PresencaOnlineProperties();
        properties.setMinutosAtivos(5);
        service = new PresencaOnlineService(properties, authService);
        lenient().when(authService.deveAparecerNaPresencaOnline(any())).thenReturn(true);
    }

    @Test
    void contaUsuariosDistintosOnline() {
        service.registrarAtividade(usuario(1L, "Polyana"));
        service.registrarAtividade(usuario(2L, "Carol"));

        assertEquals(2, service.contarUsuariosOnline());
    }

    @Test
    void listaUsuariosOnlineOrdenadosPorNome() {
        service.registrarAtividade(usuario(1L, "Polyana"));
        service.registrarAtividade(usuario(2L, "Carol"));

        var nomes = service.listarUsuariosOnline().stream()
                .map(view -> view.nome())
                .toList();

        assertEquals("Carol", nomes.get(0));
        assertEquals("Polyana", nomes.get(1));
    }

    @Test
    void montarVisaoAtualIncluiTotalENomes() {
        service.registrarAtividade(usuario(1L, "Polyana"));

        var visao = service.montarVisaoAtual();

        assertEquals(1, visao.online());
        assertEquals(5, visao.minutosAtivos());
        assertEquals(1, visao.usuarios().size());
        assertEquals("Polyana", visao.usuarios().get(0).nome());
    }

    @Test
    void montarVisaoDemoLocalRetornaCincoNomes() {
        PresencaOnlineProperties properties = new PresencaOnlineProperties();
        properties.setDemoLocal(true);
        PresencaOnlineService serviceDemo = new PresencaOnlineService(properties, authService);

        var visao = serviceDemo.montarVisaoAtual();

        assertEquals(5, visao.online());
        assertEquals(5, visao.usuarios().size());
        assertEquals("Lucas", visao.usuarios().get(1).nome());
    }

    @Test
    void naoRegistraAdminPrincipal() {
        when(authService.deveAparecerNaPresencaOnline(any())).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            return usuario != null && !"admin".equalsIgnoreCase(usuario.getLogin());
        });

        service.registrarAtividade(usuario(1L, "Administrador", "admin"));
        service.registrarAtividade(usuario(2L, "Lucas", "lucas"));

        assertEquals(1, service.contarUsuariosOnline());
        assertEquals("Lucas", service.listarUsuariosOnline().get(0).nome());
    }

    private static Usuario usuario(Long id, String nome) {
        return usuario(id, nome, nome.toLowerCase());
    }

    private static Usuario usuario(Long id, String nome, String login) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome(nome);
        usuario.setLogin(login);
        usuario.setCargo("ROLE_PROFISSIONAL");
        return usuario;
    }
}
