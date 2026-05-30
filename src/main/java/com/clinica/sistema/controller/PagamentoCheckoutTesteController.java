package com.clinica.sistema.controller;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.PagamentoConsultaService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Checkout de pagamento apenas para desenvolvimento local (profile {@code local}).
 */
@Profile("local")
@Controller
@RequestMapping("/pagamentos")
public class PagamentoCheckoutTesteController {

    private final PagamentoConsultaService pagamentoConsultaService;
    private final AgendamentoRepository agendamentoRepository;
    private final AuthService authService;

    public PagamentoCheckoutTesteController(
            PagamentoConsultaService pagamentoConsultaService,
            AgendamentoRepository agendamentoRepository,
            AuthService authService
    ) {
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.agendamentoRepository = agendamentoRepository;
        this.authService = authService;
    }

    @GetMapping("/checkout-teste")
    public String checkoutTeste(
            @RequestParam String order,
            @RequestParam Long agendamento,
            Model model
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        Agendamento registro = agendamentoRepository.findById(agendamento)
                .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));
        validarAcesso(registro, usuarioLogado);
        if (registro.getPagamentoOrderNsu() == null || !registro.getPagamentoOrderNsu().equals(order)) {
            throw new RuntimeException("Pedido de pagamento invalido.");
        }
        model.addAttribute("agendamento", registro);
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("orderNsu", order);
        return "pagamento-checkout-teste";
    }

    @PostMapping("/checkout-teste/confirmar")
    public String confirmarCheckoutTeste(
            @RequestParam String order,
            @RequestParam Long agendamento,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            Agendamento registro = agendamentoRepository.findById(agendamento)
                    .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));
            validarAcesso(registro, usuarioLogado);
            if (registro.getPagamentoOrderNsu() == null || !registro.getPagamentoOrderNsu().equals(order)) {
                throw new RuntimeException("Pedido de pagamento invalido.");
            }
            pagamentoConsultaService.confirmarPagamentoPorOrderNsuModoTeste(order);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
            return "redirect:/agendamentos/dashboard";
        }
        redirectAttributes.addFlashAttribute("exibirModalPixConfirmado", true);
        redirectAttributes.addFlashAttribute("pixConfirmadoAgendamentoId", agendamento);
        return "redirect:/agendamentos/dashboard";
    }

    @GetMapping("/checkout-teste-dia")
    public String checkoutTesteDia(@RequestParam String order, Model model) {
        return checkoutTesteLote(order, model, "Pagamento do proximo dia", "Confirmar pagamento PIX");
    }

    @PostMapping("/checkout-teste-dia/confirmar")
    public String confirmarCheckoutTesteDia(
            @RequestParam String order,
            RedirectAttributes redirectAttributes
    ) {
        return confirmarCheckoutTesteLote(order, redirectAttributes, "Pagamento do proximo dia confirmado (modo teste).");
    }

    @GetMapping("/checkout-teste-semana")
    public String checkoutTesteSemana(@RequestParam String order, Model model) {
        return checkoutTesteLote(order, model, "PIX unico da semana", "Confirmar pagamento PIX");
    }

    @PostMapping("/checkout-teste-semana/confirmar")
    public String confirmarCheckoutTesteSemana(
            @RequestParam String order,
            RedirectAttributes redirectAttributes
    ) {
        return confirmarCheckoutTesteLote(order, redirectAttributes, "Pagamento da semana confirmado (modo teste).");
    }

    @GetMapping("/checkout-teste-mes")
    public String checkoutTesteMes(@RequestParam String order, Model model) {
        return checkoutTesteLote(order, model, "PIX unico do mes", "Confirmar pagamento PIX");
    }

    @PostMapping("/checkout-teste-mes/confirmar")
    public String confirmarCheckoutTesteMes(
            @RequestParam String order,
            RedirectAttributes redirectAttributes
    ) {
        return confirmarCheckoutTesteLote(order, redirectAttributes, "Pagamento do mes confirmado (modo teste).");
    }

    private String checkoutTesteLote(String order, Model model, String titulo, String rotuloBotao) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        List<Agendamento> consultas = pagamentoConsultaService.listarAgendamentosPorOrderNsu(order, usuarioLogado);
        model.addAttribute("consultasLote", consultas);
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("orderNsu", order);
        model.addAttribute("totalTaxaLote", pagamentoConsultaService.formatarTotalTaxaPix(consultas));
        model.addAttribute("tituloLote", titulo);
        model.addAttribute("rotuloBotao", rotuloBotao);
        model.addAttribute("acaoConfirmar", order.startsWith("dia-")
                ? "/pagamentos/checkout-teste-dia/confirmar"
                : order.startsWith("mes-")
                        ? "/pagamentos/checkout-teste-mes/confirmar"
                        : "/pagamentos/checkout-teste-semana/confirmar");
        return "pagamento-checkout-teste-lote";
    }

    private String confirmarCheckoutTesteLote(
            String order,
            RedirectAttributes redirectAttributes,
            String mensagemSucesso
    ) {
        try {
            authService.buscarUsuarioLogadoObrigatorio();
            pagamentoConsultaService.confirmarPagamentoPorOrderNsuModoTeste(order);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
            return "redirect:/agendamentos/meus-pagamentos";
        }
        redirectAttributes.addFlashAttribute("exibirModalPixConfirmado", true);
        return "redirect:/agendamentos/meus-pagamentos";
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
