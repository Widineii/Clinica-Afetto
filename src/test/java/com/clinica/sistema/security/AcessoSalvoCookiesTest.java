package com.clinica.sistema.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcessoSalvoCookiesTest {

    @Test
    void codificaEDecodificaSenha() {
        String original = "297b";
        String codificado = AcessoSalvoCookies.codificarValor(original);
        assertEquals(original, AcessoSalvoCookies.decodificarValor(codificado));
    }

    @Test
    void decodificarValorInvalidoRetornaVazio() {
        assertEquals("", AcessoSalvoCookies.decodificarValor("@@@"));
    }
}
