package com.clinica.sistema.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SitePropertiesTest {

    @Test
    void deveMontarRotuloDaVersaoDoSite() {
        SiteProperties properties = new SiteProperties();
        properties.setVersion("2.49");

        assertEquals("2.49", properties.getVersion());
        assertEquals("Versão do site 2.49", properties.getRotuloExibicao());
    }
}
