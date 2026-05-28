package com.clinica.sistema.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@Order(1)
public class PersistenciaDatabaseVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PersistenciaDatabaseVerifier.class);

    private final DataSource dataSource;

    public PersistenciaDatabaseVerifier(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();

            if (url.contains("h2:mem")) {
                boolean hospedagemNuvem = System.getenv("RAILWAY_ENVIRONMENT") != null
                        || "true".equalsIgnoreCase(System.getenv("RENDER"))
                        || "true".equalsIgnoreCase(System.getenv("RENDER_SERVICE_ID"));

                if (hospedagemNuvem) {
                    throw new IllegalStateException(
                            "Banco em memoria (H2): agendamentos somem ao reiniciar. "
                                    + "Conecte PostgreSQL ao app e use SPRING_PROFILES_ACTIVE=prod."
                    );
                }

                log.warn("H2 em memoria no PC: agendamentos somem ao reiniciar o servidor. "
                        + "Em producao use PostgreSQL no Render (Blueprint render.yaml).");
                return;
            }

            if (url.contains("postgresql")) {
                log.info("PostgreSQL ativo — agendamentos ficam salvos mesmo se o site reiniciar.");
            }
        }
    }
}
