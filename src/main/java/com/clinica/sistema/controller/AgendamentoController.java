package com.clinica.sistema.controller;

import com.clinica.sistema.config.ManualProperties;
import com.clinica.sistema.config.SiteProperties;
import com.clinica.sistema.config.StartupDataInitializer;
import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.NovidadeSiteView;
import com.clinica.sistema.dto.RelocacaoAgendamentoForm;
import com.clinica.sistema.dto.AtualizarPeriodicidadeForm;
import com.clinica.sistema.dto.ResumoPendenciasPagamentoView;
import com.clinica.sistema.dto.ResultadoAtualizacaoValoresConsulta;
import com.clinica.sistema.dto.AtualizarValoresConsultaProfissionalForm;
import com.clinica.sistema.dto.CadastroProfissionalForm;
import com.clinica.sistema.dto.EditarProfissionalForm;
import com.clinica.sistema.dto.AvisoWhatsappPeriodicidadePainelView;
import com.clinica.sistema.model.AvisoPagamentoEmailJanela;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.dto.TrocarSenhaAdminForm;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AgendamentoService;
import com.clinica.sistema.service.BoasVindasLoginService;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.EncerramentoSerieNotificacaoService;
import com.clinica.sistema.service.NovoAgendamentoNotificacaoService;
import com.clinica.sistema.service.FinanceiroPolyanaAcessoService;
import com.clinica.sistema.service.NovidadeSiteService;
import com.clinica.sistema.service.IndicacaoReservaService;
import com.clinica.sistema.service.PagamentoConsultaService;
import com.clinica.sistema.service.RelatorioMensalService;
import com.clinica.sistema.service.RelatorioSemanalService;
import com.clinica.sistema.service.RelatorioUsoSitePdfService;
import com.clinica.sistema.service.RelatorioUsoSiteService;
import com.clinica.sistema.service.RelatorioUsoSiteSessaoService;
import com.clinica.sistema.service.TaxaSalaService;
import com.clinica.sistema.service.AvisoPagamentoEmailService;
import com.clinica.sistema.service.WhatsAppAvisoPagamentoService;
import com.clinica.sistema.service.WhatsAppMensagemPagamentoService;
import com.clinica.sistema.service.WhatsAppNotificacaoService;
import com.clinica.sistema.service.UsuarioService;
import com.clinica.sistema.util.MoedaBrasilUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/agendamentos")
public class AgendamentoController {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoController.class);

    private final AgendamentoService service;
    private final AuthService authService;
    private final StartupDataInitializer startupDataInitializer;
    private final UsuarioService usuarioService;
    private final RelatorioSemanalService relatorioSemanalService;
    private final RelatorioMensalService relatorioMensalService;
    private final EncerramentoSerieNotificacaoService encerramentoSerieNotificacaoService;
    private final NovoAgendamentoNotificacaoService novoAgendamentoNotificacaoService;
    private final PagamentoConsultaService pagamentoConsultaService;
    private final IndicacaoReservaService indicacaoReservaService;
    private final FinanceiroPolyanaAcessoService financeiroPolyanaAcessoService;
    private final ManualProperties manualProperties;
    private final SiteProperties siteProperties;
    private final NovidadeSiteService novidadeSiteService;
    private final RelatorioUsoSiteService relatorioUsoSiteService;
    private final RelatorioUsoSiteSessaoService relatorioUsoSiteSessaoService;
    private final RelatorioUsoSitePdfService relatorioUsoSitePdfService;
    private final TaxaSalaService taxaSalaService;
    private final WhatsAppNotificacaoService whatsAppNotificacaoService;
    private final WhatsAppMensagemPagamentoService whatsAppMensagemPagamentoService;
    private final WhatsAppAvisoPagamentoService whatsAppAvisoPagamentoService;
    private final AvisoPagamentoEmailService avisoPagamentoEmailService;
    private final BoasVindasLoginService boasVindasLoginService;

    public AgendamentoController(
            AgendamentoService service,
            AuthService authService,
            StartupDataInitializer startupDataInitializer,
            UsuarioService usuarioService,
            RelatorioSemanalService relatorioSemanalService,
            RelatorioMensalService relatorioMensalService,
            EncerramentoSerieNotificacaoService encerramentoSerieNotificacaoService,
            NovoAgendamentoNotificacaoService novoAgendamentoNotificacaoService,
            PagamentoConsultaService pagamentoConsultaService,
            IndicacaoReservaService indicacaoReservaService,
            FinanceiroPolyanaAcessoService financeiroPolyanaAcessoService,
            ManualProperties manualProperties,
            SiteProperties siteProperties,
            NovidadeSiteService novidadeSiteService,
            RelatorioUsoSiteService relatorioUsoSiteService,
            RelatorioUsoSiteSessaoService relatorioUsoSiteSessaoService,
            RelatorioUsoSitePdfService relatorioUsoSitePdfService,
            TaxaSalaService taxaSalaService,
            WhatsAppNotificacaoService whatsAppNotificacaoService,
            WhatsAppMensagemPagamentoService whatsAppMensagemPagamentoService,
            WhatsAppAvisoPagamentoService whatsAppAvisoPagamentoService,
            AvisoPagamentoEmailService avisoPagamentoEmailService,
            BoasVindasLoginService boasVindasLoginService
    ) {
        this.service = service;
        this.authService = authService;
        this.startupDataInitializer = startupDataInitializer;
        this.usuarioService = usuarioService;
        this.relatorioSemanalService = relatorioSemanalService;
        this.relatorioMensalService = relatorioMensalService;
        this.encerramentoSerieNotificacaoService = encerramentoSerieNotificacaoService;
        this.novoAgendamentoNotificacaoService = novoAgendamentoNotificacaoService;
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.indicacaoReservaService = indicacaoReservaService;
        this.financeiroPolyanaAcessoService = financeiroPolyanaAcessoService;
        this.manualProperties = manualProperties;
        this.siteProperties = siteProperties;
        this.novidadeSiteService = novidadeSiteService;
        this.relatorioUsoSiteService = relatorioUsoSiteService;
        this.relatorioUsoSiteSessaoService = relatorioUsoSiteSessaoService;
        this.relatorioUsoSitePdfService = relatorioUsoSitePdfService;
        this.taxaSalaService = taxaSalaService;
        this.whatsAppNotificacaoService = whatsAppNotificacaoService;
        this.whatsAppMensagemPagamentoService = whatsAppMensagemPagamentoService;
        this.whatsAppAvisoPagamentoService = whatsAppAvisoPagamentoService;
        this.avisoPagamentoEmailService = avisoPagamentoEmailService;
        this.boasVindasLoginService = boasVindasLoginService;
    }

    @ModelAttribute("gradeAcoesPorId")
    public Map<Long, String> gradeAcoesPorIdPadrao() {
        return Collections.emptyMap();
    }

    @ModelAttribute("valoresConsultaPorProfissionalJson")
    public String valoresConsultaPorProfissionalJson() {
        return usuarioService.jsonValoresConsultaPadraoPorProfissional();
    }

    @ModelAttribute("taxaSala4Valor")
    public BigDecimal taxaSala4Valor() {
        return taxaSalaService.taxaClinicaSala4();
    }

    @ModelAttribute("sala4Id")
    public Long sala4Id() {
        return taxaSalaService.idSala4();
    }

    @ModelAttribute
    public void prepararTaxasSalaReferenciaLogado(Model model) {
        if (model.containsAttribute("taxasSalaReferenciaLogado")) {
            return;
        }
        authService.buscarUsuarioLogado().ifPresent(usuario -> {
            if (!authService.podeGerenciarEquipe(usuario)
                    && !authService.profissionalIgnoraValoresEPagamento(usuario)) {
                model.addAttribute(
                        "taxasSalaReferenciaLogado",
                        usuarioService.taxasSalaReferenciaProfissional(usuario)
                );
            }
        });
    }

    @ModelAttribute
    public void prepararFlagsValoresConsulta(Model model) {
        if (!model.containsAttribute("podeGerenciarValoresConsulta")) {
            model.addAttribute("podeGerenciarValoresConsulta", false);
        }
        if (!model.containsAttribute("relatorioUsoSiteGerado")) {
            model.addAttribute("relatorioUsoSiteGerado", Boolean.FALSE);
        }
        if (!model.containsAttribute("relatorioUsoSite")) {
            model.addAttribute("relatorioUsoSite", null);
        }
        if (!model.containsAttribute("resumoPendenciasPagamento")) {
            model.addAttribute("resumoPendenciasPagamento", ResumoPendenciasPagamentoView.vazio());
        }
        if (!model.containsAttribute("exibirModalPendenciasPagamento")) {
            model.addAttribute("exibirModalPendenciasPagamento", false);
        }
        if (!model.containsAttribute("exibirModalCadastroContato")) {
            model.addAttribute("exibirModalCadastroContato", false);
        }
        if (!model.containsAttribute("precisaCadastrarEmailNoContato")) {
            model.addAttribute("precisaCadastrarEmailNoContato", false);
        }
        if (!model.containsAttribute("precisaCadastrarWhatsappNoContato")) {
            model.addAttribute("precisaCadastrarWhatsappNoContato", false);
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
        if (!model.containsAttribute("podeCadastrarEmailNotificacao")) {
            model.addAttribute("podeCadastrarEmailNotificacao", false);
        }
        if (!model.containsAttribute("podeCadastrarTelefoneWhatsapp")) {
            model.addAttribute("podeCadastrarTelefoneWhatsapp", false);
        }
    }

    @ModelAttribute
    public void prepararResumoPendenciasPagamentoAgenda(Model model, HttpSession session) {
        if (model.containsAttribute("resumoPendenciasPagamento")
                && model.getAttribute("resumoPendenciasPagamento") instanceof ResumoPendenciasPagamentoView resumo
                && resumo.quantidade() > 0) {
            return;
        }
        authService.buscarUsuarioLogado().ifPresent(usuario -> {
            if (!authService.isAdmin(usuario) && !authService.isDonaClinica(usuario)) {
                pagamentoConsultaService.adicionarResumoPendenciasPagamentoAoModel(model, usuario, session);
                Object resumoAttr = model.getAttribute("resumoPendenciasPagamento");
                int total = resumoAttr instanceof ResumoPendenciasPagamentoView r ? r.quantidade() : 0;
                model.addAttribute("totalMeusPagamentosPendentes", total);
            }
        });
    }

    @ModelAttribute
    public void prepararAjudaSuporte(Model model) {
        model.addAttribute("manualWhatsappAtivo", manualProperties.temWhatsappSuporte());
        model.addAttribute("manualWhatsappUrl", manualProperties.resolverLinkWhatsapp());
        model.addAttribute("versaoSite", siteProperties.getVersion());
        model.addAttribute("versaoSiteRotulo", siteProperties.getRotuloExibicao());
    }

    @ModelAttribute
    public void prepararNotificacaoRelatorioMensal(Model model, HttpSession session) {
        authService.buscarUsuarioLogado().ifPresentOrElse(
                usuario -> {
                    if (authService.podeGerenciarEquipe(usuario)) {
                        relatorioMensalService.adicionarNotificacaoAoModelSeAplicavel(model, session);
                    } else {
                        model.addAttribute("notificacaoRelatorioMensal", null);
                        model.addAttribute("exibirBolinhaNotificacaoRelatorio", false);
                    }
                    if (authService.podeAcessarCentralProfissionais(usuario)) {
                        encerramentoSerieNotificacaoService.adicionarNotificacaoAoModelSeAplicavel(model, session);
                        novoAgendamentoNotificacaoService.adicionarNotificacaoAoModelSeAplicavel(model, session);
                    } else {
                        model.addAttribute("notificacaoEncerramentoSerie", null);
                        model.addAttribute("exibirBolinhaNotificacaoEncerramento", false);
                        model.addAttribute("notificacoesNovosAgendamentos", List.of());
                        model.addAttribute("totalNotificacoesNovosAgendamentos", 0L);
                        model.addAttribute("exibirBolinhaNotificacaoNovoAgendamento", false);
                    }
                    if (!authService.isAdmin(usuario)
                            && !authService.isDonaClinica(usuario)
                            && !authService.profissionalIgnoraValoresEPagamento(usuario)) {
                        pagamentoConsultaService.adicionarNotificacaoPagamentoAoModelSeAplicavel(model, usuario);
                    } else {
                        model.addAttribute("notificacaoPagamentoProfissional", null);
                        model.addAttribute("exibirBolinhaNotificacaoPagamento", false);
                    }
                },
                () -> {
                    model.addAttribute("notificacaoRelatorioMensal", null);
                    model.addAttribute("exibirBolinhaNotificacaoRelatorio", false);
                    model.addAttribute("notificacaoEncerramentoSerie", null);
                    model.addAttribute("exibirBolinhaNotificacaoEncerramento", false);
                    model.addAttribute("notificacoesNovosAgendamentos", List.of());
                    model.addAttribute("totalNotificacoesNovosAgendamentos", 0L);
                    model.addAttribute("exibirBolinhaNotificacaoNovoAgendamento", false);
                    model.addAttribute("notificacaoPagamentoProfissional", null);
                    model.addAttribute("exibirBolinhaNotificacaoPagamento", false);
                }
        );
    }

    @GetMapping("/dashboard")
    public String abrirDashboard(
            Model model,
            @RequestParam(required = false) Long salaId,
            @RequestParam(required = false) LocalDate semana,
            @RequestParam(required = false) Long pagamentoId,
            @RequestParam(required = false) Long abrirRemarcarMensal,
            @RequestParam(required = false, defaultValue = "false") boolean secaoMensal,
            @RequestParam(required = false, defaultValue = "false") boolean viaNotificacaoNovoAgendamento,
            @RequestParam(required = false, defaultValue = "false") boolean alterarSenha,
            @RequestParam(required = false, defaultValue = "todos") String acompProfissional,
            @RequestParam(required = false, defaultValue = "hoje") String acompPeriodo,
            @RequestParam(required = false, defaultValue = "todos") String acompRecorrencia,
            HttpSession session
    ) {
        relatorioSemanalService.limparSessao(session);
        if (authService.buscarUsuarioLogado().isEmpty()) {
            return "redirect:/login";
        }
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        service.renovarSeriesRecorrentesAtivasSeNecessario();

        if (viaNotificacaoNovoAgendamento && authService.podeAcessarCentralProfissionais(usuarioLogado)) {
            novoAgendamentoNotificacaoService.marcarComoVisto(session);
            model.addAttribute("notificacoesNovosAgendamentos", List.of());
            model.addAttribute("totalNotificacoesNovosAgendamentos", 0L);
            model.addAttribute("exibirBolinhaNotificacaoNovoAgendamento", false);
        }

        boolean isAdmin = authService.isAdmin(usuarioLogado);
        boolean isDonaClinica = authService.isDonaClinica(usuarioLogado);
        boolean podeTrocarPropriaSenha = authService.podeTrocarPropriaSenha(usuarioLogado);

        boolean podeGerenciarEquipe = authService.podeGerenciarEquipe(usuarioLogado);
        if (!model.containsAttribute("agendamentoForm")) {
            AgendamentoForm form = new AgendamentoForm();
            if (!podeGerenciarEquipe) {
                form.setProfissionalId(usuarioLogado.getId());
            }
            LocalDate dataSugerida = agendaDataSugerida(semana);
            form.setDataAtendimento(dataSugerida);
            form.setHorarioAtendimento(service.listarHorariosDisponiveis().get(0));
            if (!podeGerenciarEquipe) {
                usuarioService.preencherTaxaSalaPadraoNoForm(form, usuarioLogado.getId(), "AVULSO");
            }
            model.addAttribute("agendamentoForm", form);
        }
        if (podeTrocarPropriaSenha && !model.containsAttribute("trocarSenhaForm")) {
            model.addAttribute("trocarSenhaForm", new com.clinica.sistema.dto.TrocarSenhaForm());
        }
        if (model.containsAttribute("trocarSenhaForm")) {
            Object formFlash = model.getAttribute("trocarSenhaForm");
            if (formFlash instanceof com.clinica.sistema.dto.TrocarSenhaForm form) {
                form.setSenhaAtual("");
                model.addAttribute("trocarSenhaForm", form);
            }
        }

        List<com.clinica.sistema.model.Agendamento> agendamentos = service.buscarParaUsuario(usuarioLogado);
        List<com.clinica.sistema.model.Agendamento> agendamentosAvulsos =
                service.listarProximosPorSerie(
                        agendamentos,
                        com.clinica.sistema.model.Agendamento::isAvulsoSemMensal
                );
        List<com.clinica.sistema.dto.MensalAgendamentoLinha> linhasMensaisResumo =
                service.agruparMensaisAtivos(agendamentos, usuarioLogado);
        List<com.clinica.sistema.model.Agendamento> agendamentosFixos =
                service.listarProximasOcorrencias(agendamentos, com.clinica.sistema.model.Agendamento::isFixoSemanal);
        List<com.clinica.sistema.model.Agendamento> agendamentosQuinzenais =
                service.listarProximasOcorrencias(agendamentos, com.clinica.sistema.model.Agendamento::isQuinzenal);

        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", isAdmin);
        boolean reabrirModalTrocarSenha = model.containsAttribute("reabrirModalTrocarSenha");
        boolean exibirModalTrocarSenhaObrigatoria = podeTrocarPropriaSenha
                && usuarioService.exibirModalTrocaSenhaPrimeiroAcesso(
                session,
                reabrirModalTrocarSenha
        );
        boolean trocaSenhaAindaPendente = podeTrocarPropriaSenha
                && usuarioService.usuarioLogadoDeveTrocarSenha()
                && !exibirModalTrocarSenhaObrigatoria;
        if (exibirModalTrocarSenhaObrigatoria) {
            usuarioService.confirmarExibicaoModalTrocaSenha(session);
        }
        model.addAttribute("exibirModalTrocarSenhaObrigatoria", exibirModalTrocarSenhaObrigatoria);
        model.addAttribute("trocaSenhaAindaPendente", trocaSenhaAindaPendente);
        boolean podeCadastrarTelefoneWhatsapp = authService.podeCadastrarProprioTelefoneWhatsapp(usuarioLogado);
        boolean podeCadastrarEmailNotificacao = authService.podeCadastrarEmailNotificacaoPagamento(usuarioLogado);
        boolean precisaCadastrarEmailNoContato = usuarioService.precisaCadastrarEmailNotificacao(usuarioLogado);
        boolean precisaCadastrarWhatsappNoContato = usuarioService.precisaCadastrarTelefoneWhatsapp(usuarioLogado);
        boolean reabrirModalCadastroContato = model.containsAttribute("reabrirModalCadastroContato");
        boolean exibirModalCadastroContato = !exibirModalTrocarSenhaObrigatoria
                && usuarioService.exibirModalCadastroContatoEntrada(
                session,
                reabrirModalCadastroContato,
                usuarioLogado
        );
        if (exibirModalCadastroContato) {
            model.addAttribute("exibirModalPendenciasPagamento", false);
        }
        model.addAttribute("podeCadastrarEmailNotificacao", podeCadastrarEmailNotificacao);
        model.addAttribute("exibirModalCadastroContato", exibirModalCadastroContato);
        model.addAttribute("precisaCadastrarEmailNoContato", precisaCadastrarEmailNoContato);
        model.addAttribute("precisaCadastrarWhatsappNoContato", precisaCadastrarWhatsappNoContato);
        if (podeCadastrarTelefoneWhatsapp && !model.containsAttribute("atualizarTelefoneWhatsappForm")) {
            var contatoForm = new com.clinica.sistema.dto.AtualizarTelefoneWhatsappForm();
            if (usuarioLogado.getEmail() != null && !usuarioLogado.getEmail().isBlank()) {
                contatoForm.setEmail(usuarioLogado.getEmail());
            }
            if (usuarioLogado.getTelefoneWhatsappFormulario() != null
                    && !usuarioLogado.getTelefoneWhatsappFormulario().isBlank()) {
                contatoForm.setTelefoneWhatsapp(usuarioLogado.getTelefoneWhatsappFormulario());
            }
            model.addAttribute("atualizarTelefoneWhatsappForm", contatoForm);
        }
        model.addAttribute("podeCadastrarTelefoneWhatsapp", podeCadastrarTelefoneWhatsapp);
        boolean exibirModalBoasVindasLogin = !exibirModalTrocarSenhaObrigatoria
                && !exibirModalCadastroContato
                && boasVindasLoginService.exibirBoasVindasLoginEntrada(session)
                && boasVindasLoginService.elegivelBoasVindas(usuarioLogado);
        if (exibirModalBoasVindasLogin) {
            model.addAttribute("boasVindasLogin", boasVindasLoginService.montar(usuarioLogado));
            model.addAttribute("exibirModalPendenciasPagamento", false);
        }
        model.addAttribute("exibirModalBoasVindasLogin", exibirModalBoasVindasLogin);
        model.addAttribute("isDonaClinica", isDonaClinica);
        model.addAttribute("podeEscolherFormaPagamento", authService.podeEscolherFormaPagamento(usuarioLogado));
        model.addAttribute("podeTrocarPropriaSenha", podeTrocarPropriaSenha);
        model.addAttribute("abrirModalAlterarSenha", alterarSenha && podeTrocarPropriaSenha);
        model.addAttribute(
                "podeAcessarGestaoFinanceira",
                financeiroPolyanaAcessoService.podeAcessarGestaoFinanceira(usuarioLogado)
        );
        model.addAttribute("podeGerenciarEquipe", podeGerenciarEquipe);
        model.addAttribute("podeVerRelatorioProprio", authService.podeVerRelatorioProprio(usuarioLogado));
        boolean podeAcompanharGanhosConsultaPropria =
                authService.podeAcompanharGanhosConsultaPropria(usuarioLogado);
        model.addAttribute("podeAcompanharGanhosConsultaPropria", podeAcompanharGanhosConsultaPropria);
        model.addAttribute("podeVerValoresDeTodos", podeGerenciarEquipe);
        model.addAttribute(
                "exibirPainelValoresConsulta",
                !authService.profissionalIgnoraValoresEPagamento(usuarioLogado) || podeGerenciarEquipe
        );
        model.addAttribute(
                "clinicaCobraEditavelNaAgenda",
                authService.clinicaCobraEditavelNaAgenda(usuarioLogado)
        );
        model.addAttribute("agendamentos", agendamentos);
        model.addAttribute("agendamentosAvulsos", agendamentosAvulsos);
        model.addAttribute("linhasMensaisResumo", linhasMensaisResumo);
        model.addAttribute("agendamentosFixos", agendamentosFixos);
        model.addAttribute("agendamentosQuinzenais", agendamentosQuinzenais);
        model.addAttribute("totalAgendamentosAvulsos",
                service.contarSeries(
                        agendamentos,
                        com.clinica.sistema.model.Agendamento::isAvulsoSemMensal
                ));
        model.addAttribute("totalAgendamentosFixos",
                service.contarOcorrencias(agendamentos, com.clinica.sistema.model.Agendamento::isFixoSemanal));
        model.addAttribute("totalAgendamentosQuinzenais",
                service.contarOcorrencias(agendamentos, com.clinica.sistema.model.Agendamento::isQuinzenal));

        boolean meusAgendamentosResumido = !isAdmin;
        model.addAttribute("meusAgendamentosResumido", meusAgendamentosResumido);
        if (meusAgendamentosResumido) {
            var seriesFixas = service.agruparSeriesAtivas(
                    agendamentos,
                    com.clinica.sistema.model.Agendamento::isFixoSemanal,
                    usuarioLogado
            );
            var seriesQuinzenais = service.agruparSeriesAtivas(
                    agendamentos,
                    com.clinica.sistema.model.Agendamento::isQuinzenal,
                    usuarioLogado
            );
            model.addAttribute("seriesFixasResumo", seriesFixas);
            model.addAttribute("seriesQuinzenaisResumo", seriesQuinzenais);
            model.addAttribute("totalFixosResumo", seriesFixas.size());
            model.addAttribute("totalQuinzenaisResumo", seriesQuinzenais.size());
            model.addAttribute("totalMensaisResumo", linhasMensaisResumo.size());
        } else {
            model.addAttribute("seriesFixasResumo", Collections.emptyList());
            model.addAttribute("seriesQuinzenaisResumo", Collections.emptyList());
            model.addAttribute("totalFixosResumo", 0);
            model.addAttribute("totalQuinzenaisResumo", 0);
            model.addAttribute("totalMensaisResumo", 0);
            model.addAttribute("linhasMensaisResumo", Collections.emptyList());
        }

        List<Usuario> equipeProfissionais = usuarioService.listarProfissionaisDaEquipe();
        model.addAttribute(
                "idsProfissionaisSemValores",
                equipeProfissionais.stream()
                        .filter(p -> authService.profissionalIgnoraValoresEPagamento(p))
                        .map(Usuario::getId)
                        .toList()
        );
        if (podeGerenciarEquipe) {
            model.addAttribute(
                    "resumosProfissionais",
                    service.montarResumosProfissionais(equipeProfissionais, usuarioLogado)
            );
        } else {
            model.addAttribute("resumosProfissionais", Collections.emptyList());
        }
        model.addAttribute("salas", service.listarSalas());
        model.addAttribute("profissionais", podeGerenciarEquipe
                ? equipeProfissionais
                : List.of(usuarioLogado));
        model.addAttribute("horariosDisponiveis", service.listarHorariosDisponiveis());
        model.addAttribute("turnosLocacao", service.listarTurnosLocacao());
        LocalDate referenciaSemana = agendaDataSugerida(semana);
        java.util.Map<Long, Integer> salasOcupadasNaSemana = service.contarAgendamentosPorSalaNaSemana(referenciaSemana);
        Long salaIdGrade = service.resolverSalaIdParaGrade(salaId, referenciaSemana, salasOcupadasNaSemana);
        Long profissionalIdGrade = usuarioLogado.getId();
        if (model.containsAttribute("agendamentoForm")) {
            Object formAttr = model.getAttribute("agendamentoForm");
            if (formAttr instanceof AgendamentoForm formGrade && formGrade.getProfissionalId() != null) {
                profissionalIdGrade = formGrade.getProfissionalId();
            }
        }
        var agendaSala = service.montarAgendaSala(salaIdGrade, referenciaSemana, profissionalIdGrade);
        Map<Long, String> gradeAcoesPorId = service.montarAcoesGradePorId(agendaSala, usuarioLogado);
        service.mensagemAgendamentosEmOutraSala(agendaSala.getSala().getId(), salasOcupadasNaSemana)
                .ifPresent(msg -> model.addAttribute("avisoAgendamentoOutraSala", msg));
        model.addAttribute("salasOcupadasNaSemana", salasOcupadasNaSemana);
        List<com.clinica.sistema.model.Agendamento> agendamentosDoDia =
                service.listarAgendamentosDoDia(usuarioLogado, isAdmin);
        if (isDonaClinica) {
            var filtroProfissionalAcompanhamento = com.clinica.sistema.dto.AcompanhamentoAgendaFiltros.FiltroProfissional
                    .fromParam(acompProfissional);
            var periodoAcompanhamento = com.clinica.sistema.dto.AcompanhamentoAgendaFiltros.Periodo
                    .fromParam(acompPeriodo);
            var recorrenciaAcompanhamento = com.clinica.sistema.dto.AcompanhamentoAgendaFiltros.RecorrenciaConsulta
                    .fromParam(acompRecorrencia);
            var intervaloAcompanhamento = com.clinica.sistema.dto.AcompanhamentoAgendaFiltros.resolverIntervalo(
                    periodoAcompanhamento,
                    referenciaSemana
            );
            List<com.clinica.sistema.model.Agendamento> agendamentosAcompanhamento = service.listarAgendamentosAcompanhamento(
                    filtroProfissionalAcompanhamento,
                    intervaloAcompanhamento,
                    recorrenciaAcompanhamento
            );
            model.addAttribute("exibirPainelAcompanhamentoAgenda", true);
            model.addAttribute("profissionaisAcompanhamento", equipeProfissionais);
            model.addAttribute("agendamentosAcompanhamento", agendamentosAcompanhamento);
            model.addAttribute("totalAgendamentosAcompanhamento", agendamentosAcompanhamento.size());
            model.addAttribute("acompanhamentoFiltroProfissional", filtroProfissionalAcompanhamento);
            model.addAttribute("acompanhamentoPeriodo", periodoAcompanhamento);
            model.addAttribute("acompanhamentoRecorrencia", recorrenciaAcompanhamento);
            model.addAttribute("acompanhamentoIntervaloRotulo", intervaloAcompanhamento.rotulo());
            model.addAttribute("acompanhamentoProfissionalParam", filtroProfissionalAcompanhamento.parametroUrl());
            model.addAttribute("acompanhamentoPeriodoParam", periodoAcompanhamento.getParam());
            model.addAttribute("acompanhamentoRecorrenciaParam", recorrenciaAcompanhamento.getParam());
        } else {
            model.addAttribute("exibirPainelAcompanhamentoAgenda", false);
        }
        model.addAttribute("agendaSala", agendaSala);
        model.addAttribute("agendamentosDoDia", agendamentosDoDia);
        model.addAttribute("dataAgendaDia", LocalDate.now());
        model.addAttribute("totalAgendamentosDoDia", agendamentosDoDia.size());
        model.addAttribute("gradeAcoesPorId", gradeAcoesPorId != null ? gradeAcoesPorId : Collections.emptyMap());
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("agendamentoService", service);
        model.addAttribute("abrirRemarcarMensal", abrirRemarcarMensal);
        model.addAttribute("secaoMensal", secaoMensal);
        model.addAttribute("periodicidadePagamento", pagamentoConsultaService.resolverPeriodicidade(usuarioLogado));
        model.addAttribute("periodicidadesPagamento", PeriodicidadePagamento.values());
        popularControlePeriodicidadePropria(model, usuarioLogado);
        var pendenciasBloqueioPagamento = pagamentoConsultaService.listarPendenciasObrigatoriasParaBloqueio(usuarioLogado);
        pagamentoConsultaService.adicionarResumoPendenciasPagamentoAoModel(model, usuarioLogado, session);
        if (Boolean.TRUE.equals(model.getAttribute("exibirModalCadastroContato"))) {
            model.addAttribute("exibirModalPendenciasPagamento", false);
        }
        model.addAttribute("pagamentoBloqueioAtivo", !pendenciasBloqueioPagamento.isEmpty());
        model.addAttribute(
                "pagamentoBloqueioAgendamentoId",
                pendenciasBloqueioPagamento.isEmpty() ? null : pendenciasBloqueioPagamento.get(0).getId()
        );
        model.addAttribute(
                "pagamentoBloqueioMensagem",
                pagamentoConsultaService.mensagemBloqueioPagamento(usuarioLogado)
        );
        Object resumoAttr = model.getAttribute("resumoPendenciasPagamento");
        int totalPendentes = resumoAttr instanceof com.clinica.sistema.dto.ResumoPendenciasPagamentoView resumo
                ? resumo.quantidade()
                : 0;
        model.addAttribute("totalMeusPagamentosPendentes", totalPendentes);
        model.addAttribute(
                "pagamentosAguardandoQr",
                pagamentoConsultaService.listarAguardandoConfirmacao(usuarioLogado, podeGerenciarEquipe)
        );
        Object pagamentoFlashId = model.containsAttribute("pagamentoAgendamentoId")
                ? model.getAttribute("pagamentoAgendamentoId")
                : null;
        Long pagamentoSelecionadoId = pagamentoId != null ? pagamentoId : extrairIdPagamento(pagamentoFlashId);
        if (pagamentoSelecionadoId != null) {
            service.buscarPorId(pagamentoSelecionadoId)
                    .ifPresent(ag -> {
                        model.addAttribute("pagamentoAgendamento", ag);
                        model.addAttribute("exibirModalPendenciasPagamento", false);
                    });
        }
        aplicarModalPixConfirmadoSeNecessario(model);
        popularNovidades(model, usuarioLogado, session);
        aplicarValorPadraoAgendamentoSeNecessario(model, usuarioLogado, podeGerenciarEquipe);
        if (Boolean.TRUE.equals(model.getAttribute("exibirModalBoasVindasLogin"))) {
            boolean pendenciasDepois = Boolean.TRUE.equals(model.getAttribute("exibirModalPendenciasPagamento"));
            boolean novidadesDepois = Boolean.TRUE.equals(model.getAttribute("exibirModalNovidades"));
            model.addAttribute("pendenciasPagamentoDepoisBoasVindas", pendenciasDepois);
            model.addAttribute("novidadesDepoisBoasVindas", novidadesDepois);
            model.addAttribute("exibirModalPendenciasPagamento", false);
            model.addAttribute("exibirModalNovidades", false);
        } else {
            model.addAttribute("pendenciasPagamentoDepoisBoasVindas", false);
            model.addAttribute("novidadesDepoisBoasVindas", false);
        }
        return "agenda";
    }

    private void aplicarValorPadraoAgendamentoSeNecessario(
            Model model,
            Usuario usuarioLogado,
            boolean podeGerenciarEquipe
    ) {
        Object attr = model.getAttribute("agendamentoForm");
        if (!(attr instanceof AgendamentoForm form)) {
            return;
        }
        Long profissionalId = form.getProfissionalId();
        if (profissionalId == null && !podeGerenciarEquipe) {
            profissionalId = usuarioLogado.getId();
        }
        if (profissionalId == null) {
            return;
        }
        String recorrencia = form.getRecorrencia() != null && !form.getRecorrencia().isBlank()
                ? form.getRecorrencia()
                : "AVULSO";
        usuarioService.preencherTaxaSalaPadraoNoForm(form, profissionalId, recorrencia);
    }

    private void popularNovidades(Model model, Usuario usuarioLogado, HttpSession session) {
        List<NovidadeSiteView> novidadesVisiveis = novidadeSiteService.listarVisiveisParaUsuario(usuarioLogado);
        boolean modalFechadoNaSessao = Boolean.TRUE.equals(session.getAttribute("novidadesModalFechado"));
        model.addAttribute("novidadesVisiveis", novidadesVisiveis);
        model.addAttribute("totalNovidadesVisiveis", novidadesVisiveis.size());
        model.addAttribute("exibirModalNovidades", !novidadesVisiveis.isEmpty() && !modalFechadoNaSessao);
        model.addAttribute("novidadesDiasExibicao", NovidadeSiteService.DIAS_EXIBICAO_SEM_MARCAR);
    }

    @PostMapping("/novidades/dispensar")
    public String dispensarNovidades(
            @RequestParam(required = false) List<Long> novidadeIds,
            @RequestParam(defaultValue = "false") boolean naoMostrarMais,
            @RequestParam(defaultValue = "/agendamentos/dashboard") String retorno,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            if (novidadeIds != null && !novidadeIds.isEmpty()) {
                novidadeSiteService.dispensarNovidades(usuarioLogado, novidadeIds, naoMostrarMais);
            }
            session.setAttribute("novidadesModalFechado", Boolean.TRUE);
            if (naoMostrarMais) {
                redirectAttributes.addFlashAttribute("sucesso", "Novidades ocultadas.");
            }
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:" + normalizarRetornoNovidades(retorno);
    }

    private String normalizarRetornoNovidades(String retorno) {
        if (retorno == null || retorno.isBlank()) {
            return "/agendamentos/dashboard";
        }
        if (retorno.startsWith("/agendamentos/")) {
            return retorno;
        }
        return "/agendamentos/dashboard";
    }

    private void aplicarModalPixConfirmadoSeNecessario(Model model) {
        if (!model.containsAttribute("exibirModalPixConfirmado")) {
            return;
        }
        Object idFlash = model.getAttribute("pixConfirmadoAgendamentoId");
        Long id = extrairIdPagamento(idFlash);
        if (id != null) {
            service.buscarPorId(id).ifPresent(ag -> model.addAttribute("pixConfirmadoAgendamento", ag));
        }
    }

    private Long extrairIdPagamento(Object pagamentoFlashId) {
        if (pagamentoFlashId == null) {
            return null;
        }
        if (pagamentoFlashId instanceof Number numero) {
            return numero.longValue();
        }
        if (pagamentoFlashId instanceof String texto && !texto.isBlank()) {
            try {
                return Long.parseLong(texto.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @GetMapping("/central-profissionais")
    public String abrirCentralProfissionais(
            @RequestParam(name = "aba", required = false, defaultValue = "equipe") String aba,
            @RequestParam(name = "viaNotificacaoEncerramento", required = false, defaultValue = "false")
            boolean viaNotificacaoEncerramento,
            Model model,
            HttpSession session
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeAcessarCentralProfissionais(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }

        if (!model.containsAttribute("cadastroProfissionalForm")) {
            model.addAttribute("cadastroProfissionalForm", new CadastroProfissionalForm());
        }
        if (!model.containsAttribute("trocarSenhaAdminForm")) {
            model.addAttribute("trocarSenhaAdminForm", new TrocarSenhaAdminForm());
        }
        if (!model.containsAttribute("editarProfissionalForm")) {
            model.addAttribute("editarProfissionalForm", new EditarProfissionalForm());
        }

        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", authService.isAdmin(usuarioLogado));
        model.addAttribute("isDonaClinica", authService.isDonaClinica(usuarioLogado));
        List<Usuario> profissionais = usuarioService.listarProfissionaisDaEquipe();
        model.addAttribute("profissionais", profissionais);
        model.addAttribute("fotosPerfilEquipe", usuarioService.mapaFotosPerfil(profissionais));
        model.addAttribute("usuariosSenha", usuarioService.listarUsuariosParaTrocaSenha());
        model.addAttribute("periodicidadesPagamento", PeriodicidadePagamento.values());
        boolean podeVerRelatorioUsoSite = authService.podeVerRelatorioUsoSite(usuarioLogado);
        boolean podeGerenciarValoresConsulta = authService.podeGerenciarValoresConsultaProfissionais(usuarioLogado);
        String abaSolicitada = aba != null ? aba.toLowerCase() : "equipe";
        if ("uso-site".equals(abaSolicitada) && !podeVerRelatorioUsoSite) {
            abaSolicitada = "equipe";
        }
        if ("valores".equals(abaSolicitada) && !podeGerenciarValoresConsulta) {
            abaSolicitada = "equipe";
        }
        String abaAtiva = switch (abaSolicitada) {
            case "configuracao" -> "configuracao";
            case "encerramentos" -> "encerramentos";
            case "indicacoes" -> "indicacoes";
            case "mensagem" -> "mensagem";
            case "uso-site" -> "uso-site";
            case "valores" -> "valores";
            default -> "equipe";
        };
        if (!"uso-site".equals(abaAtiva)) {
            relatorioUsoSiteSessaoService.limpar(session);
        }
        model.addAttribute("abaAtiva", abaAtiva);
        model.addAttribute("podeVerRelatorioUsoSite", podeVerRelatorioUsoSite);
        model.addAttribute("podeGerenciarValoresConsulta", podeGerenciarValoresConsulta);
        model.addAttribute("whatsappMetaAtivo", whatsAppNotificacaoService.ativo());
        var secoesAvisoWhatsapp = avisoPagamentoEmailService.montarPainelCentral();
        model.addAttribute("secoesAvisoWhatsapp", secoesAvisoWhatsapp);
        model.addAttribute("msgWhatsappGeral", whatsAppMensagemPagamentoService.resolverTextoGeral());
        model.addAttribute("avisoPagamentoAutomaticoAtivo", avisoPagamentoEmailService.avisoAutomaticoAtivo());
        model.addAttribute("avisoPagamentoPorEmail", true);
        int totalProfissionaisWhatsapp = secoesAvisoWhatsapp.stream()
                .mapToInt(secao -> secao.profissionais().size())
                .sum();
        int totalProfissionaisPendenciaWhatsapp = secoesAvisoWhatsapp.stream()
                .mapToInt(AvisoWhatsappPeriodicidadePainelView::quantidadeComPendencia)
                .sum();
        model.addAttribute("totalProfissionaisWhatsappCentral", totalProfissionaisWhatsapp);
        model.addAttribute("totalProfissionaisPendenciaWhatsapp", totalProfissionaisPendenciaWhatsapp);
        if (podeGerenciarValoresConsulta) {
            model.addAttribute(
                    "profissionaisValoresConsulta",
                    usuarioService.listarProfissionaisParaGestaoValoresConsulta()
            );
            model.addAttribute("taxaSala4Input", taxaSalaService.taxaClinicaSala4Input());
        } else {
            model.addAttribute("profissionaisValoresConsulta", List.of());
        }
        if ("uso-site".equals(abaAtiva)) {
            model.addAttribute("relatorioUsoSite", relatorioUsoSiteSessaoService.obter(session).orElse(null));
            model.addAttribute("relatorioUsoSiteGerado", relatorioUsoSiteSessaoService.possuiRelatorioGerado(session));
            model.addAttribute("versaoDownload", System.currentTimeMillis());
        } else {
            model.addAttribute("relatorioUsoSite", null);
            model.addAttribute("relatorioUsoSiteGerado", false);
        }
        model.addAttribute("encerramentosSerie", service.listarEncerramentosSerieRecentes());
        model.addAttribute("indicacoesPendentes", indicacaoReservaService.listarAguardandoAprovacao());
        model.addAttribute("totalIndicacoesPendentes", indicacaoReservaService.contarAguardandoAprovacao());
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        if ("encerramentos".equals(abaAtiva) || viaNotificacaoEncerramento) {
            encerramentoSerieNotificacaoService.marcarComoVisto(session);
            model.addAttribute("notificacaoEncerramentoSerie", null);
            model.addAttribute("exibirBolinhaNotificacaoEncerramento", false);
        }
        return "central-profissionais";
    }

    @PostMapping("/central-profissionais/uso-site/gerar")
    public String gerarRelatorioUsoSiteCentral(
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeVerRelatorioUsoSite(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }
        try {
            relatorioUsoSiteSessaoService.armazenar(session, relatorioUsoSiteService.montarRelatorio());
            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    "Relatorio gerado. Ao sair desta aba os dados sao removidos da sessao."
            );
        } catch (RuntimeException e) {
            log.error("Falha ao gerar relatorio de uso do site na central", e);
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Nao foi possivel gerar o relatorio. Tente novamente em alguns minutos."
            );
        }
        return "redirect:/agendamentos/central-profissionais?aba=uso-site";
    }

    @PostMapping("/central-profissionais/mensagem/salvar")
    public ResponseEntity<Void> salvarMensagemWhatsappCentral(@RequestParam String texto) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeAcessarCentralProfissionais(usuarioLogado)) {
            return ResponseEntity.status(403).build();
        }
        whatsAppMensagemPagamentoService.salvarTextoGeral(texto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/central-profissionais/mensagem/abrir-whatsapp")
    public String abrirWhatsappMensagemCentral(
            @RequestParam(required = false) Long profissionalId,
            @RequestParam(required = false) String telefone,
            @RequestParam(required = false) String nomeProfissional,
            @RequestParam(required = false) PeriodicidadePagamento periodicidade,
            @RequestParam(required = false) String textoMensagem,
            @RequestParam(required = false) String valorTotal,
            RedirectAttributes redirectAttributes
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeAcessarCentralProfissionais(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }
        redirectAttributes.addFlashAttribute("mensagemTesteTelefone", telefone);
        redirectAttributes.addFlashAttribute("mensagemTesteNome", nomeProfissional);
        if (periodicidade != null) {
            redirectAttributes.addFlashAttribute("mensagemTestePeriodicidade", periodicidade.name());
        }
        if (textoMensagem != null && !textoMensagem.isBlank()) {
            redirectAttributes.addFlashAttribute("mensagemTextoEditado", textoMensagem);
            if (periodicidade != null) {
                redirectAttributes.addFlashAttribute(
                        "mensagemTexto" + periodicidade.name(),
                        textoMensagem
                );
            }
        }
        try {
            String url = montarUrlWhatsappMensagemCentral(
                    profissionalId,
                    telefone,
                    nomeProfissional,
                    periodicidade,
                    textoMensagem,
                    valorTotal
            );
            return "redirect:" + url;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
            return "redirect:/agendamentos/central-profissionais?aba=mensagem";
        }
    }

    private String montarUrlWhatsappMensagemCentral(
            Long profissionalId,
            String telefone,
            String nomeProfissional,
            PeriodicidadePagamento periodicidade,
            String textoMensagem,
            String valorTotal
    ) {
        String preview;
        String numeroDestino;
        String nome = nomeProfissional;
        String total = valorTotal;
        if (profissionalId != null) {
            Usuario profissional = pagamentoConsultaService.buscarProfissionalParaAvisoWhatsapp(profissionalId);
            numeroDestino = profissional.getTelefoneWhatsappFormulario();
            if (numeroDestino == null || numeroDestino.isBlank()) {
                throw new RuntimeException(
                        "Cadastre o WhatsApp do profissional na aba Equipe antes de enviar o aviso."
                );
            }
            nome = profissional.getNome();
            if (total == null || total.isBlank()) {
                total = pagamentoConsultaService.montarResumoPendenciasPagamento(profissional).valorTotalFormatado();
            }
            if (textoMensagem != null && !textoMensagem.isBlank()) {
                preview = pagamentoConsultaService.aplicarVariaveisFraseWhatsapp(textoMensagem, nome, total);
            } else {
                preview = pagamentoConsultaService.aplicarVariaveisFraseWhatsapp(
                        whatsAppMensagemPagamentoService.resolverTextoGeral(),
                        nome,
                        total
                );
            }
        } else {
            if (telefone == null || telefone.isBlank()) {
                throw new RuntimeException("Informe o WhatsApp do destinatario.");
            }
            numeroDestino = telefone;
            String totalTeste = total != null && !total.isBlank() ? total : "R$ 50,00";
            if (textoMensagem != null && !textoMensagem.isBlank()) {
                preview = pagamentoConsultaService.aplicarVariaveisFraseWhatsapp(
                        textoMensagem,
                        nomeProfissional,
                        totalTeste
                );
            } else {
                preview = pagamentoConsultaService.aplicarVariaveisFraseWhatsapp(
                        whatsAppMensagemPagamentoService.resolverTextoGeral(),
                        nomeProfissional,
                        totalTeste
                );
            }
        }
        return whatsAppNotificacaoService.urlWaMe(numeroDestino, preview)
                .orElseThrow(() -> new RuntimeException(
                        "WhatsApp invalido. Use DDD + numero (ex.: 31999887766)."
                ));
    }

    @PostMapping("/central-profissionais/mensagem/enviar-email")
    public String enviarMensagemEmailCentral(
            @RequestParam Long profissionalId,
            @RequestParam(required = false) PeriodicidadePagamento periodicidade,
            RedirectAttributes redirectAttributes
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeAcessarCentralProfissionais(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }
        try {
            AvisoPagamentoEmailJanela janela = periodicidade != null
                    ? switch (periodicidade) {
                        case DIARIO -> AvisoPagamentoEmailJanela.DIARIO_MANHA;
                        case SEMANAL -> AvisoPagamentoEmailJanela.SEMANAL_SABADO_TARDE;
                        case MENSAL -> AvisoPagamentoEmailJanela.MENSAL_DIA1;
                    }
                    : null;
            avisoPagamentoEmailService.enviarManualProfissional(profissionalId, janela);
            redirectAttributes.addFlashAttribute("sucesso", "E-mail de aviso enviado.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/agendamentos/central-profissionais?aba=mensagem";
    }

    @PostMapping("/central-profissionais/mensagem/enviar-teste")
    public String enviarMensagemTesteCentral(
            @RequestParam(required = false) Long profissionalId,
            @RequestParam(required = false) String telefone,
            @RequestParam(required = false) String nomeProfissional,
            @RequestParam(required = false) PeriodicidadePagamento periodicidade,
            @RequestParam(required = false) String textoMensagem,
            @RequestParam(required = false) String valorTotal,
            RedirectAttributes redirectAttributes
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeAcessarCentralProfissionais(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }
        redirectAttributes.addFlashAttribute("mensagemTesteTelefone", telefone);
        redirectAttributes.addFlashAttribute("mensagemTesteNome", nomeProfissional);
        if (periodicidade != null) {
            redirectAttributes.addFlashAttribute("mensagemTestePeriodicidade", periodicidade.name());
        }
        try {
            String url = montarUrlWhatsappMensagemCentral(
                    profissionalId,
                    telefone,
                    nomeProfissional,
                    periodicidade,
                    textoMensagem,
                    valorTotal
            );
            return "redirect:" + url;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
            return "redirect:/agendamentos/central-profissionais?aba=mensagem";
        }
    }

    @PostMapping("/central-profissionais/valores/salvar")
    public String salvarValoresConsultaProfissional(
            @RequestParam Long usuarioId,
            @RequestParam(name = "valorAvulso", required = false) String valorAvulso,
            @RequestParam(name = "valorSemanal", required = false) String valorSemanal,
            @RequestParam(name = "valorQuinzenal", required = false) String valorQuinzenal,
            @RequestParam(name = "valorMensal", required = false) String valorMensal,
            @RequestParam(name = "taxaSala4", required = false) String taxaSala4,
            @RequestParam(name = "percentualTaxaIndicacao", required = false) String percentualTaxaIndicacao,
            RedirectAttributes redirectAttributes
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeGerenciarValoresConsultaProfissionais(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }
        try {
            AtualizarValoresConsultaProfissionalForm form = montarFormValoresConsulta(
                    valorAvulso,
                    valorSemanal,
                    valorQuinzenal,
                    valorMensal,
                    percentualTaxaIndicacao
            );
            form.setUsuarioId(usuarioId);
            int consultasAtualizadas = usuarioService.atualizarValoresConsultaProfissional(form, usuarioLogado);
            int consultasSala4 = 0;
            if (taxaSala4 != null && !taxaSala4.isBlank()) {
                consultasSala4 = taxaSalaService.atualizarTaxaSala4(parseValorMoedaOpcional(taxaSala4));
            }
            String mensagem = consultasAtualizadas > 0
                    ? "Taxas de sala salvas. Consultas não pagas ajustadas (Clin.): "
                    + consultasAtualizadas
                    + ". Consultas já pagas não foram alteradas. Recarregue a agenda (F5)."
                    : "Taxas de sala salvas. Nenhuma consulta não paga precisou de ajuste (Clin. já igual ou locação de turno).";
            if (consultasSala4 > 0) {
                mensagem += " Sala 4: " + consultasSala4 + " consulta(s) não paga(s) ajustada(s).";
            }
            redirectAttributes.addFlashAttribute("sucesso", mensagem);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/agendamentos/central-profissionais?aba=valores";
    }

    @PostMapping("/central-profissionais/valores/salvar-todos")
    public String salvarValoresConsultaTodosProfissionais(
            @RequestParam(name = "valorAvulso", required = false) String valorAvulso,
            @RequestParam(name = "valorSemanal", required = false) String valorSemanal,
            @RequestParam(name = "valorQuinzenal", required = false) String valorQuinzenal,
            @RequestParam(name = "valorMensal", required = false) String valorMensal,
            @RequestParam(name = "percentualTaxaIndicacao", required = false) String percentualTaxaIndicacao,
            @RequestParam(name = "taxaSala4", required = false) String taxaSala4,
            @RequestParam(name = "excluirUsuarioIds", required = false) List<Long> excluirUsuarioIds,
            RedirectAttributes redirectAttributes
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeGerenciarValoresConsultaProfissionais(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }
        try {
            AtualizarValoresConsultaProfissionalForm form = montarFormValoresConsulta(
                    valorAvulso,
                    valorSemanal,
                    valorQuinzenal,
                    valorMensal,
                    percentualTaxaIndicacao
            );
            ResultadoAtualizacaoValoresConsulta resultado =
                    usuarioService.atualizarValoresConsultaTodosProfissionais(
                            form,
                            usuarioLogado,
                            excluirUsuarioIds
                    );
            int consultasSala4 = 0;
            if (taxaSala4 != null && !taxaSala4.isBlank()) {
                consultasSala4 = taxaSalaService.atualizarTaxaSala4(parseValorMoedaOpcional(taxaSala4));
            }
            String mensagem = "Taxas de sala alteradas para "
                    + resultado.profissionaisAtualizados()
                    + " profissional(is). Consultas não pagas ajustadas (Clin.): "
                    + resultado.consultasAtualizadas()
                    + ". Consultas já pagas não foram alteradas. Recarregue a agenda (F5).";
            if (consultasSala4 > 0) {
                mensagem += " Sala 4: " + consultasSala4 + " consulta(s) não paga(s) ajustada(s).";
            }
            if (resultado.profissionaisExcluidos() > 0) {
                mensagem += " "
                        + resultado.profissionaisExcluidos()
                        + " profissional(is) mantido(s) sem alteração (Menos).";
            }
            redirectAttributes.addFlashAttribute("sucesso", mensagem);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/agendamentos/central-profissionais?aba=valores";
    }

    private AtualizarValoresConsultaProfissionalForm montarFormValoresConsulta(
            String valorAvulso,
            String valorSemanal,
            String valorQuinzenal,
            String valorMensal,
            String percentualTaxaIndicacao
    ) {
        AtualizarValoresConsultaProfissionalForm form = new AtualizarValoresConsultaProfissionalForm();
        form.setValorAvulso(parseValorMoedaOpcional(valorAvulso));
        form.setValorSemanal(parseValorMoedaOpcional(valorSemanal));
        form.setValorQuinzenal(parseValorMoedaOpcional(valorQuinzenal));
        form.setValorMensal(parseValorMoedaOpcional(valorMensal));
        form.setPercentualTaxaIndicacao(parsePercentualOpcional(percentualTaxaIndicacao));
        return form;
    }

    private BigDecimal parsePercentualOpcional(String valorTexto) {
        if (valorTexto == null || valorTexto.isBlank()) {
            return null;
        }
        try {
            return MoedaBrasilUtil.parsePercentual(valorTexto);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    private BigDecimal parseValorMoedaOpcional(String valorTexto) {
        if (valorTexto == null || valorTexto.isBlank()) {
            return null;
        }
        return MoedaBrasilUtil.parse(valorTexto);
    }

    @GetMapping(value = "/central-profissionais/uso-site/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> baixarPdfRelatorioUsoSiteCentral(HttpSession session) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeVerRelatorioUsoSite(usuarioLogado)) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Acesso negado.".getBytes(StandardCharsets.UTF_8));
        }
        var relatorioOpt = relatorioUsoSiteSessaoService.obter(session);
        if (relatorioOpt.isEmpty()) {
            return ResponseEntity.status(400)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Gere o relatorio nesta aba antes de baixar o PDF.".getBytes(StandardCharsets.UTF_8));
        }
        try {
            byte[] pdf = relatorioUsoSitePdfService.gerarPdf(relatorioOpt.get());
            if (pdf.length < 4 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') {
                log.error("PDF do relatorio de uso do site invalido (tamanho={})", pdf.length);
                return ResponseEntity.internalServerError()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Erro ao gerar PDF.".getBytes(StandardCharsets.UTF_8));
            }
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(relatorioUsoSitePdfService.nomeArquivoPdf(), StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (RuntimeException e) {
            log.error("Falha ao gerar PDF do relatorio de uso do site", e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Erro ao gerar PDF: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/manual")
    public String abrirManual(Model model) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", authService.isAdmin(usuarioLogado));
        model.addAttribute("isDonaClinica", authService.isDonaClinica(usuarioLogado));
        model.addAttribute("podeGerenciarEquipe", authService.podeGerenciarEquipe(usuarioLogado));
        model.addAttribute(
                "podeAcessarGestaoFinanceira",
                financeiroPolyanaAcessoService.podeAcessarGestaoFinanceira(usuarioLogado)
        );
        boolean ehProfissional = !authService.isAdmin(usuarioLogado) && !authService.isDonaClinica(usuarioLogado);
        PeriodicidadePagamento periodicidadePagamento = pagamentoConsultaService.resolverPeriodicidade(usuarioLogado);
        model.addAttribute("ehProfissional", ehProfissional);
        model.addAttribute("periodicidadePagamento", periodicidadePagamento);
        model.addAttribute("pagamentoDiario", periodicidadePagamento == PeriodicidadePagamento.DIARIO);
        model.addAttribute("pagamentoSemanal", periodicidadePagamento == PeriodicidadePagamento.SEMANAL);
        model.addAttribute("pagamentoMensal", periodicidadePagamento == PeriodicidadePagamento.MENSAL);
        model.addAttribute("periodicidadesPagamento", PeriodicidadePagamento.values());
        model.addAttribute("podeEscolherFormaPagamento", authService.podeEscolherFormaPagamento(usuarioLogado));
        model.addAttribute("podeAcessarCentralProfissionais", authService.podeAcessarCentralProfissionais(usuarioLogado));
        model.addAttribute("podeVerRelatorioProprio", authService.podeVerRelatorioProprio(usuarioLogado));
        model.addAttribute("podeTrocarPropriaSenha", authService.podeTrocarPropriaSenha(usuarioLogado));
        model.addAttribute("manualVideoUrl", manualProperties.getVideoUrlNormalizada());
        model.addAttribute("manualVideoTitulo", manualProperties.getVideoTitulo());
        model.addAttribute("manualVideoDescricao", manualProperties.getVideoDescricao());
        model.addAttribute("manualVideoAtivo", manualProperties.temVideoConfigurado());
        model.addAttribute("manualVideoModo", manualProperties.resolverModoVideo());
        return "manual";
    }

    @GetMapping("/meus-pacientes")
    public String abrirMeusPacientes(Model model) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeAcessarMeusPacientes(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }

        List<com.clinica.sistema.dto.PacienteAgendamentoCardView> cards =
                service.listarCardsPacientesAgendamento(usuarioLogado, usuarioLogado);
        model.addAttribute("agendamentoService", service);
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("salas", service.listarSalas());
        model.addAttribute("horariosDisponiveis", service.listarHorariosDisponiveis());
        model.addAttribute("cardsPacientes", cards);
        model.addAttribute("totalClientes", cards.size());
        return "meus-pacientes";
    }

    @GetMapping("/meus-pagamentos")
    public String abrirMeusPagamentos(Model model, HttpSession session) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }

        List<com.clinica.sistema.model.Agendamento> meusPagamentosPendentes =
                pagamentoConsultaService.listarPagamentosPendentesExibicaoMeusPagamentos(usuarioLogado);
        List<com.clinica.sistema.model.Agendamento> consultasSemana =
                pagamentoConsultaService.listarConsultasAdiantamentoSemanaAtual(usuarioLogado);
        List<com.clinica.sistema.model.Agendamento> consultasMes =
                pagamentoConsultaService.listarConsultasPagamentoMensal(usuarioLogado);
        PeriodicidadePagamento periodicidade = pagamentoConsultaService.resolverPeriodicidade(usuarioLogado);
        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", false);
        model.addAttribute("isDonaClinica", false);
        model.addAttribute("podeGerenciarEquipe", false);
        model.addAttribute("podeAcessarGestaoFinanceira", false);
        model.addAttribute("podeVerRelatorioProprio", authService.podeVerRelatorioProprio(usuarioLogado));
        model.addAttribute("podeTrocarPropriaSenha", authService.podeTrocarPropriaSenha(usuarioLogado));
        model.addAttribute("podeEscolherFormaPagamento", true);
        model.addAttribute("periodicidadePagamento", periodicidade);
        model.addAttribute("periodicidadesPagamento", PeriodicidadePagamento.values());
        popularControlePeriodicidadePropria(model, usuarioLogado);
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        pagamentoConsultaService.adicionarResumoPendenciasPagamentoAoModel(model, usuarioLogado, session);
        model.addAttribute("meusPagamentosPendentes", meusPagamentosPendentes);
        model.addAttribute("totalMeusPagamentosPendentes", meusPagamentosPendentes.size());
        model.addAttribute("rotuloProximoDiaPendentes", pagamentoConsultaService.rotuloProximoDiaPagamentoPendente());
        model.addAttribute("totalTaxaPendentesSelecionaveis",
                pagamentoConsultaService.formatarTotalTaxaPix(meusPagamentosPendentes));
        model.addAttribute("consultasPagamentoSemana", consultasSemana);
        model.addAttribute("totalConsultasPagamentoSemana", consultasSemana.size());
        model.addAttribute("rotuloSemanaAtual", pagamentoConsultaService.rotuloPeriodoSemanaAtual());
        model.addAttribute("totalTaxaSemana", pagamentoConsultaService.formatarTotalTaxaPix(consultasSemana));
        model.addAttribute("consultasPagamentoMes", consultasMes);
        model.addAttribute("totalConsultasPagamentoMes", consultasMes.size());
        model.addAttribute("rotuloMesPagamento", pagamentoConsultaService.rotuloMesPagamentoPendente());
        model.addAttribute("totalTaxaMes", pagamentoConsultaService.formatarTotalTaxaPix(consultasMes));
        aplicarModalPixConfirmadoSeNecessario(model);
        return "meus-pagamentos";
    }

    @PostMapping("/central-profissionais/cadastrar")
    public String cadastrarProfissionalCentral(
            @ModelAttribute CadastroProfissionalForm cadastroProfissionalForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            Usuario novo = usuarioService.cadastrarProfissional(cadastroProfissionalForm, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Profissional cadastrado: " + novo.getNome() + ".");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("cadastroProfissionalForm", cadastroProfissionalForm);
            redirectAttributes.addFlashAttribute("abrirModalCadastro", true);
        }
        return "redirect:/agendamentos/central-profissionais";
    }

    @PostMapping("/central-profissionais/trocar-senha")
    public String trocarSenhaCentral(
            @ModelAttribute TrocarSenhaAdminForm trocarSenhaAdminForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            usuarioService.trocarSenhaComoGestor(trocarSenhaAdminForm, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Senha alterada com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("trocarSenhaAdminForm", trocarSenhaAdminForm);
            redirectAttributes.addFlashAttribute("abrirModalTrocarSenha", true);
        }
        return "redirect:/agendamentos/central-profissionais";
    }

    @PostMapping("/central-profissionais/editar")
    public String editarProfissionalCentral(
            @ModelAttribute EditarProfissionalForm editarProfissionalForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            Usuario atualizado = usuarioService.atualizarProfissionalEquipe(editarProfissionalForm, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Dados atualizados: " + atualizado.getNome() + ".");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("editarProfissionalForm", editarProfissionalForm);
            redirectAttributes.addFlashAttribute("abrirModalEditar", true);
        }
        return "redirect:/agendamentos/central-profissionais";
    }

    @PostMapping("/central-profissionais/excluir")
    public String excluirProfissionalCentral(
            @RequestParam Long usuarioId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            usuarioService.excluirUsuario(usuarioId, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário excluído com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/agendamentos/central-profissionais";
    }

    @PostMapping("/central-profissionais/periodicidade")
    public String atualizarPeriodicidadeCentral(
            @ModelAttribute AtualizarPeriodicidadeForm form,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            int agendamentosMigrados = usuarioService.atualizarPeriodicidadePagamento(form, usuarioLogado);
            if (agendamentosMigrados > 0) {
                redirectAttributes.addFlashAttribute(
                        "sucesso",
                        "Periodicidade atualizada. "
                                + agendamentosMigrados
                                + " agendamento(s) futuro(s) ajustado(s) para a nova regra."
                );
            } else {
                redirectAttributes.addFlashAttribute("sucesso", "Periodicidade de pagamento atualizada.");
            }
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/agendamentos/central-profissionais";
    }

    @PostMapping("/minha-periodicidade-pagamento")
    public String atualizarMinhaPeriodicidadePagamento(
            @RequestParam PeriodicidadePagamento periodicidade,
            @RequestParam(defaultValue = "/agendamentos/meus-pagamentos") String retorno,
            RedirectAttributes redirectAttributes
    ) {
        String destino = normalizarRetornoPeriodicidade(retorno);
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            int agendamentosMigrados = usuarioService.atualizarPeriodicidadePropria(periodicidade, usuarioLogado);
            if (agendamentosMigrados > 0) {
                redirectAttributes.addFlashAttribute(
                        "sucesso",
                        "Forma de pagamento atualizada. "
                                + agendamentosMigrados
                                + " agendamento(s) futuro(s) ajustado(s) para a nova regra."
                );
            } else {
                redirectAttributes.addFlashAttribute("sucesso", "Forma de pagamento atualizada.");
            }
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:" + destino;
    }

    @PostMapping
    public String criar(
            @ModelAttribute AgendamentoForm agendamentoForm,
            RedirectAttributes redirectAttributes
    ) {
        boolean continuacaoMensal = agendamentoForm.isContinuacaoMensal();
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            var criado = service.salvar(agendamentoForm, usuarioLogado);
            if (PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO.equals(criado.getStatusPagamento())) {
                redirectAttributes.addFlashAttribute(
                        "sucesso",
                        "Reserva de indicação enviada. A Polyana precisa aprovar antes do pagamento PIX."
                );
            } else if (PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(criado.getStatusPagamento())) {
                redirectAttributes.addFlashAttribute(
                        "sucesso",
                        "Agendamento reservado na agenda. Esperando confirmação do pagamento ("
                                + pagamentoConsultaService.prazoConfirmacaoMinutos()
                                + " min). "
                                + "Se fechar o QR, use a aba Pagamentos pendentes para voltar."
                );
                redirectAttributes.addFlashAttribute("pagamentoAgendamentoId", criado.getId());
            } else if (pagamentoConsultaService.isAgendadoPorGestorParaOutroProfissional(
                    criado.getProfissional(),
                    usuarioLogado
            )) {
                String nomeProfissional = criado.getProfissional() != null && criado.getProfissional().getNome() != null
                        ? criado.getProfissional().getNome()
                        : "o profissional";
                redirectAttributes.addFlashAttribute(
                        "sucesso",
                        "Agendamento cadastrado para " + nomeProfissional + ". "
                                + "A 1ª consulta já fica confirmada; as próximas seguem a regra de pagamento dela."
                );
            } else {
                redirectAttributes.addFlashAttribute(
                        "sucesso",
                        "QUINZENAL".equalsIgnoreCase(agendamentoForm.getRecorrencia())
                                ? "Agendamento quinzenal cadastrado. A série continua automaticamente até encerrar."
                                : "SEMANAL".equalsIgnoreCase(agendamentoForm.getRecorrencia())
                                        ? "Agendamento fixo cadastrado. A série continua automaticamente até encerrar."
                                        : "MENSAL".equalsIgnoreCase(agendamentoForm.getRecorrencia())
                                                ? (agendamentoForm.isContinuacaoMensal()
                                                        ? "Próxima consulta mensal reservada. Pagamento conforme sua regra (diário na véspera, semanal ou mensal)."
                                                        : "Consulta mensal cadastrada. Marque a próxima quando precisar em Meus agendamentos.")
                                                : "Agendamento cadastrado com sucesso."
                );
            }
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
            redirectAttributes.addFlashAttribute("abrirModalErroAgendamento", true);
            redirectAttributes.addFlashAttribute("agendamentoForm", agendamentoForm);
        }
        if (continuacaoMensal) {
            return "redirect:/agendamentos/dashboard?secaoMensal=true#meus-agendamentos";
        }
        return "redirect:/agendamentos/dashboard";
    }

    @GetMapping("/{id}/proxima-mensal")
    public String prepararProximaConsultaMensal(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            service.prepararProximaConsultaMensal(id, usuarioLogado);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/agendamentos/dashboard";
        }
        return "redirect:/agendamentos/dashboard?abrirRemarcarMensal=" + id;
    }

    @GetMapping("/{id}/realocar")
    public String formRealocar(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Usuario usuarioLogado = authService.buscarUsuarioLogado().orElse(null);
        if (usuarioLogado == null) {
            return "redirect:/login";
        }

        com.clinica.sistema.model.Agendamento agendamento = service.buscarPorId(id).orElse(null);
        if (agendamento == null) {
            redirectAttributes.addFlashAttribute("erro", "Agendamento não encontrado.");
            return "redirect:/agendamentos/dashboard";
        }
        if (!service.podeRealocar(agendamento, usuarioLogado)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Realocação permitida somente antes do horário do atendimento, com pagamento confirmado "
                            + "ou cobrança semanal/mensal ainda pendente."
            );
            return "redirect:/agendamentos/dashboard";
        }

        RelocacaoAgendamentoForm form = new RelocacaoAgendamentoForm();
        if (agendamento.getSala() != null) {
            form.setSalaId(agendamento.getSala().getId());
        }
        if (agendamento.getDataHoraInicio() != null) {
            form.setDataAtendimento(agendamento.getDataHoraInicio().toLocalDate());
            form.setHorarioAtendimento(agendamento.getDataHoraInicio().toLocalTime());
        }

        model.addAttribute("agendamento", agendamento);
        model.addAttribute("relocacaoForm", form);
        model.addAttribute("salas", service.listarSalas());
        model.addAttribute("horariosDisponiveis", service.listarHorariosDisponiveis());
        model.addAttribute("turnosLocacao", service.listarTurnosLocacao());
        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("realocacaoAvulsa", service.isRealocacaoAvulsa(agendamento));
        model.addAttribute("datasPermitidasRealocacao", service.listarDatasPermitidasRealocacao(agendamento));
        return "agendamento-realocar";
    }

    @PostMapping("/{id}/realocar")
    public String realocar(
            @PathVariable Long id,
            @ModelAttribute RelocacaoAgendamentoForm relocacaoForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            com.clinica.sistema.model.Agendamento atualizado = service.realocar(id, relocacaoForm, usuarioLogado);
            String mensagemSucesso = "Agendamento realocado para "
                    + (atualizado.getSala() != null ? atualizado.getSala().getNome() : "sala")
                    + " em "
                    + (atualizado.getDataHoraInicio() != null
                    ? atualizado.getDataHoraInicio().format(
                            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    : "-");
            if (pagamentoConsultaService.realocacaoMensalComCobrancaPendente(atualizado)) {
                mensagemSucesso += ". A cobranca mensal continua no mes "
                        + pagamentoConsultaService.rotuloMesCobranca(atualizado)
                        + " (pagamento do dia 01 ao "
                        + pagamentoConsultaService.formatarJanelaPagamentoMensal(atualizado)
                        + ").";
            } else if (pagamentoConsultaService.realocacaoSemanalComCobrancaPendente(atualizado)) {
                mensagemSucesso += ". A cobranca semanal continua na semana "
                        + pagamentoConsultaService.rotuloIntervaloSemanaCobranca(atualizado)
                        + " (pague em Meus pagamentos até domingo).";
            } else {
                mensagemSucesso += ". O pagamento ja realizado foi mantido.";
            }
            redirectAttributes.addFlashAttribute("sucesso", mensagemSucesso);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            service.cancelar(id, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Agendamento cancelado com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/{id}/cancelar-serie-mensal")
    public String cancelarSerieMensal(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            int cancelados = service.cancelarSerieMensal(id, usuarioLogado);
            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    cancelados == 1
                            ? "Consulta mensal cancelada."
                            : cancelados + " consultas mensais futuras canceladas."
            );
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/{id}/encerrar-fixo")
    public String encerrarFixo(
            @PathVariable Long id,
            @RequestParam("motivoEncerramento") String motivoEncerramento,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            service.encerrarSerieFixa(id, motivoEncerramento, usuarioLogado);
            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    "Série encerrada. Todos os horários foram removidos e o motivo ficou registrado em Central dos profissionais > Encerramentos."
            );
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/{id}/alterar-valor-profissional-serie")
    public String alterarValorProfissionalSerie(
            @PathVariable Long id,
            @RequestParam("valorProfissionalRecebe") String valorProfissionalRecebe,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            BigDecimal novoValor = MoedaBrasilUtil.parse(valorProfissionalRecebe);
            int atualizados = service.alterarValorProfissionalSerie(id, novoValor, usuarioLogado);
            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    atualizados == 1
                            ? "Valor da consulta atualizado em 1 data pendente. Datas já pagas não foram alteradas."
                            : "Valor da consulta atualizado em " + atualizados
                            + " datas pendentes. Datas já pagas não foram alteradas."
            );
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
        }
        return "redirect:/agendamentos/dashboard#meus-agendamentos";
    }

    @PostMapping("/sincronizar-fixos")
    public String sincronizarFixos(
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            if (!authService.isAdmin(usuarioLogado)) {
                throw new RuntimeException("Somente a administração pode carregar a agenda fixa.");
            }

            startupDataInitializer.sincronizarCargaInicialClinica();
            redirectAttributes.addFlashAttribute("sucesso", "Agenda fixa da planilha carregada com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/resetar-demo")
    public String resetarDemonstracao(
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            if (!authService.isAdmin(usuarioLogado)) {
                throw new RuntimeException("Somente a administração pode restaurar a demonstração.");
            }

            startupDataInitializer.resetarBaseDemonstracao(usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Demonstração restaurada com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
        }
        return "redirect:/agendamentos/dashboard";
    }

    private LocalDate agendaDataSugerida(LocalDate semana) {
        LocalDate base = semana != null ? semana : LocalDate.now();
        if (base.getDayOfWeek().getValue() > 6) {
            base = base.with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        }
        return base;
    }

    private void popularControlePeriodicidadePropria(Model model, Usuario usuarioLogado) {
        if (!authService.podeEscolherFormaPagamento(usuarioLogado)) {
            return;
        }
        model.addAttribute("podeAlterarPeriodicidadePropria", usuarioService.podeAlterarPeriodicidadePropria(usuarioLogado));
        model.addAttribute("mensagemBloqueioPeriodicidade", usuarioService.mensagemBloqueioPeriodicidade(usuarioLogado));
    }

    private String normalizarRetornoPeriodicidade(String retorno) {
        if (retorno == null || retorno.isBlank()) {
            return "/agendamentos/meus-pagamentos";
        }
        if (!retorno.startsWith("/agendamentos/")) {
            return "/agendamentos/meus-pagamentos";
        }
        return retorno;
    }
}
