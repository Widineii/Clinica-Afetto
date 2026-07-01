package com.clinica.sistema.controller;

import com.clinica.sistema.config.InfinitePayProperties;
import com.clinica.sistema.exception.HorarioJaReservadoPorOutroProfissionalException;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.service.AuditoriaService;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.PagamentoConsultaService;
import com.clinica.sistema.service.QrCodeService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/pagamentos")
public class PagamentoController {

    private final PagamentoConsultaService pagamentoConsultaService;
    private final AgendamentoRepository agendamentoRepository;
    private final AuthService authService;
    private final QrCodeService qrCodeService;
    private final InfinitePayProperties infinitePayProperties;
    private final AuditoriaService auditoriaService;

    public PagamentoController(
            PagamentoConsultaService pagamentoConsultaService,
            AgendamentoRepository agendamentoRepository,
            AuthService authService,
            QrCodeService qrCodeService,
            InfinitePayProperties infinitePayProperties,
            AuditoriaService auditoriaService
    ) {
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.agendamentoRepository = agendamentoRepository;
        this.authService = authService;
        this.qrCodeService = qrCodeService;
        this.infinitePayProperties = infinitePayProperties;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/{id}")
    public String paginaPagamento(@PathVariable Long id, Model model) {
        preencherModelPagamentoConsulta(id, model);
        return "pagamento-consulta";
    }

    @GetMapping("/{id}/modal")
    public String modalPagamento(@PathVariable Long id, Model model) {
        preencherModelPagamentoConsulta(id, model);
        return "fragments/pagamento-consulta :: corpo-pagamento-consulta";
    }

    private void preencherModelPagamentoConsulta(Long id, Model model) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        Agendamento agendamento = agendamentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));
        validarAcesso(agendamento, usuarioLogado);

        model.addAttribute("agendamento", agendamento);
        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("podeGerenciarEquipe", authService.podeGerenciarEquipe(usuarioLogado));
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("rotuloStatus", pagamentoConsultaService.rotuloStatusPagamento(agendamento));
        model.addAttribute("bloqueado", pagamentoConsultaService.bloqueadoPorPagamento(agendamento));
        model.addAttribute("vagaPreenchida", pagamentoConsultaService.vagaPreenchidaPorOutroProfissional(agendamento));
        model.addAttribute("podeRecuperarVaga", pagamentoConsultaService.podeRecuperarVagaComPagamento(agendamento));
    }

    @GetMapping("/{id}/qr.png")
    public ResponseEntity<byte[]> qrCode(@PathVariable Long id) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        Agendamento agendamento = agendamentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));
        validarAcesso(agendamento, usuarioLogado);

        if (agendamento.getPagamentoLink() == null || agendamento.getPagamentoLink().isBlank()) {
            throw new RuntimeException("Link de pagamento ainda nao foi gerado.");
        }

        byte[] png = qrCodeService.gerarPng(agendamento.getPagamentoLink(), 280);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    @GetMapping("/{id}/pagar-agora")
    public String pagarAgoraGet(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            Agendamento agendamento = pagamentoConsultaService.pagarAgora(id, usuarioLogado);
            return redirecionarModalPixAgenda(agendamento, redirectAttributes);
        } catch (HorarioJaReservadoPorOutroProfissionalException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
            return "redirect:/agendamentos/meus-pagamentos";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
            return "redirect:/agendamentos/meus-pagamentos";
        }
    }

    @PostMapping("/gerar-link-dia-selecionados")
    public String gerarLinkDiaSelecionados(
            @RequestParam(value = "agendamentoIds", required = false) List<Long> agendamentoIds,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            String order = pagamentoConsultaService.gerarPagamentoUnicoPendentesSelecionados(
                    usuarioLogado,
                    agendamentoIds
            );
            return "redirect:/pagamentos/dia?order=" + order;
        } catch (HorarioJaReservadoPorOutroProfissionalException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/agendamentos/meus-pagamentos#pagamentos-pendentes";
    }

    @PostMapping("/gerar-links-semana-atual")
    public String gerarLinksSemanaAtual(RedirectAttributes redirectAttributes) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            String order = pagamentoConsultaService.gerarPagamentoUnicoSemanaAtual(usuarioLogado);
            return "redirect:/pagamentos/semana?order=" + order;
        } catch (HorarioJaReservadoPorOutroProfissionalException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/agendamentos/meus-pagamentos#pagamentos-semana";
    }

    @PostMapping({"/gerar-links-mes-vigente", "/gerar-links-mes-anterior"})
    public String gerarLinksMesVigente(RedirectAttributes redirectAttributes) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            String order = pagamentoConsultaService.gerarPagamentoUnicoMesVigente(usuarioLogado);
            return "redirect:/pagamentos/mes?order=" + order;
        } catch (HorarioJaReservadoPorOutroProfissionalException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/agendamentos/meus-pagamentos#pagamentos-mes";
    }

    @GetMapping("/dia")
    public String paginaPagamentoDia(@RequestParam String order, Model model) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!pagamentoConsultaService.isPedidoPagamentoDia(order)) {
            throw new RuntimeException("Pedido de pagamento do dia invalido.");
        }
        List<Agendamento> consultas = pagamentoConsultaService.listarAgendamentosPorOrderNsu(order, usuarioLogado);
        Agendamento referencia = consultas.get(0);
        boolean todasPagas = consultas.stream().allMatch(Agendamento::isPagamentoPago);

        model.addAttribute("consultasLote", consultas);
        model.addAttribute("agendamento", referencia);
        model.addAttribute("orderNsu", order);
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("totalTaxaLote", pagamentoConsultaService.formatarTotalTaxaPix(consultas));
        model.addAttribute("rotuloPeriodo", pagamentoConsultaService.rotuloProximoDiaPagamentoPendente());
        model.addAttribute("tituloLote", "Pagamento do proximo dia");
        model.addAttribute("qrUrl", "/pagamentos/dia/qr.png?order=" + order);
        model.addAttribute("voltarUrl", "/agendamentos/meus-pagamentos#pagamentos-pendentes");
        model.addAttribute("todasPagas", todasPagas);
        return "pagamento-lote";
    }

    @GetMapping("/dia/qr.png")
    public ResponseEntity<byte[]> qrCodeDia(@RequestParam String order) {
        return qrCodeLote(order);
    }

    @GetMapping("/semana")
    public String paginaPagamentoSemana(@RequestParam String order, Model model) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!pagamentoConsultaService.isPedidoPagamentoSemana(order)) {
            throw new RuntimeException("Pedido de pagamento da semana invalido.");
        }
        List<Agendamento> consultas = pagamentoConsultaService.listarAgendamentosPorOrderNsu(order, usuarioLogado);
        Agendamento referencia = consultas.get(0);
        boolean todasPagas = consultas.stream().allMatch(Agendamento::isPagamentoPago);

        model.addAttribute("consultasLote", consultas);
        model.addAttribute("agendamento", referencia);
        model.addAttribute("orderNsu", order);
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("totalTaxaLote", pagamentoConsultaService.formatarTotalTaxaPix(consultas));
        model.addAttribute("rotuloPeriodo", pagamentoConsultaService.rotuloPeriodoSemanaAtual());
        model.addAttribute("tituloLote", "Pagamento da semana inteira");
        model.addAttribute("qrUrl", "/pagamentos/semana/qr.png?order=" + order);
        model.addAttribute("voltarUrl", "/agendamentos/meus-pagamentos#pagamentos-semana");
        model.addAttribute("todasPagas", todasPagas);
        return "pagamento-lote";
    }

    @GetMapping("/semana/qr.png")
    public ResponseEntity<byte[]> qrCodeSemana(@RequestParam String order) {
        return qrCodeLote(order);
    }

    @GetMapping("/mes")
    public String paginaPagamentoMes(@RequestParam String order, Model model) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!pagamentoConsultaService.isPedidoPagamentoMes(order)) {
            throw new RuntimeException("Pedido de pagamento do mes invalido.");
        }
        List<Agendamento> consultas = pagamentoConsultaService.listarAgendamentosPorOrderNsu(order, usuarioLogado);
        Agendamento referencia = consultas.get(0);
        boolean todasPagas = consultas.stream().allMatch(Agendamento::isPagamentoPago);

        model.addAttribute("consultasLote", consultas);
        model.addAttribute("agendamento", referencia);
        model.addAttribute("orderNsu", order);
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("totalTaxaLote", pagamentoConsultaService.formatarTotalTaxaPix(consultas));
        model.addAttribute("rotuloPeriodo", pagamentoConsultaService.rotuloMesPagamentoPendente());
        model.addAttribute("tituloLote", "Pagamento do mes vigente");
        model.addAttribute("qrUrl", "/pagamentos/mes/qr.png?order=" + order);
        model.addAttribute("voltarUrl", "/agendamentos/meus-pagamentos#pagamentos-mes");
        model.addAttribute("todasPagas", todasPagas);
        return "pagamento-lote";
    }

    @GetMapping("/mes/qr.png")
    public ResponseEntity<byte[]> qrCodeMes(@RequestParam String order) {
        return qrCodeLote(order);
    }

    private ResponseEntity<byte[]> qrCodeLote(String order) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        List<Agendamento> consultas = pagamentoConsultaService.listarAgendamentosPorOrderNsu(order, usuarioLogado);
        Agendamento referencia = consultas.get(0);
        if (referencia.getPagamentoLink() == null || referencia.getPagamentoLink().isBlank()) {
            throw new RuntimeException("Link de pagamento ainda nao foi gerado.");
        }
        byte[] png = qrCodeService.gerarPng(referencia.getPagamentoLink(), 280);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    @PostMapping("/{id}/gerar-link")
    public String gerarLink(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            if (pagamentoConsultaService.usaPagamentoPixEmLote(usuarioLogado)) {
                if (pagamentoConsultaService.resolverPeriodicidade(usuarioLogado)
                        == com.clinica.sistema.model.PeriodicidadePagamento.MENSAL) {
                    String order = pagamentoConsultaService.gerarPagamentoUnicoMesVigente(usuarioLogado);
                    return "redirect:/pagamentos/mes?order=" + order;
                }
                String order = pagamentoConsultaService.gerarPagamentoUnicoSemanaAtual(usuarioLogado);
                return "redirect:/pagamentos/semana?order=" + order;
            }
            Agendamento agendamento = pagamentoConsultaService.pagarAgora(id, usuarioLogado);
            return redirecionarModalPixAgenda(agendamento, redirectAttributes);
        } catch (HorarioJaReservadoPorOutroProfissionalException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/agendamentos/meus-pagamentos";
    }

    @PostMapping("/{id}/verificar")
    public String verificarPagamento(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            pagamentoConsultaService.sincronizarPagamentoComInfinitePay(id, usuarioLogado);
            redirectAttributes.addFlashAttribute("exibirModalPixConfirmado", true);
            redirectAttributes.addFlashAttribute("pixConfirmadoAgendamentoId", id);
            redirectAttributes.addFlashAttribute("sucesso", "Pagamento confirmado pela InfinitePay.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
            String referer = request.getHeader("Referer");
            if (referer != null && referer.contains("/pagamentos/")) {
                return "redirect:/pagamentos/" + id;
            }
            return "redirect:/agendamentos/dashboard";
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/{id}/sincronizar-automatico")
    @ResponseBody
    public Map<String, Object> sincronizarAutomatico(@PathVariable Long id) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (pagamentoConsultaService.sincronizarPagamentoAutomaticoSilencioso(id, usuarioLogado)) {
            return Map.of("pago", true);
        }
        pagamentoConsultaService.reconciliarPagamentosInfinitePayPendentes(usuarioLogado);
        Agendamento agendamento = agendamentoRepository.findById(id).orElse(null);
        boolean pago = agendamento != null && agendamento.isPagamentoPago();
        return Map.of("pago", pago);
    }

    @PostMapping("/semana/verificar-atual")
    public String verificarSemanaAtual(RedirectAttributes redirectAttributes) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            PagamentoConsultaService.ReconciliacaoInfinitePayResult resultado =
                    pagamentoConsultaService.reconciliarPagamentosInfinitePayPendentes(usuarioLogado);
            if (resultado.pedidosConfirmados() > 0) {
                redirectAttributes.addFlashAttribute("sucesso", "Pagamento da semana confirmado pela InfinitePay!");
            } else if (resultado.pedidosVerificados() > 0) {
                redirectAttributes.addFlashAttribute(
                        "erro",
                        "InfinitePay ainda não confirmou. Envie o comprovante do PIX para a Polyana."
                );
            } else {
                redirectAttributes.addFlashAttribute(
                        "info",
                        "Não há PIX registrado no sistema. Envie o comprovante para a Polyana confirmar na agenda."
                );
            }
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/agendamentos/meus-pagamentos#pagamentos-semana";
    }

    @PostMapping("/{id}/confirmar-gestor")
    public String confirmarPagamentoGestor(
            @PathVariable Long id,
            @RequestParam(required = false) String origem,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request
    ) {
        Agendamento agendamentoRef = null;
        try {
            Usuario gestor = authService.buscarUsuarioLogadoObrigatorio();
            PagamentoConsultaService.ResultadoConfirmacaoGestor resultado =
                    pagamentoConsultaService.confirmarPagamentoComoGestor(id, gestor);
            Agendamento agendamento = resultado.referencia();
            agendamentoRef = agendamento;
            if (agendamento.getProfissional() != null) {
                auditoriaService.registrarPagamentoConfirmadoGestor(
                        gestor,
                        agendamento.getProfissional(),
                        agendamento,
                        resultado.consultasConfirmadas()
                );
            }
            if (resultado.consultasConfirmadas() > 1) {
                redirectAttributes.addFlashAttribute(
                        "sucesso",
                        "Pagamento confirmado para "
                                + resultado.consultasConfirmadas()
                                + " consulta(s) de "
                                + (agendamento.getProfissional() != null ? agendamento.getProfissional().getNome() : "profissional")
                                + "."
                );
            } else {
                redirectAttributes.addFlashAttribute(
                        "sucesso",
                        "Pagamento confirmado para "
                                + (agendamento.getNomeCliente() != null ? agendamento.getNomeCliente() : "consulta")
                                + "."
                );
            }
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
            agendamentoRef = agendamentoRepository.findById(id).orElse(null);
        }
        if ("confirmar-pagamento".equals(origem) || "auditoria".equals(origem)) {
            Long profId = agendamentoRef != null && agendamentoRef.getProfissional() != null
                    ? agendamentoRef.getProfissional().getId()
                    : null;
            String cliente = agendamentoRef != null && agendamentoRef.getNomeCliente() != null
                    ? agendamentoRef.getNomeCliente().trim()
                    : null;
            if (profId != null && cliente != null && !cliente.isBlank()) {
                return "redirect:/agendamentos/central-profissionais?aba=confirmar-pagamento&profConfirmacao="
                        + profId
                        + "&clienteConfirmacao="
                        + URLEncoder.encode(cliente, StandardCharsets.UTF_8);
            }
            if (profId != null) {
                return "redirect:/agendamentos/central-profissionais?aba=confirmar-pagamento&profConfirmacao=" + profId;
            }
            return "redirect:/agendamentos/central-profissionais?aba=confirmar-pagamento";
        }
        String referer = request.getHeader("Referer");
        if (referer != null && referer.contains("/agendamentos/central-profissionais")) {
            return "redirect:/agendamentos/central-profissionais?aba=confirmar-pagamento";
        }
        if (referer != null && referer.contains("/agendamentos/dashboard")) {
            return "redirect:/agendamentos/dashboard";
        }
        if (referer != null && referer.contains("/agendamentos/meus-pagamentos")) {
            return "redirect:/agendamentos/meus-pagamentos";
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/{id}/simular")
    public String simularPagamento(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            pagamentoConsultaService.simularPagamento(id, usuarioLogado);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
            return "redirect:/agendamentos/meus-pagamentos";
        }
        if (infinitePayProperties.isModoTeste()) {
            redirectAttributes.addFlashAttribute("exibirModalPixConfirmado", true);
            redirectAttributes.addFlashAttribute("pixConfirmadoAgendamentoId", id);
            return "redirect:/agendamentos/dashboard";
        }
        redirectAttributes.addFlashAttribute("sucesso", "Pagamento simulado com sucesso (modo teste).");
        String referer = request.getHeader("Referer");
        if (referer != null && referer.contains("/pagamentos/checkout-teste")) {
            return "redirect:/agendamentos/dashboard";
        }
        return "redirect:/agendamentos/dashboard";
    }

    private String redirecionarModalPixAgenda(Agendamento agendamento, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(
                "sucesso",
                "PIX gerado. Escaneie o QR na tela ou use Pagamentos pendentes se fechar."
        );
        redirectAttributes.addFlashAttribute("pagamentoAgendamentoId", agendamento.getId());
        return "redirect:/agendamentos/dashboard";
    }

    private void validarAcesso(Agendamento agendamento, Usuario usuarioLogado) {
        if (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado)) {
            return;
        }
        if (agendamento.getProfissional() == null
                || !agendamento.getProfissional().getId().equals(usuarioLogado.getId())) {
            throw new RuntimeException("Acesso negado.");
        }
    }
}
