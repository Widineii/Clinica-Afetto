package com.clinica.sistema.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppNumeroUtilTest {

    @Test
    void deveAdicionarDdiBrasilQuandoFaltar() {
        assertEquals("5537998550994", WhatsAppNumeroUtil.normalizarDestinatario("37998550994").orElseThrow());
        assertEquals("5537998550994", WhatsAppNumeroUtil.normalizarDestinatario("(37) 99855-0994").orElseThrow());
    }

    @Test
    void deveManterNumeroComDdi() {
        assertEquals("5537998550994", WhatsAppNumeroUtil.normalizarDestinatario("5537998550994").orElseThrow());
    }

    @Test
    void deveRejeitarNumeroCurto() {
        assertTrue(WhatsAppNumeroUtil.normalizarDestinatario("123").isEmpty());
        assertTrue(WhatsAppNumeroUtil.normalizarDestinatario(null).isEmpty());
    }
}
