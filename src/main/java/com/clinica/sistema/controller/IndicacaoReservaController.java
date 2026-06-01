package com.clinica.sistema.controller;

import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.IndicacaoReservaService;
import com.clinica.sistema.service.PagamentoConsultaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/indicacoes")
public class IndicacaoReservaController {

    private final IndicacaoReservaService indicacaoReservaService;
    private final PagamentoConsultaService pagamentoConsultaService;
    private final AuthService authService;

    public IndicacaoReservaController(
            IndicacaoReservaService indicacaoReservaService,
            PagamentoConsultaService pagamentoConsultaService,
            AuthService authService
    ) {
        this.indicacaoReservaService = indicacaoReservaService;
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.authService = authService;
    }

    @GetMapping("/pendentes")
    public String pendentes() {
        var usuario = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.isDonaClinica(usuario) && !authService.isAdmin(usuario)) {
            throw new RuntimeException("Acesso negado.");
        }
        return "redirect:/agendamentos/central-profissionais?aba=indicacoes";
    }

    @PostMapping("/{id}/aprovar")
    public String aprovar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            var usuario = authService.buscarUsuarioLogadoObrigatorio();
            var agendamento = indicacaoReservaService.aprovarReservaIndicacao(id, usuario);
            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    "Indicação aprovada para "
                            + agendamento.getNomeCliente()
                            + ". O profissional poderá pagar pelo site após o atendimento."
            );
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/agendamentos/central-profissionais?aba=indicacoes";
    }
}
