package com.clinica.sistema.controller;

import com.clinica.sistema.dto.RelatorioProfissionalMesView;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.RelatorioProfissionalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;
import java.util.Arrays;

@Controller
@RequestMapping("/agendamentos/meu-relatorio")
public class RelatorioProfissionalController {

    private static final Logger log = LoggerFactory.getLogger(RelatorioProfissionalController.class);

    private final RelatorioProfissionalService relatorioProfissionalService;
    private final AuthService authService;
    private final Environment environment;

    public RelatorioProfissionalController(
            RelatorioProfissionalService relatorioProfissionalService,
            AuthService authService,
            Environment environment
    ) {
        this.relatorioProfissionalService = relatorioProfissionalService;
        this.authService = authService;
        this.environment = environment;
    }

    private boolean isPerfilLocal() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }

    @GetMapping
    public String meuRelatorio(
            Model model,
            RedirectAttributes redirectAttributes,
            @RequestParam(required = false) String mesAno,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) String sala
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeVerRelatorioProprio(usuarioLogado)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Relatorio individual disponivel apenas para profissionais."
            );
            return "redirect:/agendamentos/dashboard";
        }

        YearMonth mesSelecionado = resolverMes(mesAno, mes, ano);
        String salaFiltro = relatorioProfissionalService.resolverSalaFiltro(sala);
        boolean exibirTaxasRelatorio = !authService.profissionalIgnoraValoresEPagamento(usuarioLogado);
        try {
            RelatorioProfissionalMesView relatorio =
                    relatorioProfissionalService.montarRelatorio(
                            usuarioLogado,
                            mesSelecionado,
                            sala,
                            exibirTaxasRelatorio
                    );
            model.addAttribute("relatorio", relatorio);
        } catch (RuntimeException ex) {
            log.error("Falha ao montar relatorio do profissional {}", usuarioLogado.getId(), ex);
            model.addAttribute("relatorio", RelatorioProfissionalMesView.vazio(
                    mesSelecionado,
                    usuarioLogado.getNome(),
                    salaFiltro,
                    exibirTaxasRelatorio
            ));
            model.addAttribute("erro", "Nao foi possivel carregar seu relatorio. Tente novamente.");
        }

        model.addAttribute("exibirTaxasRelatorio", exibirTaxasRelatorio);

        model.addAttribute("salasFiltro", RelatorioProfissionalService.SALAS_FILTRO);
        model.addAttribute("perfilLocal", isPerfilLocal());

        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", authService.isAdmin(usuarioLogado));
        return "meu-relatorio";
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
