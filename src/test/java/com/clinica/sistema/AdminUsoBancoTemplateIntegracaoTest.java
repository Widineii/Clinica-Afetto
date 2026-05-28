package com.clinica.sistema;

import com.clinica.sistema.dto.UsoBancoView;
import com.clinica.sistema.service.UsoBancoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("local")
class AdminUsoBancoTemplateIntegracaoTest {

    @Autowired
    private UsoBancoService usoBancoService;

    @Test
    void deveMontarResumoNoH2SemErroDeTransacao() {
        UsoBancoView resumo = usoBancoService.montarResumo();

        assertNotNull(resumo);
        assertFalse(resumo.isPostgresComTamanhoReal());
    }
}
