package com.clinica.sistema;

import com.clinica.sistema.dto.ResumoPendenciasPagamentoView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class PendenciasPagamentoFragmentTemplateTest {

    @Autowired
    private TemplateEngine templateEngine;

    @Test
    void bannerDeveRenderizarMetricasSemErro() {
        ResumoPendenciasPagamentoView resumo = new ResumoPendenciasPagamentoView(
                3,
                "R$ 150,00",
                "Pendências",
                "Você tem 3 itens pendentes.",
                "Você tem 3 itens. Total: R$ 150,00.",
                "03/06/2026",
                "/agendamentos/meus-pagamentos#pagamentos-pendentes"
        );
        Context context = new Context();
        context.setVariable("resumoPendenciasPagamento", resumo);

        String html = templateEngine.process(
                "fragments/pendencias-pagamento-profissional",
                Set.of("banner"),
                context
        );

        assertFalse(html.contains("Algo deu errado"), () -> "HTML: " + html);
        assertTrue(html.contains("R$ 150,00"), () -> "HTML: " + html);
        assertTrue(html.contains("pendencias-alerta-principal__numero--qtd"), () -> "HTML: " + html);
        assertTrue(html.contains("pendencias-alerta-principal__metrica-icone"), () -> "HTML: " + html);
        assertTrue(html.contains("Resolver pendências agora"), () -> "HTML: " + html);
        assertTrue(html.contains(">3<") || html.contains(">3</span>"), () -> "HTML: " + html);
    }
}
