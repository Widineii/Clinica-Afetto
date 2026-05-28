package com.clinica.sistema.service;

import com.clinica.sistema.dto.CadastroProfissionalForm;
import com.clinica.sistema.dto.TrocarSenhaForm;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private com.clinica.sistema.repository.AgendamentoRepository agendamentoRepository;

    @Mock
    private AuthService authService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private CadastroProfissionalForm form;
    private Usuario admin;
    private Usuario profissional;

    @BeforeEach
    void setUp() {
        form = new CadastroProfissionalForm();
        form.setNome("Novo Profissional");
        form.setLogin("novoprof");
        form.setSenha("1234");

        admin = new Usuario();
        admin.setId(1L);
        admin.setNome("Admin");
        admin.setCargo("ROLE_ADMIN");

        profissional = new Usuario();
        profissional.setId(2L);
        profissional.setNome("Profissional");
        profissional.setCargo("ROLE_PROFISSIONAL");
    }

    @Test
    void deveCadastrarProfissionalQuandoUsuarioLogadoForAdmin() {
        when(authService.podeGerenciarEquipe(admin)).thenReturn(true);
        when(usuarioRepository.findByLogin("novoprof")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("1234")).thenReturn("hash-1234");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario usuarioSalvo = usuarioService.cadastrarProfissional(form, admin);

        assertEquals("Novo Profissional", usuarioSalvo.getNome());
        assertEquals("novoprof", usuarioSalvo.getLogin());
        assertEquals("hash-1234", usuarioSalvo.getSenha());
        assertEquals("ROLE_PROFISSIONAL", usuarioSalvo.getCargo());
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void naoDeveCadastrarQuandoUsuarioNaoForAdmin() {
        when(authService.podeGerenciarEquipe(profissional)).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> usuarioService.cadastrarProfissional(form, profissional));

        assertEquals("Somente administracao ou dona da clinica podem gerenciar a equipe.", exception.getMessage());
    }

    @Test
    void deveTrocarSenhaDoUsuarioLogado() {
        profissional.setSenha("hash-antiga");
        TrocarSenhaForm formSenha = new TrocarSenhaForm();
        formSenha.setSenhaAtual("1234");
        formSenha.setNovaSenha("nova1234");
        formSenha.setConfirmarSenha("nova1234");

        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(passwordEncoder.matches(eq("1234"), eq("hash-antiga"))).thenReturn(true);
        when(passwordEncoder.encode("nova1234")).thenReturn("hash-nova");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        usuarioService.trocarSenha(formSenha, profissional);

        assertEquals("hash-nova", profissional.getSenha());
        verify(usuarioRepository).save(profissional);
    }

    @Test
    void naoDeveTrocarSenhaComSenhaAtualIncorreta() {
        profissional.setSenha("hash-antiga");
        TrocarSenhaForm formSenha = new TrocarSenhaForm();
        formSenha.setSenhaAtual("errada");
        formSenha.setNovaSenha("nova1234");
        formSenha.setConfirmarSenha("nova1234");

        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(passwordEncoder.matches(anyString(), eq("hash-antiga"))).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> usuarioService.trocarSenha(formSenha, profissional));

        assertEquals("Senha atual incorreta.", exception.getMessage());
    }

    @Test
    void naoDeveCadastrarQuandoLoginJaExistir() {
        when(authService.podeGerenciarEquipe(admin)).thenReturn(true);
        when(usuarioRepository.findByLogin("novoprof")).thenReturn(Optional.of(new Usuario()));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> usuarioService.cadastrarProfissional(form, admin));

        assertEquals("Ja existe um usuario com esse login.", exception.getMessage());
    }
}
