package com.clinica.sistema.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProdProfileEnvironmentPostProcessorTest {

  private final ProdProfileEnvironmentPostProcessor processor = new ProdProfileEnvironmentPostProcessor();

  @Test
  void deveAtivarProdQuandoDatabaseUrlExiste() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("DATABASE_URL", "postgresql://u:p@host:5432/db");

    processor.postProcessEnvironment(env, new SpringApplication());

    assertTrue(env.matchesProfiles("prod"));
  }

  @Test
  void naoDeveSobrescreverPerfilJaDefinido() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("local");
    env.setProperty("DATABASE_URL", "postgresql://u:p@host:5432/db");

    processor.postProcessEnvironment(env, new SpringApplication());

    assertEquals(1, env.getActiveProfiles().length);
    assertTrue(env.matchesProfiles("local"));
  }
}
