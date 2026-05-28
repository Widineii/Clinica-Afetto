package com.clinica.sistema.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Garante colunas novas no PostgreSQL (Neon/Render) quando ddl-auto nao aplicou a tempo.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PostgresSchemaPatch implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostgresSchemaPatch.class);

    private final JdbcTemplate jdbcTemplate;

    public PostgresSchemaPatch(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String produto = jdbcTemplate.queryForObject("SELECT version()", String.class);
            if (produto == null || !produto.toLowerCase().contains("postgresql")) {
                return;
            }
            jdbcTemplate.execute(
                    """
                    ALTER TABLE relatorios_mensais_arquivados
                    ADD COLUMN IF NOT EXISTS pdf_notificacao_baixado_em TIMESTAMP
                    """
            );
            log.info("Schema PostgreSQL: coluna pdf_notificacao_baixado_em verificada.");
        } catch (Exception e) {
            log.warn("Nao foi possivel aplicar patch de schema no PostgreSQL: {}", e.getMessage());
        }
    }
}
