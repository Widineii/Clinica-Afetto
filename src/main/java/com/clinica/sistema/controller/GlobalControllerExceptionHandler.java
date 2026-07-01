package com.clinica.sistema.controller;

import com.clinica.sistema.exception.HorarioJaReservadoPorOutroProfissionalException;
import com.clinica.sistema.exception.PagamentoWebhookNaoAutorizadoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Map;

@ControllerAdvice
public class GlobalControllerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalControllerExceptionHandler.class);
    private static final String MSG_PADRAO = "Não foi possível concluir sua solicitação. Tente novamente.";

    @ExceptionHandler(PagamentoWebhookNaoAutorizadoException.class)
    public ResponseEntity<Map<String, String>> tratarWebhookNaoAutorizado(PagamentoWebhookNaoAutorizadoException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void ignorarRecursoEstaticoAusente(NoResourceFoundException ex) {
        log.debug("Recurso estatico nao encontrado: {}", ex.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public Object tratarExcecaoGlobal(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        if (deveResponderDownload(request)) {
            String mensagem = mensagemUsuario(ex);
            log.warn("Falha em download {}: {}", request.getRequestURI(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(mensagem);
        }
        if (deveResponderJson(request)) {
            String mensagem = mensagemUsuario(ex);
            HttpStatus status = ex instanceof PagamentoWebhookNaoAutorizadoException
                    ? HttpStatus.UNAUTHORIZED
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("erro", mensagem));
        }

        String destino = destinoPorRota(request);
        String mensagem = mensagemUsuario(ex);
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        flashMap.put("erro", mensagem);
        flashMap.put("erroContexto", contextoPorRota(request));
        RequestContextUtils.saveOutputFlashMap(destino, request, response);

        log.warn("Excecao tratada globalmente em {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return "redirect:" + destino;
    }

    private boolean deveResponderDownload(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (uri.contains("/download") || uri.contains("/exportar-pdf"));
    }

    private boolean deveResponderJson(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return true;
        }
        String xRequestedWith = request.getHeader("X-Requested-With");
        return xRequestedWith != null && xRequestedWith.equalsIgnoreCase("XMLHttpRequest");
    }

    private String destinoPorRota(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "/agendamentos/dashboard";
        }
        if (uri.startsWith("/login") || uri.startsWith("/logout") || uri.startsWith("/primeiro-acesso")) {
            return "/login";
        }
        if (uri.startsWith("/senha")) {
            return "/senha/esqueci";
        }
        if (uri.contains("/central-profissionais")) {
            return "/agendamentos/central-profissionais?aba=equipe";
        }
        if (uri.contains("/meus-pacientes/")) {
            int inicio = uri.indexOf("/meus-pacientes/");
            int fim = uri.indexOf('/', inicio + "/meus-pacientes/".length());
            if (fim > inicio) {
                return uri.substring(0, fim);
            }
            return uri;
        }
        return "/agendamentos/dashboard";
    }

    private String contextoPorRota(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "agendamento";
        }
        if (uri.contains("/admin/uso-banco")) {
            return "uso-banco";
        }
        if (uri.contains("/admin/arquivo-sistema")) {
            return "arquivo-sistema";
        }
        if (uri.startsWith("/relatorios")) {
            return "relatorio";
        }
        if (uri.startsWith("/senha") || uri.startsWith("/primeiro-acesso")) {
            return "senha";
        }
        if (uri.contains("/central-profissionais")) {
            return "central";
        }
        return "agendamento";
    }

    private String mensagemUsuario(Exception ex) {
        if (ex instanceof HorarioJaReservadoPorOutroProfissionalException) {
            return ex.getMessage();
        }
        if (ex instanceof RuntimeException && ex.getMessage() != null && isMensagemNegocio(ex.getMessage())) {
            return ex.getMessage();
        }
        return MSG_PADRAO;
    }

    private boolean isMensagemNegocio(String mensagem) {
        if (mensagem.isBlank() || mensagem.length() > 220) {
            return false;
        }
        String lower = mensagem.toLowerCase();
        return !lower.contains("exception")
                && !lower.contains("org.")
                && !lower.contains("java.")
                && !lower.contains("nullpointer");
    }
}
