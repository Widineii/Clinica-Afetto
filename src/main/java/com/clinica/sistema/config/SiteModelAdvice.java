package com.clinica.sistema.config;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class SiteModelAdvice {

    private final SiteProperties siteProperties;

    public SiteModelAdvice(SiteProperties siteProperties) {
        this.siteProperties = siteProperties;
    }

    @ModelAttribute
    public void adicionarVersaoSite(Model model) {
        model.addAttribute("versaoSite", siteProperties.getVersion());
        model.addAttribute("versaoSiteRotulo", siteProperties.getRotuloExibicao());
    }
}
