package com.clinica.sistema.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresUrlParserTest {

    @Test
    void deveConverterDatabaseUrlDoRailway() {
        PostgresUrlParser.ParsedDatasource parsed = PostgresUrlParser.parse(
                "postgresql://usuario:senha123@containers.railway.app:6543/railway"
        );

        assertEquals("usuario", parsed.username());
        assertEquals("senha123", parsed.password());
        assertTrue(parsed.jdbcUrl().startsWith("jdbc:postgresql://containers.railway.app:6543/railway"));
        assertTrue(parsed.jdbcUrl().contains("sslmode=prefer") || parsed.jdbcUrl().contains("sslmode=require"));
    }

    @Test
    void devePreservarParametrosDaUrlDoNeon() {
        PostgresUrlParser.ParsedDatasource parsed = PostgresUrlParser.parse(
                "postgresql://user:pass@ep-test.us-east-1.aws.neon.tech/neondb?sslmode=require"
        );

        assertTrue(parsed.jdbcUrl().contains("neon.tech"));
        assertTrue(parsed.jdbcUrl().contains("sslmode=require"));
    }
}
