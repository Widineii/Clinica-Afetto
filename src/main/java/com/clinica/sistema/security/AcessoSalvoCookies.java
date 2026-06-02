package com.clinica.sistema.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Cookies para preencher login e senha na tela de entrada neste aparelho.
 * A senha e armazenada apenas em Base64 (ofuscacao), nao criptografia forte.
 */
public final class AcessoSalvoCookies {

    public static final String COOKIE_LOGIN = "afetto_login";
    public static final String COOKIE_SENHA = "afetto_senha_salva";

    private AcessoSalvoCookies() {
    }

    public static String codificarValor(String valor) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodificarValor(String codificado) {
        if (codificado == null || codificado.isBlank()) {
            return "";
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(codificado.trim());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    public static void salvarLogin(HttpServletResponse response, HttpServletRequest request, String login, int maxAgeSegundos) {
        if (login == null || login.isBlank()) {
            return;
        }
        definirCookie(response, request, COOKIE_LOGIN, login.trim().toLowerCase(), maxAgeSegundos);
    }

    public static void salvarSenha(HttpServletResponse response, HttpServletRequest request, String senha, int maxAgeSegundos) {
        if (senha == null || senha.isBlank()) {
            return;
        }
        definirCookie(response, request, COOKIE_SENHA, codificarValor(senha), maxAgeSegundos);
    }

    public static void removerSenha(HttpServletResponse response, HttpServletRequest request) {
        definirCookie(response, request, COOKIE_SENHA, "", 0);
    }

    public static void removerLogin(HttpServletResponse response, HttpServletRequest request) {
        definirCookie(response, request, COOKIE_LOGIN, "", 0);
    }

    public static void removerTudo(HttpServletResponse response, HttpServletRequest request) {
        removerLogin(response, request);
        removerSenha(response, request);
    }

    private static void definirCookie(
            HttpServletResponse response,
            HttpServletRequest request,
            String nome,
            String valor,
            int maxAgeSegundos
    ) {
        Cookie cookie = new Cookie(nome, valor != null ? valor : "");
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSegundos);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        response.addCookie(cookie);
    }
}
