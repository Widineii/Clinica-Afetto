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
    public String contrato(Model model, RedirectAttributes redirectAttributes) {
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
        model.addAttribute("isDonaClinica", authService.isDonaClinica(usuario));
        model.addAttribute("contratoGrupoUsuario", authService.resolverGrupoContratoLicenciamento(usuario));
        model.addAttribute("contratoPerfilContratado", authService.isAdmin(usuario));
        return "admin-contrato";
    }

    @GetMapping("/contrato/dados")
    @ResponseBody
    public ContratoRascunhoView buscarDados() {
        exigirAcessoContrato();
        return contratoLicenciamentoService.buscarRascunho();
    }

    @PostMapping("/contrato/dados")
    @ResponseBody
    public ContratoRascunhoView salvarDados(@RequestBody Map<String, Object> dados) {
        Usuario usuario = exigirAcessoContrato();
        try {
            return contratoLicenciamentoService.salvarRascunho(
                    usuario,
                    dados,
                    authService.resolverGrupoContratoLicenciamento(usuario)
            );
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
    }

    @PostMapping("/contrato/finalizar")
    @ResponseBody
    public ContratoRascunhoView finalizarContrato() {
        Usuario usuario = exigirAcessoContrato();
        if (!ContratoLicenciamentoService.GRUPO_CONTRATANTE.equals(
                authService.resolverGrupoContratoLicenciamento(usuario)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Somente a clinica pode finalizar o contrato."
            );
        }
        try {
            return contratoLicenciamentoService.finalizarContratante(usuario);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/contrato/liberar")
    @ResponseBody
    public ContratoRascunhoView liberarContrato() {
        Usuario usuario = exigirAcessoContrato();
        if (!authService.isAdmin(usuario)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Somente o desenvolvedor pode liberar a edicao da clinica."
            );
        }
        try {
            return contratoLicenciamentoService.liberarContratante(usuario);
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

    private Usuario exigirAcessoContrato() {
        Usuario usuario = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeAcessarContratoLicenciamento(usuario)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado.");
        }
        return usuario;
    }
}
