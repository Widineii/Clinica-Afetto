package com.clinica.sistema.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
public class CsrfControllerAdvice {

    @ModelAttribute
    public void exporTokenCsrf(HttpServletRequest request, Model model) {
        if (model.containsAttribute("_csrf")) {
            return;
        }
        CsrfToken csrfToken = materializarTokenCsrf(request);
        if (csrfToken != null) {
            model.addAttribute("_csrf", csrfToken);
        }
    }

    private CsrfToken materializarTokenCsrf(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        if (csrfToken == null) {
            return null;
        }
        return new DefaultCsrfToken(
                csrfToken.getHeaderName(),
                csrfToken.getParameterName(),
                csrfToken.getToken()
        );
    }
}
