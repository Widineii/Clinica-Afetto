package com.clinica.sistema.service;

import com.clinica.sistema.config.RecuperacaoSenhaProperties;
import com.clinica.sistema.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ContaAprovadaEmailService {

    private static final Logger log = LoggerFactory.getLogger(ContaAprovadaEmailService.class);

    private final EmailEnvioService emailEnvioService;
    private final RecuperacaoSenhaProperties recuperacaoSenhaProperties;

    public ContaAprovadaEmailService(
            EmailEnvioService emailEnvioService,
            RecuperacaoSenhaProperties recuperacaoSenhaProperties
    ) {
        this.emailEnvioService = emailEnvioService;
        this.recuperacaoSenhaProperties = recuperacaoSenhaProperties;
    }

    public void notificarContaLiberada(Usuario usuario) {
        if (usuario == null || !"PUBLICO".equalsIgnoreCase(usuario.getOrigemCadastro())) {
            return;
        }
        String email = usuario.getEmail();
        if (email == null || email.isBlank()) {
            return;
        }

        String urlLogin = recuperacaoSenhaProperties.getUrlSite();
        ContaAprovadaEmailTemplate.ConteudoEmail conteudo = ContaAprovadaEmailTemplate.montar(
                usuario.getNome(),
                usuario.getLogin(),
                urlLogin
        );
        boolean enviado = emailEnvioService.enviarHtml(
                email,
                "Sua conta foi liberada — Agenda Afetto",
                conteudo.textoPlano(),
                conteudo.html()
        );
        if (!enviado) {
            log.warn(
                    "[ContaAprovadaEmail] nao foi possivel enviar e-mail de liberacao para login={}",
                    usuario.getLogin()
            );
        }
    }
}
