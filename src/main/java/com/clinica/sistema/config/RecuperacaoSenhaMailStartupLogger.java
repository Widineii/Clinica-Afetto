package com.clinica.sistema.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class RecuperacaoSenhaMailStartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaMailStartupLogger.class);

    private final RecuperacaoSenhaProperties recuperacaoSenhaProperties;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public RecuperacaoSenhaMailStartupLogger(RecuperacaoSenhaProperties recuperacaoSenhaProperties) {
        this.recuperacaoSenhaProperties = recuperacaoSenhaProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!recuperacaoSenhaProperties.isEnabled()) {
            log.info("Recuperacao de senha: desligada.");
            return;
        }
        if (recuperacaoSenhaProperties.isModoConsola() || !mailConfigurado()) {
            log.warn(
                    "Recuperacao de senha: codigos no LOG (modo consola). "
                            + "Defina MAIL_PASSWORD e RECUPERACAO_SENHA_MODO_CONSOLA=false para enviar e-mail."
            );
            return;
        }
        log.info(
                "Recuperacao de senha: envio por e-mail ativo ({} via {}).",
                recuperacaoSenhaProperties.getRemetenteEmail(),
                mailHost
        );
    }

    private boolean mailConfigurado() {
        return mailHost != null && !mailHost.isBlank()
                && mailUsername != null && !mailUsername.isBlank()
                && mailPassword != null && !mailPassword.isBlank();
    }
}
