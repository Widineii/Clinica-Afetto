package com.clinica.sistema.controller;

import com.clinica.sistema.config.ManualProperties;
import com.clinica.sistema.config.StartupDataInitializer;
import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.RelocacaoAgendamentoForm;
import com.clinica.sistema.dto.CadastroProfissionalForm;
import com.clinica.sistema.dto.TrocarSenhaAdminForm;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AgendamentoService;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.FinanceiroPolyanaAcessoService;
import com.clinica.sistema.service.PagamentoConsultaService;
import com.clinica.sistema.service.RelatorioMensalService;
import com.clinica.sistema.service.RelatorioSemanalService;
import com.clinica.sistema.service.UsuarioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/agendamentos")
public class AgendamentoController {
    private final AgendamentoService service;
    private final AuthService authService;
    private final StartupDataInitializer startupDataInitializer;
    private final UsuarioService usuarioService;
    private final RelatorioSemanalService relatorioSemanalService;
    private final RelatorioMensalService relatorioMensalService;
    private final PagamentoConsultaService pagamentoConsultaService;
    private final FinanceiroPolyanaAcessoService financeiroPolyanaAcessoService;
    private final ManualProperties manualProperties;

    public AgendamentoController(
            AgendamentoService service,
            AuthService authService,
            StartupDataInitializer startupDataInitializer,
            UsuarioService usuarioService,
            RelatorioSemanalService relatorioSemanalService,
            RelatorioMensalService relatorioMensalService,
            PagamentoConsultaService pagamentoConsultaService,
            FinanceiroPolyanaAcessoService financeiroPolyanaAcessoService,
            ManualProperties manualProperties
    ) {
        this.service = service;
        this.authService = authService;
        this.startupDataInitializer = startupDataInitializer;
        this.usuarioService = usuarioService;
        this.relatorioSemanalService = relatorioSemanalService;
        this.relatorioMensalService = relatorioMensalService;
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.financeiroPolyanaAcessoService = financeiroPolyanaAcessoService;
        this.manualProperties = manualProperties;
    }

    @ModelAttribute("gradeAcoesPorId")
    public Map<Long, String> gradeAcoesPorIdPadrao() {
        return Collections.emptyMap();
    }

    @ModelAttribute
    public void prepararAjudaSuporte(Model model) {
        model.addAttribute("manualWhatsappAtivo", manualProperties.temWhatsappSuporte());
        model.addAttribute("manualWhatsappUrl", manualProperties.resolverLinkWhatsapp());
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
                },
                () -> {
                    model.addAttribute("notificacaoRelatorioMensal", null);
                    model.addAttribute("exibirBolinhaNotificacaoRelatorio", false);
                }
        );
    }

    @GetMapping("/dashboard")
    public String abrirDashboard(
            Model model,
            @RequestParam(required = false) Long salaId,
            @RequestParam(required = false) LocalDate semana,
            @RequestParam(required = false) Long pagamentoId,
            HttpSession session
    ) {
        relatorioSemanalService.limparSessao(session);
        Usuario usuarioLogado = authService.buscarUsuarioLogado().orElse(null);
        if (usuarioLogado == null) {
            return "redirect:/login";
        }

        service.renovarSeriesRecorrentesAtivas();
        pagamentoConsultaService.processarPagamentosPendentes();

        boolean isAdmin = authService.isAdmin(usuarioLogado);

        if (!model.containsAttribute("agendamentoForm")) {
            AgendamentoForm form = new AgendamentoForm();
            form.setProfissionalId(usuarioLogado.getId());
            LocalDate dataSugerida = agendaDataSugerida(semana);
            form.setDataAtendimento(dataSugerida);
            form.setHorarioAtendimento(service.listarHorariosDisponiveis().get(0));
            model.addAttribute("agendamentoForm", form);
        }
        boolean podeGerenciarEquipe = authService.podeGerenciarEquipe(usuarioLogado);
        if (!podeGerenciarEquipe && !model.containsAttribute("trocarSenhaForm")) {
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
                service.listarProximosPorSerie(agendamentos, com.clinica.sistema.model.Agendamento::isAvulso);
        List<com.clinica.sistema.model.Agendamento> agendamentosFixos =
                service.listarProximasOcorrencias(agendamentos, com.clinica.sistema.model.Agendamento::isFixoSemanal);
        List<com.clinica.sistema.model.Agendamento> agendamentosQuinzenais =
                service.listarProximasOcorrencias(agendamentos, com.clinica.sistema.model.Agendamento::isQuinzenal);

        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isDonaClinica", authService.isDonaClinica(usuarioLogado));
        model.addAttribute(
                "podeAcessarGestaoFinanceira",
                financeiroPolyanaAcessoService.podeAcessarGestaoFinanceira(usuarioLogado)
        );
        model.addAttribute("podeGerenciarEquipe", podeGerenciarEquipe);
        model.addAttribute("podeVerValoresDeTodos", podeGerenciarEquipe);
        model.addAttribute(
                "exibirPainelValoresConsulta",
                !authService.profissionalIgnoraValoresEPagamento(usuarioLogado)
        );
        model.addAttribute("agendamentos", agendamentos);
        model.addAttribute("agendamentosAvulsos", agendamentosAvulsos);
        model.addAttribute("agendamentosFixos", agendamentosFixos);
        model.addAttribute("agendamentosQuinzenais", agendamentosQuinzenais);
        model.addAttribute("totalAgendamentosAvulsos",
                service.contarSeries(agendamentos, com.clinica.sistema.model.Agendamento::isAvulso));
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
        } else {
            model.addAttribute("seriesFixasResumo", Collections.emptyList());
            model.addAttribute("seriesQuinzenaisResumo", Collections.emptyList());
            model.addAttribute("totalFixosResumo", 0);
            model.addAttribute("totalQuinzenaisResumo", 0);
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
        LocalDate referenciaSemana = agendaDataSugerida(semana);
        Long salaIdGrade = service.resolverSalaIdParaGrade(salaId, referenciaSemana);
        var agendaSala = service.montarAgendaSala(salaIdGrade, referenciaSemana);
        Map<Long, String> gradeAcoesPorId = service.montarAcoesGradePorId(agendaSala, usuarioLogado);
        java.util.Map<Long, Integer> salasOcupadasNaSemana = service.contarAgendamentosPorSalaNaSemana(referenciaSemana);
        service.mensagemAgendamentosEmOutraSala(agendaSala.getSala().getId(), referenciaSemana)
                .ifPresent(msg -> model.addAttribute("avisoAgendamentoOutraSala", msg));
        model.addAttribute("salasOcupadasNaSemana", salasOcupadasNaSemana);
        List<com.clinica.sistema.model.Agendamento> agendamentosDoDia =
                service.listarAgendamentosDoDia(usuarioLogado, podeGerenciarEquipe);
        model.addAttribute("agendaSala", agendaSala);
        model.addAttribute("agendamentosDoDia", agendamentosDoDia);
        model.addAttribute("dataAgendaDia", LocalDate.now());
        model.addAttribute("totalAgendamentosDoDia", agendamentosDoDia.size());
        model.addAttribute("gradeAcoesPorId", gradeAcoesPorId != null ? gradeAcoesPorId : Collections.emptyMap());
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("agendamentoService", service);
        var pendenciasBloqueioPagamento = pagamentoConsultaService.listarPendenciasObrigatoriasParaBloqueio(usuarioLogado);
        model.addAttribute("pagamentoBloqueioAtivo", !pendenciasBloqueioPagamento.isEmpty());
        model.addAttribute(
                "pagamentoBloqueioAgendamentoId",
                pendenciasBloqueioPagamento.isEmpty() ? null : pendenciasBloqueioPagamento.get(0).getId()
        );
        model.addAttribute(
                "pagamentoBloqueioMensagem",
                pagamentoConsultaService.mensagemBloqueioPagamento(usuarioLogado)
        );
        model.addAttribute(
                "totalMeusPagamentosPendentes",
                pagamentoConsultaService.listarPagamentosPendentesProximoDia(usuarioLogado).size()
        );
        model.addAttribute(
                "pagamentosAguardandoQr",
                pagamentoConsultaService.listarAguardandoConfirmacao(usuarioLogado, podeGerenciarEquipe)
        );
        Object pagamentoFlashId = model.containsAttribute("pagamentoAgendamentoId")
                ? model.getAttribute("pagamentoAgendamentoId")
                : null;
        Long pagamentoSelecionadoId = pagamentoId != null ? pagamentoId : extrairIdPagamento(pagamentoFlashId);
        if (pagamentoSelecionadoId != null) {
            service.buscarPorId(pagamentoSelecionadoId).ifPresent(ag -> model.addAttribute("pagamentoAgendamento", ag));
        }
        return "agenda";
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
    public String abrirCentralProfissionais(Model model) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeGerenciarEquipe(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }

        if (!model.containsAttribute("cadastroProfissionalForm")) {
            model.addAttribute("cadastroProfissionalForm", new CadastroProfissionalForm());
        }
        if (!model.containsAttribute("trocarSenhaAdminForm")) {
            model.addAttribute("trocarSenhaAdminForm", new TrocarSenhaAdminForm());
        }

        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", authService.isAdmin(usuarioLogado));
        model.addAttribute("isDonaClinica", authService.isDonaClinica(usuarioLogado));
        model.addAttribute("profissionais", usuarioService.listarProfissionaisDaEquipe());
        model.addAttribute("usuariosSenha", usuarioService.listarUsuariosParaTrocaSenha());
        return "central-profissionais";
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
        model.addAttribute(
                "ehProfissional",
                !authService.isAdmin(usuarioLogado) && !authService.isDonaClinica(usuarioLogado)
        );
        model.addAttribute("manualVideoUrl", manualProperties.getVideoUrl());
        model.addAttribute("manualVideoTitulo", manualProperties.getVideoTitulo());
        model.addAttribute("manualVideoDescricao", manualProperties.getVideoDescricao());
        model.addAttribute("manualVideoAtivo", manualProperties.temVideoConfigurado());
        model.addAttribute("manualVideoModo", manualProperties.resolverModoVideo());
        return "manual";
    }

    @GetMapping("/meus-pagamentos")
    public String abrirMeusPagamentos(Model model) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }

        List<com.clinica.sistema.model.Agendamento> meusPagamentosPendentes =
                pagamentoConsultaService.listarPagamentosPendentesProximoDia(usuarioLogado);
        List<com.clinica.sistema.model.Agendamento> consultasSemana =
                pagamentoConsultaService.listarConsultasAdiantamentoSemanaAtual(usuarioLogado);
        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", false);
        model.addAttribute("isDonaClinica", false);
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("meusPagamentosPendentes", meusPagamentosPendentes);
        model.addAttribute("totalMeusPagamentosPendentes", meusPagamentosPendentes.size());
        model.addAttribute("rotuloProximoDiaPendentes", pagamentoConsultaService.rotuloProximoDiaPagamentoPendente());
        model.addAttribute("totalTaxaPendentesSelecionaveis",
                pagamentoConsultaService.formatarTotalTaxaPix(meusPagamentosPendentes));
        model.addAttribute("consultasPagamentoSemana", consultasSemana);
        model.addAttribute("totalConsultasPagamentoSemana", consultasSemana.size());
        model.addAttribute("rotuloSemanaAtual", pagamentoConsultaService.rotuloPeriodoSemanaAtual());
        model.addAttribute("totalTaxaSemana", pagamentoConsultaService.formatarTotalTaxaPix(consultasSemana));
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

    @PostMapping("/central-profissionais/excluir")
    public String excluirProfissionalCentral(
            @RequestParam Long usuarioId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            usuarioService.excluirUsuario(usuarioId, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Usuario excluido com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/agendamentos/central-profissionais";
    }

    @PostMapping
    public String criar(
            @ModelAttribute AgendamentoForm agendamentoForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            var criado = service.salvar(agendamentoForm, usuarioLogado);
            if (PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(criado.getStatusPagamento())) {
                redirectAttributes.addFlashAttribute(
                        "sucesso",
                        "Agendamento reservado na agenda. Esperando confirmacao do pagamento (5 min). "
                                + "Se fechar o QR, use a aba Pagamentos pendentes para voltar."
                );
                redirectAttributes.addFlashAttribute("pagamentoAgendamentoId", criado.getId());
            } else {
                redirectAttributes.addFlashAttribute(
                        "sucesso",
                        "QUINZENAL".equalsIgnoreCase(agendamentoForm.getRecorrencia())
                                ? "Agendamento quinzenal cadastrado. A serie continua automaticamente ate encerrar."
                                : "SEMANAL".equalsIgnoreCase(agendamentoForm.getRecorrencia())
                                        ? "Agendamento fixo cadastrado. A serie continua automaticamente ate encerrar."
                                        : "Agendamento cadastrado com sucesso."
                );
            }
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
            redirectAttributes.addFlashAttribute("abrirModalErroAgendamento", true);
            redirectAttributes.addFlashAttribute("agendamentoForm", agendamentoForm);
        }
        return "redirect:/agendamentos/dashboard";
    }

    @GetMapping("/{id}/realocar")
    public String formRealocar(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Usuario usuarioLogado = authService.buscarUsuarioLogado().orElse(null);
        if (usuarioLogado == null) {
            return "redirect:/login";
        }

        com.clinica.sistema.model.Agendamento agendamento = service.buscarPorId(id).orElse(null);
        if (agendamento == null) {
            redirectAttributes.addFlashAttribute("erro", "Agendamento nao encontrado.");
            return "redirect:/agendamentos/dashboard";
        }
        if (!service.podeRealocar(agendamento, usuarioLogado)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Realocacao permitida somente para agendamentos ja pagos e antes do horario do atendimento."
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
        model.addAttribute("usuarioLogado", usuarioLogado);
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
            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    "Agendamento realocado para "
                            + (atualizado.getSala() != null ? atualizado.getSala().getNome() : "sala")
                            + " em "
                            + (atualizado.getDataHoraInicio() != null
                            ? atualizado.getDataHoraInicio().format(
                                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                            : "-")
                            + ". O pagamento ja realizado foi mantido."
            );
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

    @PostMapping("/{id}/encerrar-fixo")
    public String encerrarFixo(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            service.encerrarSerieFixa(id, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Horario fixo encerrado com sucesso para as proximas ocorrencias.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/sincronizar-fixos")
    public String sincronizarFixos(
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            if (!authService.isAdmin(usuarioLogado)) {
                throw new RuntimeException("Somente a administracao pode carregar a agenda fixa.");
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
                throw new RuntimeException("Somente a administracao pode restaurar a demonstracao.");
            }

            startupDataInitializer.resetarBaseDemonstracao(usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Demonstracao restaurada com sucesso.");
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
}
