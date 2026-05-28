package com.clinica.sistema.controller;

import com.clinica.sistema.dto.ConfiguracaoTaxasAtendimentosView;
import com.clinica.sistema.dto.FinanceiroFiltroMesProfissionalView;
import com.clinica.sistema.dto.ReceitaPixMesView;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AgendamentoService;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.FinanceiroPolyanaAcessoService;
import com.clinica.sistema.service.FinanceiroReceitaPixService;
import com.clinica.sistema.service.PagamentoConsultaService;
import com.clinica.sistema.service.RelatorioMensalService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;

@Controller
@RequestMapping("/agendamentos/financeiro")
public class FinanceiroController {

    private final FinanceiroReceitaPixService receitaPixService;
    private final FinanceiroPolyanaAcessoService acessoService;
    private final AuthService authService;
    private final RelatorioMensalService relatorioMensalService;
    private final AgendamentoService agendamentoService;
    private final PagamentoConsultaService pagamentoConsultaService;

    public FinanceiroController(
            FinanceiroReceitaPixService receitaPixService,
            FinanceiroPolyanaAcessoService acessoService,
            AuthService authService,
            RelatorioMensalService relatorioMensalService,
            AgendamentoService agendamentoService,
            PagamentoConsultaService pagamentoConsultaService
    ) {
        this.receitaPixService = receitaPixService;
        this.acessoService = acessoService;
        this.authService = authService;
        this.relatorioMensalService = relatorioMensalService;
        this.agendamentoService = agendamentoService;
        this.pagamentoConsultaService = pagamentoConsultaService;
    }

    @GetMapping
    public String painel(
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            @RequestParam(required = false) String aba,
            @RequestParam(required = false) String mesAno,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano
    ) {
        if ("consultas".equalsIgnoreCase(aba)) {
            return "redirect:/agendamentos/financeiro/configuracao-taxas";
        }

        String bloqueio = verificarAcesso(model, session, redirectAttributes);
        if (bloqueio != null) {
            return bloqueio;
        }

        YearMonth mesSelecionado = resolverMes(mesAno, mes, ano);
        try {
            model.addAttribute("receita", receitaPixService.montarResumoMes(mesSelecionado));
        } catch (RuntimeException ex) {
            model.addAttribute("receita", ReceitaPixMesView.vazio(mesSelecionado));
            model.addAttribute("erro", "Nao foi possivel carregar a receita PIX: " + ex.getMessage());
        }
        model.addAttribute("mostrarConfiguracaoTaxas", true);
        model.addAttribute("mostrarGestaoFinanceira", false);
        return "financeiro";
    }

    /** Formulario antigo de despesas: redireciona para a tela de receita PIX. */
    @PostMapping
    public String legadoDespesas(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(
                "sucesso",
                "A gestao financeira agora mostra a receita PIX confirmada por mes."
        );
        return "redirect:/agendamentos/financeiro";
    }

    @GetMapping("/configuracao-taxas")
    public String configuracaoTaxas(
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            @RequestParam(required = false) String mesAno,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Long profissionalId
    ) {
        String bloqueio = verificarAcesso(model, session, redirectAttributes);
        if (bloqueio != null) {
            return bloqueio;
        }

        YearMonth mesSelecionado = resolverMes(mesAno, mes, ano);
        var profissionais = agendamentoService.listarProfissionaisComAgendamentoNoMes(mesSelecionado);
        Usuario profissionalSelecionado = resolverProfissional(profissionalId, profissionais);

        model.addAttribute(
                "filtro",
                new FinanceiroFiltroMesProfissionalView(mesSelecionado, profissionalSelecionado, profissionais)
        );
        model.addAttribute(
                "atendimentosView",
                profissionalSelecionado != null
                        ? agendamentoService.montarAtendimentosConfiguracaoTaxas(
                                profissionalSelecionado.getId(),
                                mesSelecionado
                        )
                        : ConfiguracaoTaxasAtendimentosView.vazio()
        );
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        var profissionaisBloqueados = pagamentoConsultaService.listarProfissionaisBloqueadosPorPagamento();
        model.addAttribute("profissionaisBloqueadosPagamento", profissionaisBloqueados);
        model.addAttribute("mostrarConfiguracaoTaxas", false);
        model.addAttribute("mostrarGestaoFinanceira", true);
        return "financeiro-configuracao-taxas";
    }

    private String verificarAcesso(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!acessoService.podeAcessarGestaoFinanceira(usuarioLogado)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Gestao financeira nao disponivel para este usuario."
            );
            return "redirect:/agendamentos/dashboard";
        }

        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isDonaClinica", authService.isDonaClinica(usuarioLogado));
        relatorioMensalService.adicionarNotificacaoAoModelSeAplicavel(model, session);
        return null;
    }

    private YearMonth resolverMes(String mesAno, Integer mes, Integer ano) {
        if (mesAno != null && !mesAno.isBlank()) {
            return YearMonth.parse(mesAno);
        }
        if (mes != null && ano != null) {
            return YearMonth.of(ano, mes);
        }
        return YearMonth.now();
    }

    private Usuario resolverProfissional(Long profissionalId, java.util.List<Usuario> profissionais) {
        if (profissionais == null || profissionais.isEmpty()) {
            return null;
        }
        if (profissionalId != null) {
            return profissionais.stream()
                    .filter(profissional -> profissionalId.equals(profissional.getId()))
                    .findFirst()
                    .orElse(profissionais.get(0));
        }
        return profissionais.get(0);
    }
}
