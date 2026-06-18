package com.clinica.sistema.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecuperacaoSenhaEmailTemplateTest {

    @Test
    void deveMontarHtmlComLogoInlineECodigo() {
        var conteudo = RecuperacaoSenhaEmailTemplate.montar(
                "061439",
                15,
                "http://177.153.203.126:8080"
        );

        assertTrue(conteudo.textoPlano().contains("061439"));
        assertTrue(conteudo.html().contains("cid:afetto-logo"));
        assertTrue(conteudo.html().contains("061439"));
        assertTrue(conteudo.html().contains("Clínica Afetto"));
        assertTrue(conteudo.html().contains("http://177.153.203.126:8080"));
        assertTrue(conteudo.html().contains("Redefinir sua senha"));
    }
}
