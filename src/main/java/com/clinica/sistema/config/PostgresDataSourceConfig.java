package com.clinica.sistema.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("prod")
public class PostgresDataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${DATABASE_URL:}") String databaseUrl,
            @Value("${DATABASE_PUBLIC_URL:}") String databasePublicUrl,
            @Value("${PGHOST:}") String pgHost,
            @Value("${PGPORT:5432}") String pgPort,
            @Value("${PGDATABASE:}") String pgDatabase,
            @Value("${PGUSER:}") String pgUser,
            @Value("${PGPASSWORD:}") String pgPassword,
            @Value("${PGSSLMODE:}") String pgSslMode
    ) {
        String urlBanco = trim(primeiroNaoVazio(databaseUrl, databasePublicUrl));
        pgHost = trim(pgHost);
        pgUser = trim(pgUser);
        pgPassword = trim(pgPassword);
        pgDatabase = trim(pgDatabase);
        pgSslMode = trim(pgSslMode);

        if (urlBanco != null && urlBanco.contains("****")) {
            throw new IllegalStateException(
                    "DATABASE_URL contem asteriscos (senha mascarada). "
                            + "Use variaveis PGHOST, PGUSER, PGPASSWORD separadas no Render."
            );
        }

        String modoSsl = (pgSslMode == null || pgSslMode.isBlank()) ? PostgresUrlParser.sslModePadrao(urlBanco) : pgSslMode;

        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1200000);
        config.setDriverClassName("org.postgresql.Driver");

        if (urlBanco != null) {
            PostgresUrlParser.ParsedDatasource parsed = PostgresUrlParser.parse(urlBanco, modoSsl);
            config.setJdbcUrl(parsed.jdbcUrl());
            config.setUsername(parsed.username());
            config.setPassword(parsed.password());
        } else if (pgHost != null && !pgHost.isBlank()) {
            String banco = (pgDatabase == null || pgDatabase.isBlank()) ? "neondb" : pgDatabase;
            config.setJdbcUrl("jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + banco + "?sslmode=" + modoSsl);
            config.setUsername(pgUser);
            config.setPassword(pgPassword);
        } else {
            throw new IllegalStateException(
                    "PostgreSQL nao configurado. Crie o banco no Neon, copie a connection string "
                            + "e defina DATABASE_URL no Web Service do Render."
            );
        }

        return new HikariDataSource(config);
    }

    private static String primeiroNaoVazio(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return null;
    }

    private static String trim(String valor) {
        return valor == null ? null : valor.trim();
    }
}
