package com.clinica.sistema.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ajusta colunas e constraints no H2 local quando ddl-auto nao recria checks de enum.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalDatabaseSchemaPatch implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDatabaseSchemaPatch.class);

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public LocalDatabaseSchemaPatch(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.toLowerCase().contains("h2")) {
            return;
        }
        try {
            adicionarColunaSeNecessario("pagamento_iniciado_em", "TIMESTAMP");
            adicionarColunaSeNecessario("pagamento_expira_em", "TIMESTAMP");
            adicionarColunaSeNecessario("liberado_em", "TIMESTAMP");
            adicionarColunaUsuariosSeNecessario("periodicidade_pagamento", "VARCHAR(20) DEFAULT 'DIARIO'");
            adicionarColunaUsuariosSeNecessario("periodicidade_alterada_em", "TIMESTAMP");
            adicionarColunaSeNecessario("data_referencia_semana_pagamento", "DATE");
            adicionarColunaSeNecessario("data_referencia_mes_pagamento", "DATE");
            preencherReferenciasCobranca();
            removerChecksStatusPagamento();
            log.info("Schema H2 local: pagamento e status_pagamento verificados.");
        } catch (Exception ex) {
            log.warn("Nao foi possivel aplicar patch de schema no H2: {}", ex.getMessage());
        }
    }

    private void adicionarColunaSeNecessario(String coluna, String tipoSql) {
        Integer existe = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'AGENDAMENTOS'
                  AND UPPER(COLUMN_NAME) = ?
                """,
                Integer.class,
                coluna.toUpperCase()
        );
        if (existe != null && existe == 0) {
            jdbcTemplate.execute("ALTER TABLE agendamentos ADD COLUMN " + coluna + " " + tipoSql);
        }
    }

    private void adicionarColunaUsuariosSeNecessario(String coluna, String tipoSql) {
        Integer existe = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'USUARIOS'
                  AND UPPER(COLUMN_NAME) = ?
                """,
                Integer.class,
                coluna.toUpperCase()
        );
        if (existe != null && existe == 0) {
            jdbcTemplate.execute("ALTER TABLE usuarios ADD COLUMN " + coluna + " " + tipoSql);
        }
    }

    private void preencherReferenciasCobranca() {
        jdbcTemplate.update(
                """
                UPDATE agendamentos
                SET data_referencia_semana_pagamento = CAST(data_hora_inicio AS DATE)
                WHERE data_referencia_semana_pagamento IS NULL
                  AND data_hora_inicio IS NOT NULL
                """
        );
        jdbcTemplate.update(
                """
                UPDATE agendamentos
                SET data_referencia_mes_pagamento = TRUNC(CAST(data_hora_inicio AS DATE), 'MONTH')
                WHERE data_referencia_mes_pagamento IS NULL
                  AND data_hora_inicio IS NOT NULL
                """
        );
    }

    private void removerChecksStatusPagamento() {
        List<String> checks = jdbcTemplate.queryForList(
                """
                SELECT CONSTRAINT_NAME
                FROM INFORMATION_SCHEMA.CONSTRAINTS
                WHERE UPPER(TABLE_NAME) = 'AGENDAMENTOS'
                  AND CONSTRAINT_TYPE = 'CHECK'
                """,
                String.class
        );
        for (String constraint : checks) {
            try {
                jdbcTemplate.execute("ALTER TABLE agendamentos DROP CONSTRAINT " + constraint);
                log.info("Removida constraint H2 {} em agendamentos.", constraint);
            } catch (Exception ex) {
                log.debug("Nao foi possivel remover constraint {}: {}", constraint, ex.getMessage());
            }
        }
    }
}
