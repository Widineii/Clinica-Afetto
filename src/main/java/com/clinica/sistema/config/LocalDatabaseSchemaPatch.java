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
        aplicarColunasContaNova();
        garantirTabelaAuditoriaEventos();
        try {
            adicionarColunaSeNecessario("pagamento_iniciado_em", "TIMESTAMP");
            adicionarColunaSeNecessario("pagamento_expira_em", "TIMESTAMP");
            adicionarColunaSeNecessario("liberado_em", "TIMESTAMP");
            adicionarColunaUsuariosSeNecessario("periodicidade_pagamento", "VARCHAR(20) DEFAULT 'DIARIO'");
            adicionarColunaUsuariosSeNecessario("periodicidade_alterada_em", "TIMESTAMP");
            adicionarColunaUsuariosSeNecessario("ultimo_acesso_em", "TIMESTAMP");
            adicionarColunaUsuariosSeNecessario("valor_consulta_avulso", "DECIMAL(10,2)");
            adicionarColunaUsuariosSeNecessario("valor_consulta_semanal", "DECIMAL(10,2)");
            adicionarColunaUsuariosSeNecessario("valor_consulta_quinzenal", "DECIMAL(10,2)");
            adicionarColunaUsuariosSeNecessario("valor_consulta_mensal", "DECIMAL(10,2)");
            adicionarColunaUsuariosSeNecessario("percentual_taxa_indicacao", "DECIMAL(5,2)");
            adicionarColunaUsuariosSeNecessario("email", "VARCHAR(120)");
            adicionarColunaUsuariosSeNecessario("telefone_whatsapp", "VARCHAR(20)");
            garantirTabelaSenhaRecuperacao();
            garantirTabelaContratoLicenciamento();
            adicionarColunaContratoSeNecessario("contratante_finalizado", "BOOLEAN NOT NULL DEFAULT FALSE");
            adicionarColunaContratoSeNecessario("contratante_finalizado_em", "TIMESTAMP");
            adicionarColunaContratoSeNecessario("contratante_finalizado_por_nome", "VARCHAR(120)");
            adicionarColunaUsuariosSeNecessario("foto_perfil", "VARCHAR(120)");
            adicionarColunaUsuariosSeNecessario("lgpd_consentimento_em", "TIMESTAMP");
            adicionarColunaUsuariosSeNecessario("boas_vindas_controle_data", "DATE");
            adicionarColunaUsuariosSeNecessario("boas_vindas_exibicoes_hoje", "INT DEFAULT 0");
            adicionarColunaUsuariosSeNecessario("boas_vindas_oculto_hoje", "BOOLEAN DEFAULT FALSE");
            adicionarColunaUsuariosSeNecessario("boas_vindas_exibicoes_noite", "INT DEFAULT 0");
            adicionarColunaUsuariosSeNecessario("boas_vindas_oculto_noite", "BOOLEAN DEFAULT FALSE");
            adicionarColunaUsuariosSeNecessario("boas_vindas_primeiro_login_concluido", "BOOLEAN DEFAULT FALSE");
            adicionarColunaUsuariosSeNecessario("boas_vindas_apenas_apresentacao", "BOOLEAN DEFAULT FALSE");
            adicionarColunaUsuariosSeNecessario("boas_vindas_apresentacao_exibida", "BOOLEAN DEFAULT FALSE");
            adicionarColunaSalasSeNecessario("taxa_clinica", "DECIMAL(10,2)");
            preencherTaxaSala4Padrao();
            adicionarColunaSeNecessario("data_referencia_semana_pagamento", "DATE");
            adicionarColunaSeNecessario("data_referencia_mes_pagamento", "DATE");
            adicionarColunaSeNecessario("historico_datas_mensal", "VARCHAR(120)");
            preencherReferenciasCobranca();
            preencherHistoricoMensalInicial();
            try {
                removerChecksStatusPagamento();
            } catch (Exception ex) {
                log.warn("Nao foi possivel ajustar checks de status_pagamento no H2: {}", ex.getMessage());
            }
            log.info("Schema H2 local: pagamento e status_pagamento verificados.");
        } catch (Exception ex) {
            log.warn("Nao foi possivel aplicar patch de schema no H2: {}", ex.getMessage());
        }
    }

    private void aplicarColunasContaNova() {
        try {
            adicionarColunaUsuariosSeNecessario("conta_aprovada", "BOOLEAN DEFAULT TRUE");
            adicionarColunaUsuariosSeNecessario("origem_cadastro", "VARCHAR(20) DEFAULT 'GESTOR'");
            adicionarColunaUsuariosSeNecessario("cadastro_solicitado_em", "TIMESTAMP");
            adicionarColunaUsuariosSeNecessario("cadastro_aprovado_em", "TIMESTAMP");
            adicionarColunaUsuariosSeNecessario("cadastro_aprovado_por_nome", "VARCHAR(120)");
            log.info("Schema H2 local: colunas de conta nova verificadas.");
        } catch (Exception ex) {
            log.warn("Nao foi possivel aplicar colunas de conta nova no H2: {}", ex.getMessage());
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

    private void adicionarColunaSalasSeNecessario(String coluna, String tipoSql) {
        Integer existe = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'SALAS'
                  AND UPPER(COLUMN_NAME) = ?
                """,
                Integer.class,
                coluna.toUpperCase()
        );
        if (existe != null && existe == 0) {
            jdbcTemplate.execute("ALTER TABLE salas ADD COLUMN " + coluna + " " + tipoSql);
        }
    }

    private void preencherTaxaSala4Padrao() {
        jdbcTemplate.update(
                """
                UPDATE salas
                SET taxa_clinica = 25.00
                WHERE UPPER(TRIM(nome)) = 'SALA 4'
                  AND taxa_clinica IS NULL
                """
        );
    }

    private void preencherHistoricoMensalInicial() {
        jdbcTemplate.update(
                """
                UPDATE agendamentos
                SET historico_datas_mensal = FORMATDATETIME(data_hora_inicio, 'dd/MM')
                WHERE UPPER(tipo_recorrencia) = 'MENSAL'
                  AND historico_datas_mensal IS NULL
                  AND data_hora_inicio IS NOT NULL
                """
        );
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
                SET data_referencia_mes_pagamento = DATE_TRUNC('MONTH', CAST(data_hora_inicio AS DATE))
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

    private void garantirTabelaSenhaRecuperacao() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS senha_recuperacao (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    usuario_id BIGINT NOT NULL,
                    codigo_hash VARCHAR(120) NOT NULL,
                    criado_em TIMESTAMP NOT NULL,
                    expira_em TIMESTAMP NOT NULL,
                    usado_em TIMESTAMP
                )
                """
        );
    }

    private void garantirTabelaContratoLicenciamento() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS contrato_licenciamento_rascunho (
                    id VARCHAR(40) PRIMARY KEY,
                    dados_json CLOB NOT NULL,
                    atualizado_em TIMESTAMP,
                    atualizado_por_usuario_id BIGINT,
                    atualizado_por_nome VARCHAR(120),
                    contratante_finalizado BOOLEAN NOT NULL DEFAULT FALSE,
                    contratante_finalizado_em TIMESTAMP,
                    contratante_finalizado_por_nome VARCHAR(120)
                )
                """
        );
    }

    private void garantirTabelaAuditoriaEventos() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS auditoria_eventos (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    criado_em TIMESTAMP NOT NULL,
                    autor_id BIGINT,
                    autor_nome VARCHAR(120),
                    tipo VARCHAR(40) NOT NULL,
                    descricao VARCHAR(500) NOT NULL
                )
                """
        );
    }

    private void adicionarColunaContratoSeNecessario(String coluna, String tipoSql) {
        Integer existe = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'CONTRATO_LICENCIAMENTO_RASCUNHO'
                  AND UPPER(COLUMN_NAME) = ?
                """,
                Integer.class,
                coluna.toUpperCase()
        );
        if (existe != null && existe == 0) {
            jdbcTemplate.execute(
                    "ALTER TABLE contrato_licenciamento_rascunho ADD COLUMN " + coluna + " " + tipoSql
            );
        }
    }
}
