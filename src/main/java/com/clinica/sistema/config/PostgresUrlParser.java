package com.clinica.sistema.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class PostgresUrlParser {

    private PostgresUrlParser() {
    }

    record ParsedDatasource(String jdbcUrl, String username, String password) {
    }

    static ParsedDatasource parse(String databaseUrl) {
        return parse(databaseUrl, sslModePadrao(databaseUrl));
    }

    static ParsedDatasource parse(String databaseUrl, String sslMode) {
        String normalizada = databaseUrl.trim();
        if (normalizada.startsWith("postgres://")) {
            normalizada = "postgresql://" + normalizada.substring("postgres://".length());
        }
        if (!normalizada.startsWith("postgresql://")) {
            throw new IllegalArgumentException("DATABASE_URL invalida para PostgreSQL.");
        }

        URI uri = URI.create(normalizada);
        String username = "";
        String password = "";
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            String[] credenciais = uri.getUserInfo().split(":", 2);
            username = decode(credenciais[0]);
            if (credenciais.length > 1) {
                password = decode(credenciais[1]);
            }
        }

        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String database = uri.getPath() != null ? uri.getPath().replaceFirst("^/", "") : "postgres";
        if (database.isBlank()) {
            database = "postgres";
        }

        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        String query = uri.getQuery();
        if (query != null && !query.isBlank()) {
            query = query.replace("sslmode=exigir", "sslmode=require");
            jdbcUrl += "?" + query;
        } else {
            String modoSsl = (sslMode == null || sslMode.isBlank()) ? sslModePadrao(databaseUrl) : sslMode;
            jdbcUrl += "?sslmode=" + modoSsl;
        }

        return new ParsedDatasource(jdbcUrl, username, password);
    }

    static String sslModePadrao(String databaseUrl) {
        String configurado = System.getenv("PGSSLMODE");
        if (configurado != null && !configurado.isBlank()) {
            return configurado;
        }
        if (databaseUrl != null && databaseUrl.contains("neon.tech")) {
            return "require";
        }
        if (System.getenv("RAILWAY_ENVIRONMENT") != null || System.getenv("RENDER") != null) {
            return "prefer";
        }
        return "require";
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
