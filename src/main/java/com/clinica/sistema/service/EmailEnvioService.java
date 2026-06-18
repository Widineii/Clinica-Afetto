package com.clinica.sistema.service;

import com.clinica.sistema.config.RecuperacaoSenhaProperties;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class EmailEnvioService {

    private static final Logger log = LoggerFactory.getLogger(EmailEnvioService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final RecuperacaoSenhaProperties recuperacaoSenhaProperties;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public EmailEnvioService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            RecuperacaoSenhaProperties recuperacaoSenhaProperties
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.recuperacaoSenhaProperties = recuperacaoSenhaProperties;
    }

    public void enviarCodigoRecuperacaoSenha(String destinatario, String codigo) {
        String assunto = "Código para redefinir sua senha — Agenda Afetto";
        RecuperacaoSenhaEmailTemplate.ConteudoEmail conteudo = RecuperacaoSenhaEmailTemplate.montar(
                codigo,
                recuperacaoSenhaProperties.getCodigoExpiracaoMinutos(),
                recuperacaoSenhaProperties.getUrlSite()
        );

        if (deveUsarModoConsola()) {
            log.warn("[RecuperacaoSenha] modo consola — destino={} codigo={}", destinatario, codigo);
            if (!recuperacaoSenhaProperties.isModoConsola()) {
                throw new RuntimeException(
                        "Envio de e-mail indisponivel no servidor. Informe a administracao (MAIL_PASSWORD / SMTP)."
                );
            }
            return;
        }

        enviarHtmlInterno(destinatario, assunto, conteudo.textoPlano(), conteudo.html(), RecuperacaoSenhaEmailTemplate.LOGO_CONTENT_ID);
        log.info("[RecuperacaoSenha] codigo enviado para {}", mascararEmail(destinatario));
    }

    public boolean enviarHtml(String destinatario, String assunto, String textoPlano, String html) {
        if (deveUsarModoConsolaAviso()) {
            log.warn("[AvisoPagamentoEmail] modo consola — destino={} assunto={}", mascararEmail(destinatario), assunto);
            return true;
        }
        try {
            enviarHtmlInterno(destinatario, assunto, textoPlano, html, AvisoPagamentoEmailTemplate.LOGO_CONTENT_ID);
            log.info("[AvisoPagamentoEmail] enviado para {}", mascararEmail(destinatario));
            return true;
        } catch (RuntimeException ex) {
            log.error("[AvisoPagamentoEmail] falha para {}: {}", mascararEmail(destinatario), ex.getMessage());
            return false;
        }
    }

    public boolean envioRealDisponivel() {
        return !deveUsarModoConsolaAviso();
    }

    private void enviarHtmlInterno(
            String destinatario,
            String assunto,
            String textoPlano,
            String html,
            String logoContentId
    ) {
        try {
            JavaMailSender mailSender = mailSenderProvider.getObject();
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, StandardCharsets.UTF_8.name());
            helper.setFrom(remetenteFormatado());
            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(textoPlano, html);
            helper.addInline(logoContentId, new ClassPathResource("static/images/logo-afetto.png"));
            mailSender.send(mensagem);
        } catch (Exception ex) {
            throw new RuntimeException("Nao foi possivel enviar o e-mail agora. Tente novamente em alguns minutos.", ex);
        }
    }

    private boolean deveUsarModoConsolaAviso() {
        return mailSenderProvider.getIfAvailable() == null || !mailConfigurado();
    }

    private boolean deveUsarModoConsola() {
        return recuperacaoSenhaProperties.isModoConsola()
                || mailSenderProvider.getIfAvailable() == null
                || !mailConfigurado();
    }

    private boolean mailConfigurado() {
        return mailUsername != null && !mailUsername.isBlank()
                && mailPassword != null && !mailPassword.isBlank();
    }

    private InternetAddress remetenteFormatado() throws Exception {
        String email = recuperacaoSenhaProperties.getRemetenteEmail();
        if (email == null || email.isBlank()) {
            email = mailUsername;
        }
        String nome = recuperacaoSenhaProperties.getRemetenteNome();
        if (nome == null || nome.isBlank()) {
            return new InternetAddress(email);
        }
        return new InternetAddress(email, nome, StandardCharsets.UTF_8.name());
    }

    private String mascararEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int arroba = email.indexOf('@');
        String local = email.substring(0, arroba);
        String dominio = email.substring(arroba);
        if (local.length() <= 2) {
            return "**" + dominio;
        }
        return local.substring(0, 2) + "***" + dominio;
    }
}
