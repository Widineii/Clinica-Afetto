package com.clinica.sistema.service;

import com.clinica.sistema.config.SegurancaProperties;
import com.clinica.sistema.dto.AtualizarPeriodicidadeForm;
import com.clinica.sistema.dto.AtualizarTelefoneWhatsappForm;
import com.clinica.sistema.dto.CadastroProfissionalForm;
import com.clinica.sistema.dto.EditarProfissionalForm;
import com.clinica.sistema.dto.TrocarSenhaForm;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Mock
    private PagamentoConsultaService pagamentoConsultaService;

    @Mock
    private SegurancaProperties segurancaProperties;

    @Mock
    private ValorConsultaService valorConsultaService;

    @Mock
    private PerfilFotoService perfilFotoService;

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
        profissional.setDonaClinica(false);
    }

    @Test
    void deveCadastrarProfissionalQuandoUsuarioLogadoForDonaClinica() {
        Usuario dona = new Usuario();
        dona.setId(3L);
        dona.setNome("Polyana");
        dona.setCargo("ROLE_PROFISSIONAL");
        dona.setDonaClinica(true);

        when(authService.podeAcessarCentralProfissionais(dona)).thenReturn(true);
        when(segurancaProperties.isExigirTrocaSenhaPrimeiroAcesso()).thenReturn(true);
        when(usuarioRepository.findByLogin("novoprof")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("1234")).thenReturn("hash-1234");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario usuarioSalvo = usuarioService.cadastrarProfissional(form, dona);

        assertEquals("Novo Profissional", usuarioSalvo.getNome());
        assertEquals("novoprof", usuarioSalvo.getLogin());
        assertEquals("hash-1234", usuarioSalvo.getSenha());
        assertEquals("ROLE_PROFISSIONAL", usuarioSalvo.getCargo());
        assertEquals(Boolean.TRUE, usuarioSalvo.getDeveTrocarSenha());
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void adminDeveCadastrarProfissionalNaCentral() {
        when(authService.podeAcessarCentralProfissionais(admin)).thenReturn(true);
        when(segurancaProperties.isExigirTrocaSenhaPrimeiroAcesso()).thenReturn(true);
        when(usuarioRepository.findByLogin("novoprof")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("1234")).thenReturn("hash-1234");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario usuarioSalvo = usuarioService.cadastrarProfissional(form, admin);

        assertEquals("novoprof", usuarioSalvo.getLogin());
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void naoDeveCadastrarQuandoUsuarioNaoForDonaClinica() {
        when(authService.podeAcessarCentralProfissionais(profissional)).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> usuarioService.cadastrarProfissional(form, profissional));

        assertEquals("Acesso negado à central dos profissionais.", exception.getMessage());
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
        assertEquals(Boolean.FALSE, profissional.getDeveTrocarSenha());
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
        Usuario dona = new Usuario();
        dona.setId(3L);
        dona.setDonaClinica(true);

        when(authService.podeAcessarCentralProfissionais(dona)).thenReturn(true);
        when(usuarioRepository.findByLogin("novoprof")).thenReturn(Optional.of(new Usuario()));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> usuarioService.cadastrarProfissional(form, dona));

        assertEquals("Ja existe um usuario com esse login.", exception.getMessage());
    }

    @Test
    void deveMigrarAgendamentosAoAtualizarPeriodicidadeNaCentral() {
        Usuario dona = new Usuario();
        dona.setId(3L);
        dona.setDonaClinica(true);

        Usuario carol = new Usuario();
        carol.setId(7L);
        carol.setCargo("ROLE_PROFISSIONAL");
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);

        AtualizarPeriodicidadeForm form = new AtualizarPeriodicidadeForm();
        form.setUsuarioId(7L);
        form.setPeriodicidade(PeriodicidadePagamento.MENSAL);

        when(authService.podeAcessarCentralProfissionais(dona)).thenReturn(true);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(carol));
        when(pagamentoConsultaService.resolverPeriodicidade(carol)).thenReturn(PeriodicidadePagamento.DIARIO);
        when(usuarioRepository.save(carol)).thenAnswer(invocation -> invocation.getArgument(0));
        when(pagamentoConsultaService.migrarAgendamentosAoAlterarPeriodicidade(
                carol,
                PeriodicidadePagamento.DIARIO,
                PeriodicidadePagamento.MENSAL
        )).thenReturn(3);

        int migrados = usuarioService.atualizarPeriodicidadePagamento(form, dona);

        assertEquals(3, migrados);
        assertEquals(PeriodicidadePagamento.MENSAL, carol.getPeriodicidadePagamento());
        verify(pagamentoConsultaService).migrarAgendamentosAoAlterarPeriodicidade(
                carol,
                PeriodicidadePagamento.DIARIO,
                PeriodicidadePagamento.MENSAL
        );
    }

    @Test
    void profissionalDeveAlterarPropriaPeriodicidadeQuandoNuncaAlterou() {
        when(authService.podeEscolherFormaPagamento(profissional)).thenReturn(true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(profissional));
        when(pagamentoConsultaService.resolverPeriodicidade(profissional)).thenReturn(PeriodicidadePagamento.DIARIO);
        when(usuarioRepository.save(profissional)).thenAnswer(invocation -> invocation.getArgument(0));
        when(pagamentoConsultaService.migrarAgendamentosAoAlterarPeriodicidade(
                profissional,
                PeriodicidadePagamento.DIARIO,
                PeriodicidadePagamento.SEMANAL
        )).thenReturn(2);

        int migrados = usuarioService.atualizarPeriodicidadePropria(PeriodicidadePagamento.SEMANAL, profissional);

        assertEquals(2, migrados);
        assertEquals(PeriodicidadePagamento.SEMANAL, profissional.getPeriodicidadePagamento());
        assertTrue(profissional.getPeriodicidadeAlteradaEm() != null);
    }

    @Test
    void profissionalNaoDeveAlterarPeriodicidadeAntesDe24Horas() {
        profissional.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);
        profissional.setPeriodicidadeAlteradaEm(LocalDateTime.now().minusHours(2));

        when(authService.podeEscolherFormaPagamento(profissional)).thenReturn(true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(profissional));
        when(pagamentoConsultaService.resolverPeriodicidade(profissional)).thenReturn(PeriodicidadePagamento.SEMANAL);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> usuarioService.atualizarPeriodicidadePropria(PeriodicidadePagamento.MENSAL, profissional));

        assertTrue(exception.getMessage().contains("24 horas"));
    }

    @Test
    void profissionalPodeAlterarPeriodicidadeApos24Horas() {
        profissional.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);
        profissional.setPeriodicidadeAlteradaEm(LocalDateTime.now().minusHours(25));

        when(authService.podeEscolherFormaPagamento(profissional)).thenReturn(true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(profissional));
        when(pagamentoConsultaService.resolverPeriodicidade(profissional)).thenReturn(PeriodicidadePagamento.SEMANAL);
        when(usuarioRepository.save(profissional)).thenAnswer(invocation -> invocation.getArgument(0));
        when(pagamentoConsultaService.migrarAgendamentosAoAlterarPeriodicidade(
                profissional,
                PeriodicidadePagamento.SEMANAL,
                PeriodicidadePagamento.MENSAL
        )).thenReturn(1);

        int migrados = usuarioService.atualizarPeriodicidadePropria(PeriodicidadePagamento.MENSAL, profissional);

        assertEquals(1, migrados);
        assertFalse(usuarioService.podeAlterarPeriodicidadePropria(profissional));
    }

    @Test
    void mensagemBloqueioPeriodicidadeDeveSerNulaQuandoPodeAlterar() {
        profissional.setPeriodicidadeAlteradaEm(LocalDateTime.now().minusHours(30));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(profissional));

        assertNull(usuarioService.mensagemBloqueioPeriodicidade(profissional));
    }

    @Test
    void profissionalPodeSalvarTelefoneWhatsapp() {
        AtualizarTelefoneWhatsappForm form = new AtualizarTelefoneWhatsappForm();
        form.setTelefoneWhatsapp("(37) 99855-0994");

        when(authService.podeEditarProprioPerfil(profissional)).thenReturn(true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(profissional));
        when(usuarioRepository.save(profissional)).thenAnswer(invocation -> invocation.getArgument(0));

        usuarioService.atualizarTelefoneWhatsapp(form, profissional);

        assertEquals("5537998550994", profissional.getTelefoneWhatsapp());
    }

    @Test
    void adminNaoPodeSalvarTelefoneWhatsapp() {
        AtualizarTelefoneWhatsappForm form = new AtualizarTelefoneWhatsappForm();
        form.setTelefoneWhatsapp("37998550994");

        when(authService.podeEditarProprioPerfil(admin)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> usuarioService.atualizarTelefoneWhatsapp(form, admin));
    }

    @Test
    void profissionalPrecisaCadastrarWhatsappQuandoCampoVazio() {
        when(authService.podeCadastrarProprioTelefoneWhatsapp(profissional)).thenReturn(true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(profissional));

        assertTrue(usuarioService.precisaCadastrarTelefoneWhatsapp(profissional));

        profissional.setTelefoneWhatsapp("5537998550994");
        assertFalse(usuarioService.precisaCadastrarTelefoneWhatsapp(profissional));
    }

    @Test
    void gestorPodeEditarProfissionalComWhatsapp() {
        EditarProfissionalForm form = new EditarProfissionalForm();
        form.setUsuarioId(2L);
        form.setNome("Profissional Atualizado");
        form.setLogin("profissional");
        form.setTelefoneWhatsapp("37998550994");

        Usuario gestor = new Usuario();
        gestor.setId(10L);

        when(authService.podeAcessarCentralProfissionais(gestor)).thenReturn(true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(profissional));
        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.save(profissional)).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario result = usuarioService.atualizarProfissionalEquipe(form, gestor);

        assertEquals("Profissional Atualizado", result.getNome());
        assertEquals("5537998550994", result.getTelefoneWhatsapp());
    }

    @Test
    void gestorPodeLimparWhatsappDoProfissional() {
        profissional.setTelefoneWhatsapp("5537998550994");

        EditarProfissionalForm form = new EditarProfissionalForm();
        form.setUsuarioId(2L);
        form.setNome("Profissional");
        form.setLogin("profissional");
        form.setTelefoneWhatsapp("");
        form.setRemoverWhatsapp(true);

        Usuario gestor = new Usuario();
        gestor.setId(10L);

        when(authService.podeAcessarCentralProfissionais(gestor)).thenReturn(true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(profissional));
        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.save(profissional)).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario result = usuarioService.atualizarProfissionalEquipe(form, gestor);

        assertNull(result.getTelefoneWhatsapp());
    }

    @Test
    void editarProfissionalRejeitaWhatsappInvalido() {
        EditarProfissionalForm form = new EditarProfissionalForm();
        form.setUsuarioId(2L);
        form.setNome("Profissional");
        form.setLogin("profissional");
        form.setTelefoneWhatsapp("123");

        Usuario gestor = new Usuario();
        gestor.setId(10L);

        when(authService.podeAcessarCentralProfissionais(gestor)).thenReturn(true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(profissional));
        when(authService.isAdmin(profissional)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> usuarioService.atualizarProfissionalEquipe(form, gestor));
    }
}
