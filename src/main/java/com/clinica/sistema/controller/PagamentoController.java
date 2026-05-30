package com.clinica.sistema.controller;

import com.clinica.sistema.config.InfinitePayProperties;
import com.clinica.sistema.exception.HorarioJaReservadoPorOutroProfissionalException;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.PagamentoConsultaService;
import com.clinica.sistema.service.QrCodeService;
import jakarta.servlet.http.HttpServletRequest;
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

    public PagamentoController(
            PagamentoConsultaService pagamentoConsultaService,
            AgendamentoRepository agendamentoRepository,
            AuthService authService,
            QrCodeService qrCodeService,
            InfinitePayProperties infinitePayProperties
    ) {
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.agendamentoRepository = agendamentoRepository;
        this.authService = authService;
        this.qrCodeService = qrCodeService;
        this.infinitePayProperties = infinitePayProperties;
    }

    @GetMapping("/{id}")
    public String paginaPagamento(@PathVariable Long id, Model model) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        Agendamento agendamento = agendamentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));
        validarAcesso(agendamento, usuarioLogado);

        model.addAttribute("agendamento", agendamento);
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("rotuloStatus", pagamentoConsultaService.rotuloStatusPagamento(agendamento));
        model.addAttribute("bloqueado", pagamentoConsultaService.bloqueadoPorPagamento(agendamento));
        model.addAttribute("vagaPreenchida", pagamentoConsultaService.vagaPreenchidaPorOutroProfissional(agendamento));
        model.addAttribute("podeRecuperarVaga", pagamentoConsultaService.podeRecuperarVagaComPagamento(agendamento));
        return "pagamento-consulta";
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
            if (PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())) {
                redirectAttributes.addFlashAttribute(
                        "sucesso",
                        "Pagamento gerado. Escaneie o QR ou abra o link PIX ("
                                + pagamentoConsultaService.prazoConfirmacaoMinutos()
                                + " min para confirmar)."
                );
            }
            return "redirect:/pagamentos/" + agendamento.getId();
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
            String orderNsu = pagamentoConsultaService.gerarPagamentoUnicoPendentesSelecionados(
                    usuarioLogado,
                    agendamentoIds
            );
            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    "PIX unico do proximo dia gerado. Pague uma vez para quitar as consultas selecionadas."
            );
            return "redirect:/pagamentos/dia?order=" + orderNsu;
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
            String orderNsu = pagamentoConsultaService.gerarPagamentoUnicoSemanaAtual(usuarioLogado);
            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    "PIX unico da semana gerado. Pague uma vez para quitar todas as consultas listadas."
            );
            return "redirect:/pagamentos/semana?order=" + orderNsu;
        } catch (HorarioJaReservadoPorOutroProfissionalException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/agendamentos/meus-pagamentos#pagamentos-semana";
    }

    @PostMapping("/gerar-links-mes-anterior")
    public String gerarLinksMesAnterior(RedirectAttributes redirectAttributes) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            String orderNsu = pagamentoConsultaService.gerarPagamentoUnicoMesAnterior(usuarioLogado);
            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    "PIX unico do mes anterior gerado. Pague uma vez para quitar todas as consultas listadas."
            );
            return "redirect:/pagamentos/mes?order=" + orderNsu;
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
        model.addAttribute("tituloLote", "Pagamento do mes anterior");
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
            Agendamento agendamento = pagamentoConsultaService.gerarLinkPagamento(id, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Link de pagamento gerado.");
            return "redirect:/pagamentos/" + agendamento.getId();
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
