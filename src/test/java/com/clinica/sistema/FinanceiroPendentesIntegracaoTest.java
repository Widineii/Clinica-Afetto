package com.clinica.sistema;

import com.clinica.sistema.dto.ReceitaPixMesView;
import com.clinica.sistema.service.FinanceiroReceitaPixService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("local")
class FinanceiroPendentesIntegracaoTest {

    @Autowired
    private FinanceiroReceitaPixService receitaPixService;

    @Test
    void montarResumoMesComPendentesNaoDeveFalhar() {
        ReceitaPixMesView resumo = receitaPixService.montarResumoMes(YearMonth.now());
        assertNotNull(resumo.getTotalAReceberFormatado());
        assertNotNull(resumo.getPendentesGraficoJson());
    }
}
