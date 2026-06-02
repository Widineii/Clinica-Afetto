package com.clinica.sistema.controller;



import com.clinica.sistema.dto.ReceitaPixMesView;

import com.clinica.sistema.model.Usuario;

import com.clinica.sistema.service.AuthService;

import com.clinica.sistema.service.EncerramentoSerieNotificacaoService;
import com.clinica.sistema.service.NovoAgendamentoNotificacaoService;

import com.clinica.sistema.service.FinanceiroPolyanaAcessoService;

import com.clinica.sistema.service.FinanceiroReceitaPixService;

import com.clinica.sistema.service.RelatorioMensalService;

import jakarta.servlet.http.HttpSession;

import org.springframework.core.env.Environment;

import org.springframework.stereotype.Controller;

import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;



import java.time.YearMonth;

import java.util.Arrays;



@Controller

@RequestMapping("/agendamentos/financeiro")

public class FinanceiroController {



    private final FinanceiroReceitaPixService receitaPixService;

    private final FinanceiroPolyanaAcessoService acessoService;

    private final AuthService authService;

    private final RelatorioMensalService relatorioMensalService;

    private final EncerramentoSerieNotificacaoService encerramentoSerieNotificacaoService;

    private final NovoAgendamentoNotificacaoService novoAgendamentoNotificacaoService;

    private final Environment environment;



    public FinanceiroController(

            FinanceiroReceitaPixService receitaPixService,

            FinanceiroPolyanaAcessoService acessoService,

            AuthService authService,

            RelatorioMensalService relatorioMensalService,

            EncerramentoSerieNotificacaoService encerramentoSerieNotificacaoService,

            NovoAgendamentoNotificacaoService novoAgendamentoNotificacaoService,

            Environment environment

    ) {

        this.receitaPixService = receitaPixService;

        this.acessoService = acessoService;

        this.authService = authService;

        this.relatorioMensalService = relatorioMensalService;

        this.encerramentoSerieNotificacaoService = encerramentoSerieNotificacaoService;

        this.novoAgendamentoNotificacaoService = novoAgendamentoNotificacaoService;

        this.environment = environment;

    }



    private boolean isPerfilLocal() {

        return Arrays.asList(environment.getActiveProfiles()).contains("local");

    }



    @GetMapping

    public String painel(

            Model model,

            HttpSession session,

            RedirectAttributes redirectAttributes,

            @RequestParam(required = false) String mesAno,

            @RequestParam(required = false) Integer mes,

            @RequestParam(required = false) Integer ano

    ) {

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

        model.addAttribute("perfilLocal", isPerfilLocal());

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



    /** Tela antiga removida: mantem links legados apontando para o painel PIX. */

    @GetMapping("/configuracao-taxas")

    public String legadoConfiguracaoTaxas() {

        return "redirect:/agendamentos/financeiro";

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

        model.addAttribute("isAdmin", authService.isAdmin(usuarioLogado));

        model.addAttribute("podeGerenciarEquipe", authService.podeGerenciarEquipe(usuarioLogado));

        relatorioMensalService.adicionarNotificacaoAoModelSeAplicavel(model, session);

        encerramentoSerieNotificacaoService.adicionarNotificacaoAoModelSeAplicavel(model, session);

        novoAgendamentoNotificacaoService.adicionarNotificacaoAoModelSeAplicavel(model, session);

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

}

