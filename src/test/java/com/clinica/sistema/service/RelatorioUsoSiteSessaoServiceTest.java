package com.clinica.sistema.service;

import com.clinica.sistema.dto.RelatorioUsoSiteView;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelatorioUsoSiteSessaoServiceTest {

    private final RelatorioUsoSiteSessaoService service = new RelatorioUsoSiteSessaoService();

    @Test
    void armazenaELimpaRelatorioNaSessao() {
        MockHttpSession session = new MockHttpSession();
        var relatorio = new RelatorioUsoSiteView(1, 1, 0, 1, 0, List.of());

        assertFalse(service.possuiRelatorioGerado(session));

        service.armazenar(session, relatorio);
        assertTrue(service.possuiRelatorioGerado(session));
        assertTrue(service.obter(session).isPresent());

        service.limpar(session);
        assertFalse(service.possuiRelatorioGerado(session));
        assertTrue(service.obter(session).isEmpty());
    }
}
