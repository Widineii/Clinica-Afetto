package com.clinica.sistema.config;

import com.clinica.sistema.dto.AtualizarTelefoneWhatsappForm;
import com.clinica.sistema.dto.ResumoPendenciasPagamentoView;
import com.clinica.sistema.dto.SuporteContratoView;
import com.clinica.sistema.dto.TrocarSenhaForm;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.ContratoLicenciamentoService;
import com.clinica.sistema.service.FinanceiroPolyanaAcessoService;
import com.clinica.sistema.service.PagamentoConsultaService;
import com.clinica.sistema.service.PerfilFotoService;
import com.clinica.sistema.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Atributos compartilhados pelo menu principal do site (todas as telas autenticadas).
 */
@ControllerAdvice
public class SiteMenuModelAdvice {

    private final AuthService authService;
    private final FinanceiroPolyanaAcessoService financeiroPolyanaAcessoService;
    private final PagamentoConsultaService pagamentoConsultaService;
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final PerfilFotoService perfilFotoService;
    private final ManualProperties manualProperties;
    private final ContratoLicenciamentoService contratoLicenciamentoService;

    public SiteMenuModelAdvice(
            AuthService authService,
            FinanceiroPolyanaAcessoService financeiroPolyanaAcessoService,
            PagamentoConsultaService pagamentoConsultaService,
            UsuarioService usuarioService,
            UsuarioRepository usuarioRepository,
            PerfilFotoService perfilFotoService,
            ManualProperties manualProperties,
            ContratoLicenciamentoService contratoLicenciamentoService
    ) {
        this.authService = authService;
        this.financeiroPolyanaAcessoService = financeiroPolyanaAcessoService;
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.perfilFotoService = perfilFotoService;
        this.manualProperties = manualProperties;
        this.contratoLicenciamentoService = contratoLicenciamentoService;
    }

    @ModelAttribute
    public void prepararContextoMenuSite(Model model, HttpSession session, HttpServletRequest request) {
        model.addAttribute("manualWhatsappAtivo", manualProperties.temWhatsappSuporte());
        model.addAttribute("manualWhatsappUrl", manualProperties.resolverLinkWhatsapp());
        model.addAttribute("manualWhatsappClinicaAtivo", manualProperties.temWhatsappClinica());
        model.addAttribute("manualWhatsappClinicaUrl", manualProperties.resolverLinkWhatsappClinica());
        model.addAttribute("manualWhatsappClinicaRotulo", manualProperties.resolverRotuloWhatsappClinicaExibicao());
        if (!model.containsAttribute("suporteContrato")) {
            model.addAttribute("suporteContrato", SuporteContratoView.inativo());
        }
        if (!model.containsAttribute("exibirWhatsappSuporte")) {
            model.addAttribute("exibirWhatsappSuporte", manualProperties.temWhatsappSuporte());
        }
        model.addAttribute("retornoPerfilUrl", resolverRetornoPerfil(request));
        if (!model.containsAttribute("reabrirModalEditarPerfil")) {
            model.addAttribute("reabrirModalEditarPerfil", false);
        }
        if (!model.containsAttribute("exibirModalBoasVindasLogin")) {
            model.addAttribute("exibirModalBoasVindasLogin", false);
        }
        if (!model.containsAttribute("pendenciasPagamentoDepoisBoasVindas")) {
            model.addAttribute("pendenciasPagamentoDepoisBoasVindas", false);
        }
        if (!model.containsAttribute("novidadesDepoisBoasVindas")) {
            model.addAttribute("novidadesDepoisBoasVindas", false);
        }

        authService.buscarUsuarioLogado().ifPresentOrElse(
                usuario -> preencherMenuUsuario(model, session, usuario),
                () -> {
                    model.addAttribute("menuQtdAcoesPendentes", 0);
                    model.addAttribute("menuPerfilRotulo", "");
                    model.addAttribute("podeEditarPerfil", false);
                    model.addAttribute("suporteContrato", SuporteContratoView.inativo());
                    model.addAttribute("exibirWhatsappSuporte", manualProperties.temWhatsappSuporte());
                }
        );
    }

    private void preencherMenuUsuario(Model model, HttpSession session, Usuario usuario) {
        boolean isAdmin = authService.isAdmin(usuario);
        boolean isDonaClinica = authService.isDonaClinica(usuario);
        boolean ehProfissional = !isAdmin && !isDonaClinica;

        if (!model.containsAttribute("usuarioLogado")) {
            model.addAttribute("usuarioLogado", usuario);
        }
        if (!model.containsAttribute("isAdmin")) {
            model.addAttribute("isAdmin", isAdmin);
        }
        if (!model.containsAttribute("isDonaClinica")) {
            model.addAttribute("isDonaClinica", isDonaClinica);
        }
        if (!model.containsAttribute("ehProfissional")) {
            model.addAttribute("ehProfissional", ehProfissional);
        }
        if (!model.containsAttribute("podeGerenciarEquipe")) {
            model.addAttribute("podeGerenciarEquipe", authService.podeGerenciarEquipe(usuario));
        }
        if (!model.containsAttribute("podeAcessarArquivoSistema")) {
            model.addAttribute("podeAcessarArquivoSistema", authService.podeAcessarArquivoSistema(usuario));
        }
        if (!model.containsAttribute("podeGerenciarValoresConsulta")) {
            model.addAttribute(
                    "podeGerenciarValoresConsulta",
                    authService.podeGerenciarValoresConsultaProfissionais(usuario)
            );
        }
        if (!model.containsAttribute("podeAcessarGestaoFinanceira")) {
            model.addAttribute(
                    "podeAcessarGestaoFinanceira",
                    financeiroPolyanaAcessoService.podeAcessarGestaoFinanceira(usuario)
            );
        }
        if (!model.containsAttribute("podeVerRelatorioUsoSite")) {
            model.addAttribute("podeVerRelatorioUsoSite", authService.podeVerRelatorioUsoSite(usuario));
        }
        if (!model.containsAttribute("podeAcessarCentralProfissionais")) {
            model.addAttribute("podeAcessarCentralProfissionais", authService.podeAcessarCentralProfissionais(usuario));
        }
        if (!model.containsAttribute("podeVerRelatorioProprio")) {
            model.addAttribute("podeVerRelatorioProprio", authService.podeVerRelatorioProprio(usuario));
        }
        if (!model.containsAttribute("podeAcessarMeusPacientes")) {
            model.addAttribute("podeAcessarMeusPacientes", authService.podeAcessarMeusPacientes(usuario));
        }
        if (!model.containsAttribute("podeTrocarPropriaSenha")) {
            model.addAttribute("podeTrocarPropriaSenha", authService.podeTrocarPropriaSenha(usuario));
        }
        if (!model.containsAttribute("podeGerenciarContaAdmin")) {
            model.addAttribute("podeGerenciarContaAdmin", authService.podeGerenciarContaAdmin(usuario));
        }
        if (!model.containsAttribute("podeAcessarContratoLicenciamento")) {
            model.addAttribute(
                    "podeAcessarContratoLicenciamento",
                    authService.podeAcessarContratoLicenciamento(usuario)
            );
        }
        SuporteContratoView suporteContrato = contratoLicenciamentoService.resolverStatusSuporte();
        model.addAttribute("suporteContrato", suporteContrato);
        model.addAttribute(
                "exibirWhatsappSuporte",
                manualProperties.temWhatsappSuporte()
                        && (isAdmin || !suporteContrato.expirado())
        );
        if (!model.containsAttribute("podeEscolherTema")) {
            model.addAttribute("podeEscolherTema", authService.podeEscolherTema(usuario));
        }
        if (authService.podeTrocarPropriaSenha(usuario) && !model.containsAttribute("trocarSenhaForm")) {
            model.addAttribute("trocarSenhaForm", new TrocarSenhaForm());
        }
        if (!model.containsAttribute("podeEscolherFormaPagamento")) {
            model.addAttribute("podeEscolherFormaPagamento", authService.podeEscolherFormaPagamento(usuario));
        }
        if (authService.podeEscolherFormaPagamento(usuario)) {
            if (!model.containsAttribute("periodicidadesPagamento")) {
                model.addAttribute("periodicidadesPagamento", PeriodicidadePagamento.values());
            }
            if (!model.containsAttribute("podeAlterarPeriodicidadePropria")) {
                model.addAttribute(
                        "podeAlterarPeriodicidadePropria",
                        usuarioService.podeAlterarPeriodicidadePropria(usuario)
                );
            }
            if (!model.containsAttribute("mensagemBloqueioPeriodicidade")) {
                model.addAttribute(
                        "mensagemBloqueioPeriodicidade",
                        usuarioService.mensagemBloqueioPeriodicidade(usuario)
                );
            }
        }
        if (!model.containsAttribute("periodicidadePagamento")) {
            model.addAttribute("periodicidadePagamento", pagamentoConsultaService.resolverPeriodicidade(usuario));
        }

        String perfilRotulo = resolverPerfilRotulo(isAdmin, isDonaClinica);
        model.addAttribute("menuPerfilRotulo", perfilRotulo);

        boolean trocaSenhaPendente = authService.podeTrocarPropriaSenha(usuario)
                && usuarioService.usuarioLogadoDeveTrocarSenha();
        if (!model.containsAttribute("trocaSenhaAindaPendente")) {
            model.addAttribute("trocaSenhaAindaPendente", trocaSenhaPendente);
        } else {
            trocaSenhaPendente = Boolean.TRUE.equals(model.getAttribute("trocaSenhaAindaPendente"));
        }

        int qtdPendencias = resolverQtdPendencias(model, session, usuario, ehProfissional);
        if (!model.containsAttribute("totalMeusPagamentosPendentes")) {
            model.addAttribute("totalMeusPagamentosPendentes", qtdPendencias);
        } else {
            Object totalAttr = model.getAttribute("totalMeusPagamentosPendentes");
            if (totalAttr instanceof Number numero) {
                qtdPendencias = numero.intValue();
            }
        }

        int qtdAcoes = 0;
        if (ehProfissional) {
            qtdAcoes = qtdPendencias;
            if (trocaSenhaPendente) {
                qtdAcoes++;
            }
        }
        model.addAttribute("menuQtdAcoesPendentes", qtdAcoes);

        boolean podeEditarPerfil = authService.podeEditarProprioPerfil(usuario);
        model.addAttribute("podeEditarPerfil", podeEditarPerfil);
        if (!model.containsAttribute("podeCadastrarEmailNotificacao")) {
            model.addAttribute(
                    "podeCadastrarEmailNotificacao",
                    authService.podeCadastrarEmailNotificacaoPagamento(usuario)
            );
        }
        if (podeEditarPerfil && !model.containsAttribute("atualizarTelefoneWhatsappForm")) {
            AtualizarTelefoneWhatsappForm formPerfil = new AtualizarTelefoneWhatsappForm();
            formPerfil.setTelefoneWhatsapp(usuario.getTelefoneWhatsappFormulario());
            if (usuario.getEmail() != null && !usuario.getEmail().isBlank()) {
                formPerfil.setEmail(usuario.getEmail());
            }
            model.addAttribute("atualizarTelefoneWhatsappForm", formPerfil);
        }

        usuarioRepository.findById(usuario.getId()).ifPresent(atualizado -> {
            String fotoUrl = perfilFotoService.resolverUrlPublica(atualizado);
            if (fotoUrl != null) {
                model.addAttribute("fotoPerfilUrl", fotoUrl);
            }
        });
    }

    private static String resolverRetornoPerfil(HttpServletRequest request) {
        if (request == null) {
            return "/agendamentos/dashboard";
        }
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank() || "/".equals(uri)) {
            return "/agendamentos/dashboard";
        }
        return uri;
    }

    private int resolverQtdPendencias(Model model, HttpSession session, Usuario usuario, boolean ehProfissional) {
        if (!ehProfissional || authService.profissionalIgnoraValoresEPagamento(usuario)) {
            if (!model.containsAttribute("resumoPendenciasPagamento")) {
                model.addAttribute("resumoPendenciasPagamento", ResumoPendenciasPagamentoView.vazio());
            }
            return 0;
        }

        Object resumoAttr = model.getAttribute("resumoPendenciasPagamento");
        if (resumoAttr instanceof ResumoPendenciasPagamentoView resumo && resumo.quantidade() > 0) {
            return resumo.quantidade();
        }

        pagamentoConsultaService.adicionarResumoPendenciasPagamentoAoModel(model, usuario, session);
        resumoAttr = model.getAttribute("resumoPendenciasPagamento");
        if (resumoAttr instanceof ResumoPendenciasPagamentoView resumoAtualizado) {
            return resumoAtualizado.quantidade();
        }
        return 0;
    }

    private static String resolverPerfilRotulo(boolean isAdmin, boolean isDonaClinica) {
        if (isAdmin) {
            return "Administração";
        }
        if (isDonaClinica) {
            return "Dona da clínica";
        }
        return "Profissional";
    }
}
