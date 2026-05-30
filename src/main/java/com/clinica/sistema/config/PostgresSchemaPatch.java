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
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS periodicidade_pagamento VARCHAR(20) DEFAULT 'DIARIO'
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE agendamentos
                    ADD COLUMN IF NOT EXISTS data_referencia_semana_pagamento DATE
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE agendamentos
                    ADD COLUMN IF NOT EXISTS data_referencia_mes_pagamento DATE
                    """
            );
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
                    SET data_referencia_mes_pagamento = DATE_TRUNC('month', CAST(data_hora_inicio AS DATE))::DATE
                    WHERE data_referencia_mes_pagamento IS NULL
                      AND data_hora_inicio IS NOT NULL
                    """
            );
            log.info("Schema PostgreSQL: colunas de pagamento e usuarios verificadas.");
        } catch (Exception e) {
            log.warn("Nao foi possivel aplicar patch de schema no PostgreSQL: {}", e.getMessage());
        }
    }
}
