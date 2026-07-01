package com.clinica.sistema.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

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
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS periodicidade_alterada_em TIMESTAMP
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS ultimo_acesso_em TIMESTAMP
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS valor_consulta_avulso NUMERIC(10,2)
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS valor_consulta_semanal NUMERIC(10,2)
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS valor_consulta_quinzenal NUMERIC(10,2)
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS valor_consulta_mensal NUMERIC(10,2)
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS percentual_taxa_indicacao NUMERIC(5,2)
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS telefone_whatsapp VARCHAR(20)
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS conta_aprovada BOOLEAN DEFAULT TRUE
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS origem_cadastro VARCHAR(20) DEFAULT 'GESTOR'
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS cadastro_solicitado_em TIMESTAMP
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS cadastro_aprovado_em TIMESTAMP
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS cadastro_aprovado_por_nome VARCHAR(120)
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS foto_perfil VARCHAR(120)
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS lgpd_consentimento_em TIMESTAMP
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS email VARCHAR(120)
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS boas_vindas_controle_data DATE
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS boas_vindas_exibicoes_hoje INTEGER DEFAULT 0
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS boas_vindas_oculto_hoje BOOLEAN DEFAULT FALSE
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS boas_vindas_exibicoes_noite INTEGER DEFAULT 0
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS boas_vindas_oculto_noite BOOLEAN DEFAULT FALSE
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS boas_vindas_primeiro_login_concluido BOOLEAN DEFAULT FALSE
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS boas_vindas_apenas_apresentacao BOOLEAN DEFAULT FALSE
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE usuarios
                    ADD COLUMN IF NOT EXISTS boas_vindas_apresentacao_exibida BOOLEAN DEFAULT FALSE
                    """
            );
            jdbcTemplate.execute(
                    """
                    ALTER TABLE salas
                    ADD COLUMN IF NOT EXISTS taxa_clinica NUMERIC(10,2)
                    """
            );
            jdbcTemplate.update(
                    """
                    UPDATE salas
                    SET taxa_clinica = 25.00
                    WHERE LOWER(TRIM(nome)) = 'sala 4'
                      AND taxa_clinica IS NULL
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
            jdbcTemplate.execute(
                    """
                    ALTER TABLE agendamentos
                    ADD COLUMN IF NOT EXISTS historico_datas_mensal VARCHAR(120)
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
            jdbcTemplate.update(
                    """
                    UPDATE agendamentos
                    SET historico_datas_mensal = TO_CHAR(data_hora_inicio, 'DD/MM')
                    WHERE UPPER(tipo_recorrencia) = 'MENSAL'
                      AND historico_datas_mensal IS NULL
                      AND data_hora_inicio IS NOT NULL
                    """
            );
            atualizarCheckStatusPagamento();
            criarTabelaWhatsappMensagemPagamentoSeNecessario();
            criarTabelaSenhaRecuperacaoSeNecessario();
            criarTabelaContratoLicenciamentoSeNecessario();
            criarTabelaAuditoriaEventosSeNecessario();
            criarTabelaPacienteCadernoObservacaoSeNecessario();
            log.info("Schema PostgreSQL: colunas de pagamento, usuarios e status_pagamento verificadas.");
        } catch (Exception e) {
            log.warn("Nao foi possivel aplicar patch de schema no PostgreSQL: {}", e.getMessage());
        }
    }

    /**
     * Hibernate criou o CHECK antes de existir {@code AGUARDANDO_APROVACAO_INDICACAO}; sem isso
     * indicações falham no INSERT e a solicitação nunca chega para aprovação.
     */
    private void atualizarCheckStatusPagamento() {
        List<String> checksStatus = jdbcTemplate.queryForList(
                """
                SELECT c.conname
                FROM pg_constraint c
                JOIN pg_class t ON c.conrelid = t.oid
                WHERE t.relname = 'agendamentos'
                  AND c.contype = 'c'
                  AND pg_get_constraintdef(c.oid) ILIKE '%status_pagamento%'
                """,
                String.class
        );
        for (String constraint : checksStatus) {
            jdbcTemplate.execute(
                    "ALTER TABLE agendamentos DROP CONSTRAINT IF EXISTS \"" + constraint + "\""
            );
            log.info("Removida constraint PostgreSQL {} em agendamentos.", constraint);
        }
        jdbcTemplate.execute(
                """
                ALTER TABLE agendamentos
                ADD CONSTRAINT agendamentos_status_pagamento_check
                CHECK (
                    status_pagamento IS NULL
                    OR status_pagamento IN (
                        'PAGAMENTO_FUTURO',
                        'ESPERANDO_CONFIRMACAO',
                        'AGUARDANDO_CONFIRMACAO_DINHEIRO',
                        'AGUARDANDO_APROVACAO_INDICACAO',
                        'AGUARDANDO_PAGAMENTO',
                        'LIBERADO_FALTA_PAGAMENTO',
                        'PAGO'
                    )
                )
                """
        );
        log.info("Constraint agendamentos_status_pagamento_check atualizada com AGUARDANDO_APROVACAO_INDICACAO.");
    }

    private void criarTabelaWhatsappMensagemPagamentoSeNecessario() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS whatsapp_mensagem_pagamento (
                    periodicidade VARCHAR(20) PRIMARY KEY,
                    texto VARCHAR(2000) NOT NULL
                )
                """
        );
        log.info("Schema PostgreSQL: tabela whatsapp_mensagem_pagamento verificada.");
    }

    private void criarTabelaSenhaRecuperacaoSeNecessario() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS senha_recuperacao (
                    id BIGSERIAL PRIMARY KEY,
                    usuario_id BIGINT NOT NULL,
                    codigo_hash VARCHAR(120) NOT NULL,
                    criado_em TIMESTAMP NOT NULL,
                    expira_em TIMESTAMP NOT NULL,
                    usado_em TIMESTAMP
                )
                """
        );
        log.info("Schema PostgreSQL: tabela senha_recuperacao verificada.");
    }

    private void criarTabelaContratoLicenciamentoSeNecessario() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS contrato_licenciamento_rascunho (
                    id VARCHAR(40) PRIMARY KEY,
                    dados_json TEXT NOT NULL,
                    atualizado_em TIMESTAMP,
                    atualizado_por_usuario_id BIGINT,
                    atualizado_por_nome VARCHAR(120),
                    contratante_finalizado BOOLEAN NOT NULL DEFAULT FALSE,
                    contratante_finalizado_em TIMESTAMP,
                    contratante_finalizado_por_nome VARCHAR(120)
                )
                """
        );
        jdbcTemplate.execute(
                """
                ALTER TABLE contrato_licenciamento_rascunho
                ADD COLUMN IF NOT EXISTS contratante_finalizado BOOLEAN NOT NULL DEFAULT FALSE
                """
        );
        jdbcTemplate.execute(
                """
                ALTER TABLE contrato_licenciamento_rascunho
                ADD COLUMN IF NOT EXISTS contratante_finalizado_em TIMESTAMP
                """
        );
        jdbcTemplate.execute(
                """
                ALTER TABLE contrato_licenciamento_rascunho
                ADD COLUMN IF NOT EXISTS contratante_finalizado_por_nome VARCHAR(120)
                """
        );
        log.info("Schema PostgreSQL: tabela contrato_licenciamento_rascunho verificada.");
    }

    private void criarTabelaAuditoriaEventosSeNecessario() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS auditoria_eventos (
                    id BIGSERIAL PRIMARY KEY,
                    criado_em TIMESTAMP NOT NULL,
                    autor_id BIGINT,
                    autor_nome VARCHAR(120),
                    tipo VARCHAR(40) NOT NULL,
                    descricao VARCHAR(500) NOT NULL
                )
                """
        );
        log.info("Schema PostgreSQL: tabela auditoria_eventos verificada.");
    }

    private void criarTabelaPacienteCadernoObservacaoSeNecessario() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS paciente_caderno_observacao (
                    id BIGSERIAL PRIMARY KEY,
                    id_profissional BIGINT NOT NULL,
                    chave_caderno VARCHAR(40) NOT NULL,
                    texto VARCHAR(2000) NOT NULL,
                    criado_em TIMESTAMP,
                    atualizado_em TIMESTAMP NOT NULL
                )
                """
        );
        jdbcTemplate.execute(
                """
                ALTER TABLE paciente_caderno_observacao
                ADD COLUMN IF NOT EXISTS criado_em TIMESTAMP
                """
        );
        jdbcTemplate.execute(
                """
                ALTER TABLE paciente_caderno_observacao
                ADD COLUMN IF NOT EXISTS evolucao_clinica VARCHAR(20)
                """
        );
        jdbcTemplate.execute(
                """
                ALTER TABLE paciente_caderno_observacao
                ADD COLUMN IF NOT EXISTS lembrete_em TIMESTAMP
                """
        );
        jdbcTemplate.update(
                """
                UPDATE paciente_caderno_observacao
                SET criado_em = atualizado_em
                WHERE criado_em IS NULL
                """
        );
        List<String> uniques = jdbcTemplate.queryForList(
                """
                SELECT c.conname
                FROM pg_constraint c
                JOIN pg_class t ON c.conrelid = t.oid
                WHERE t.relname = 'paciente_caderno_observacao'
                  AND c.contype = 'u'
                """,
                String.class
        );
        for (String constraint : uniques) {
            jdbcTemplate.execute(
                    "ALTER TABLE paciente_caderno_observacao DROP CONSTRAINT IF EXISTS \"" + constraint + "\""
            );
        }
        List<String> indicesUnicos = jdbcTemplate.queryForList(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE tablename = 'paciente_caderno_observacao'
                  AND indexdef ILIKE '%UNIQUE%'
                  AND indexname NOT LIKE '%_pkey'
                """,
                String.class
        );
        for (String index : indicesUnicos) {
            jdbcTemplate.execute("DROP INDEX IF EXISTS \"" + index + "\"");
        }
        log.info("Schema PostgreSQL: tabela paciente_caderno_observacao verificada.");
    }
}
