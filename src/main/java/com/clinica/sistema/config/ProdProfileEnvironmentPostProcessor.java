package com.clinica.sistema.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Ativa o perfil {@code prod} quando o host injeta variaveis de PostgreSQL ou e Railway/Render.
 */
public class ProdProfileEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getActiveProfiles().length > 0) {
            return;
        }

        if (temVariavel(environment, "DATABASE_URL")
                || temVariavel(environment, "DATABASE_PUBLIC_URL")
                || temPgHostRemoto(environment)
                || temVariavel(environment, "RAILWAY_ENVIRONMENT")
                || temVariavel(environment, "RENDER")) {
            environment.setActiveProfiles("prod");
        }
    }

    private static boolean temVariavel(ConfigurableEnvironment environment, String nome) {
        String valor = environment.getProperty(nome);
        return valor != null && !valor.isBlank();
    }

    private static boolean temPgHostRemoto(ConfigurableEnvironment environment) {
        String pgHost = environment.getProperty("PGHOST");
        return pgHost != null && !pgHost.isBlank() && !"localhost".equalsIgnoreCase(pgHost);
    }
}
