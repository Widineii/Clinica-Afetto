package com.clinica.sistema.controller;

import com.clinica.sistema.dto.ContratoRascunhoView;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.ContratoLicenciamentoService;
import com.clinica.sistema.tools.GeradorContratoPdf;
import com.lowagie.text.DocumentException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
@RequestMapping("/agendamentos/admin")
public class AdminContratoController {

    private static final Logger log = LoggerFactory.getLogger(AdminContratoController.class);
    private static final String NOME_ARQUIVO_PDF = "contrato-agenda-afetto.pdf";

    private final AuthService authService;
    private final ContratoLicenciamentoService contratoLicenciamentoService;

    public AdminContratoController(
            AuthService authService,
            ContratoLicenciamentoService contratoLicenciamentoService
    ) {
        this.authService = authService;
        this.contratoLicenciamentoService = contratoLicenciamentoService;
    }

    @GetMapping("/contrato")
    public String selecionarContrato(Model model, RedirectAttributes redirectAttributes) {
        var usuario = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeAcessarContratoLicenciamento(usuario)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Somente administracao ou dona da clinica podem acessar o contrato."
            );
            redirectAttributes.addFlashAttribute("erroContexto", "contrato");
            return "redirect:/agendamentos/dashboard";
        }

        model.addAttribute("usuarioLogado", usuario);
        model.addAttribute("isAdmin", authService.isAdmin(usuario));
        model.addAttribute("contratoPerfilContratado", authService.podeEditarContratoComoDesenvolvedor(usuario));
        model.addAttribute("contratoSomenteLeitura", authService.contratoSomenteLeitura(usuario));

        String tipoFinalizado = contratoLicenciamentoService.buscarTipoContratoFinalizado();
        if (tipoFinalizado != null) {
            return "redirect:/agendamentos/admin/contrato/" + tipoFinalizado;
        }
        model.addAttribute(
                "contratoAguardandoInicio",
                authService.contratoSomenteLeitura(usuario)
        );
        model.addAttribute(
                "valorBrutoContrato",
                contratoLicenciamentoService.buscarValorCampo("bruto", "pag-valor-total", "2.000,00")
        );
        model.addAttribute(
                "valorMensalContrato",
                contratoLicenciamentoService.buscarValorCampo("mensalidade", "pag-valor-mensal", "200,00")
        );
        return "admin-contrato-selecao";
    }

    @GetMapping("/contrato/{tipo}")
    public String contrato(
            @PathVariable String tipo,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (!ContratoLicenciamentoService.tipoContratoValido(tipo)) {
            return "redirect:/agendamentos/admin/contrato";
        }

        var usuario = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeAcessarContratoLicenciamento(usuario)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Somente administracao ou dona da clinica podem acessar o contrato."
            );
            redirectAttributes.addFlashAttribute("erroContexto", "contrato");
            return "redirect:/agendamentos/dashboard";
        }

        String tipoFinalizado = contratoLicenciamentoService.buscarTipoContratoFinalizado();
        if (tipoFinalizado != null && !tipoFinalizado.equals(tipo)) {
            return "redirect:/agendamentos/admin/contrato/" + tipoFinalizado;
        }

        model.addAttribute("usuarioLogado", usuario);
        model.addAttribute("isAdmin", authService.isAdmin(usuario));
        model.addAttribute("isDonaClinica", authService.isDonaClinica(usuario));
        model.addAttribute("contratoGrupoUsuario", authService.resolverGrupoContratoLicenciamento(usuario));
        model.addAttribute("contratoPerfilContratado", authService.podeEditarContratoComoDesenvolvedor(usuario));
        model.addAttribute("contratoSomenteLeitura", authService.contratoSomenteLeitura(usuario));
        model.addAttribute("contratoTipo", tipo);
        model.addAttribute("contratoTipoRotulo", ContratoLicenciamentoService.rotuloTipoContrato(tipo));
        model.addAttribute("contratoFinalizadoTipo", tipoFinalizado);
        model.addAttribute(
                "contratoFinalizadoTipoRotulo",
                tipoFinalizado != null ? ContratoLicenciamentoService.rotuloTipoContrato(tipoFinalizado) : null
        );
        model.addAttribute(
                "valorBrutoContrato",
                contratoLicenciamentoService.buscarValorCampo("bruto", "pag-valor-total", "2.000,00")
        );
        model.addAttribute(
                "valorMensalContrato",
                contratoLicenciamentoService.buscarValorCampo("mensalidade", "pag-valor-mensal", "200,00")
        );
        return "admin-contrato";
    }

    @GetMapping("/contrato/{tipo}/dados")
    @ResponseBody
    public ContratoRascunhoView buscarDados(@PathVariable String tipo) {
        exigirTipoContrato(tipo);
        exigirAcessoContrato();
        return contratoLicenciamentoService.buscarRascunho(tipo);
    }

    @PostMapping("/contrato/{tipo}/dados")
    @ResponseBody
    public ContratoRascunhoView salvarDados(
            @PathVariable String tipo,
            @RequestBody Map<String, Object> dados
    ) {
        Usuario usuario = exigirAcessoContrato();
        exigirTipoContrato(tipo);
        if (authService.contratoSomenteLeitura(usuario)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este perfil so pode visualizar o contrato.");
        }
        try {
            return contratoLicenciamentoService.salvarRascunho(
                    tipo,
                    usuario,
                    dados,
                    authService.resolverGrupoContratoLicenciamento(usuario)
            );
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
    }

    @PostMapping("/contrato/{tipo}/finalizar")
    @ResponseBody
    public ContratoRascunhoView finalizarContrato(@PathVariable String tipo) {
        Usuario usuario = exigirAcessoContrato();
        exigirTipoContrato(tipo);
        if (!authService.podeEditarDadosContratoClinica(usuario)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Somente a clinica pode finalizar o contrato."
            );
        }
        try {
            return contratoLicenciamentoService.finalizarContratante(tipo, usuario);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/contrato/{tipo}/liberar")
    @ResponseBody
    public ContratoRascunhoView liberarContrato(@PathVariable String tipo) {
        Usuario usuario = exigirAcessoContrato();
        exigirTipoContrato(tipo);
        if (!authService.podeLiberarEdicaoContratoClinica(usuario)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Somente o desenvolvedor pode liberar a edicao da clinica."
            );
        }
        try {
            return contratoLicenciamentoService.liberarContratante(tipo, usuario);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/contrato/pdf")
    public void baixarPdf(HttpServletResponse response) throws IOException {
        exigirAcessoContrato();

        try {
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + NOME_ARQUIVO_PDF + "\""
            );
            GeradorContratoPdf.gerar(response.getOutputStream());
            response.flushBuffer();
        } catch (DocumentException e) {
            log.error("Falha ao gerar PDF do contrato", e);
            if (!response.isCommitted()) {
                response.reset();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Nao foi possivel gerar o PDF.");
            }
        }
    }

    private void exigirTipoContrato(String tipo) {
        if (!ContratoLicenciamentoService.tipoContratoValido(tipo)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de contrato invalido.");
        }
    }

    private Usuario exigirAcessoContrato() {
        Usuario usuario = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeAcessarContratoLicenciamento(usuario)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado.");
        }
        return usuario;
    }
}
