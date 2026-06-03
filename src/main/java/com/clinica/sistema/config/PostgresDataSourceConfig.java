package com.clinica.sistema.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("prod")
public class PostgresDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(PostgresDataSourceConfig.class);

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
            @Value("${PGSSLMODE:}") String pgSslMode,
            @Value("${spring.datasource.hikari.maximum-pool-size:5}") int maximumPoolSize,
            @Value("${spring.datasource.hikari.minimum-idle:1}") int minimumIdle,
            @Value("${spring.datasource.hikari.connection-timeout:15000}") long connectionTimeoutMs,
            @Value("${spring.datasource.hikari.idle-timeout:240000}") long idleTimeoutMs,
            @Value("${spring.datasource.hikari.max-lifetime:600000}") long maxLifetimeMs
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
                            + "Use variaveis PGHOST, PGUSER, PGPASSWORD separadas no Railway ou Render."
            );
        }

        String modoSsl = (pgSslMode == null || pgSslMode.isBlank()) ? PostgresUrlParser.sslModePadrao(urlBanco) : pgSslMode;

        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(Math.max(2, maximumPoolSize));
        config.setMinimumIdle(Math.max(0, minimumIdle));
        config.setConnectionTimeout(connectionTimeoutMs);
        config.setIdleTimeout(idleTimeoutMs);
        config.setMaxLifetime(maxLifetimeMs);
        config.setDriverClassName("org.postgresql.Driver");

        if (urlBanco != null) {
            PostgresUrlParser.ParsedDatasource parsed = PostgresUrlParser.parse(urlBanco, modoSsl);
            config.setJdbcUrl(ajustarJdbcUrlNeon(parsed.jdbcUrl()));
            config.setUsername(parsed.username());
            config.setPassword(parsed.password());
            avisarNeonSemPooler(urlBanco);
        } else if (pgHost != null && !pgHost.isBlank()) {
            String banco = (pgDatabase == null || pgDatabase.isBlank()) ? "neondb" : pgDatabase;
            config.setJdbcUrl(ajustarJdbcUrlNeon(
                    "jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + banco + "?sslmode=" + modoSsl
            ));
            config.setUsername(pgUser);
            config.setPassword(pgPassword);
            avisarNeonSemPooler(pgHost);
        } else {
            throw new IllegalStateException(
                    "PostgreSQL nao configurado. Crie o banco no Neon e defina PGHOST, PGUSER, "
                            + "PGPASSWORD, PGDATABASE e PGSSLMODE no Railway (ou DATABASE_URL completa)."
            );
        }

        log.info(
                "Pool PostgreSQL: max={}, minIdle={}, connectionTimeoutMs={}",
                config.getMaximumPoolSize(),
                config.getMinimumIdle(),
                config.getConnectionTimeout()
        );
        return new HikariDataSource(config);
    }

    /** Neon + Railway: pool menor e TCP keepalive reduzem latencia e cold start. */
    private static String ajustarJdbcUrlNeon(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.contains("neon.tech")) {
            return jdbcUrl;
        }
        if (jdbcUrl.contains("tcpKeepAlive")) {
            return jdbcUrl;
        }
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "tcpKeepAlive=true";
    }

    private static void avisarNeonSemPooler(String hostOuUrl) {
        if (hostOuUrl == null || !hostOuUrl.contains("neon.tech")) {
            return;
        }
        if (hostOuUrl.contains("-pooler") || hostOuUrl.contains("pooler.")) {
            return;
        }
        log.warn(
                "Neon sem pooler detectado. No dashboard Neon use a connection string "
                        + "'Pooled' (host com -pooler) na variavel DATABASE_URL do Railway para menos lentidao."
        );
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
