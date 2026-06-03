package com.clinica.sistema.service;

import com.clinica.sistema.dto.RelatorioUsoSiteView;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Relatorio de uso do site apenas em memoria de sessao (nao persiste snapshot no banco).
 */
@Service
public class RelatorioUsoSiteSessaoService {

    static final String SESSION_KEY = "relatorioUsoSiteTemporario";

    public Optional<RelatorioUsoSiteView> obter(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Object valor = session.getAttribute(SESSION_KEY);
        if (valor instanceof RelatorioUsoSiteView relatorio) {
            return Optional.of(relatorio);
        }
        return Optional.empty();
    }

    public void armazenar(HttpSession session, RelatorioUsoSiteView relatorio) {
        if (session == null || relatorio == null) {
            return;
        }
        session.setAttribute(SESSION_KEY, relatorio);
    }

    public void limpar(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(SESSION_KEY);
    }

    public boolean possuiRelatorioGerado(HttpSession session) {
        return obter(session).isPresent();
    }
}
