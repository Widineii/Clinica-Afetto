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
 * Uma unica vez: profissionais que ja usavam o sistema veem a novidade no proximo login.
 * Contas novas criadas depois nao entram neste rollout e passam pelo primeiro acesso completo.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class BoasVindasApresentacaoRollout implements ApplicationRunner {

    static final String PATCH_ID = "boas_vindas_apresentacao_unica_v2";

    private static final Logger log = LoggerFactory.getLogger(BoasVindasApresentacaoRollout.class);

    private final JdbcTemplate jdbcTemplate;

    public BoasVindasApresentacaoRollout(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            garantirTabelaMigracao();
            if (patchJaAplicado()) {
                return;
            }
            garantirColunasBoasVindas();
            int afetados = jdbcTemplate.update(
                    """
                    UPDATE usuarios
                    SET boas_vindas_primeiro_login_concluido = FALSE,
                        boas_vindas_apenas_apresentacao = TRUE
                    WHERE cargo = 'ROLE_PROFISSIONAL'
                      AND COALESCE(dona_clinica, FALSE) = FALSE
                      AND ultimo_acesso_em IS NOT NULL
                      AND COALESCE(boas_vindas_apresentacao_exibida, FALSE) = FALSE
                      AND COALESCE(boas_vindas_apenas_apresentacao, FALSE) = FALSE
                    """
            );
            registrarPatch();
            log.info(
                    "Boas-vindas: apresentacao unica preparada para {} profissional(is) existente(s) no proximo login",
                    afetados
            );
        } catch (Exception ex) {
            log.warn("Nao foi possivel preparar apresentacao unica de boas-vindas: {}", ex.getMessage());
        }
    }

    private void garantirTabelaMigracao() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS sistema_migracao (
                    id VARCHAR(120) PRIMARY KEY,
                    aplicado_em TIMESTAMP NOT NULL
                )
                """
        );
    }

    private boolean patchJaAplicado() {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT id FROM sistema_migracao WHERE id = ?",
                String.class,
                PATCH_ID
        );
        return !ids.isEmpty();
    }

    private void garantirColunasBoasVindas() {
        try {
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
        } catch (Exception ex) {
            adicionarColunaBoasVindasH2SeNecessario(
                    "BOAS_VINDAS_APENAS_APRESENTACAO",
                    "boas_vindas_apenas_apresentacao BOOLEAN DEFAULT FALSE"
            );
            adicionarColunaBoasVindasH2SeNecessario(
                    "BOAS_VINDAS_APRESENTACAO_EXIBIDA",
                    "boas_vindas_apresentacao_exibida BOOLEAN DEFAULT FALSE"
            );
        }
    }

    private void adicionarColunaBoasVindasH2SeNecessario(String nomeColuna, String ddl) {
        Integer existe = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'USUARIOS'
                  AND UPPER(COLUMN_NAME) = ?
                """,
                Integer.class,
                nomeColuna
        );
        if (existe != null && existe == 0) {
            jdbcTemplate.execute("ALTER TABLE usuarios ADD COLUMN " + ddl);
        }
    }

    private void registrarPatch() {
        jdbcTemplate.update(
                "INSERT INTO sistema_migracao (id, aplicado_em) VALUES (?, CURRENT_TIMESTAMP)",
                PATCH_ID
        );
    }
}
