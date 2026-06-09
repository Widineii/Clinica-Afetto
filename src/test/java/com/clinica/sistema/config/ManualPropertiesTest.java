package com.clinica.sistema.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualPropertiesTest {

    @Test
    void deveConverterLinkYoutubeWatchParaEmbed() {
        ManualProperties props = new ManualProperties();
        props.setVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertEquals("embed", props.resolverModoVideo());
        assertTrue(props.getVideoUrlNormalizada().contains("youtube.com/embed/dQw4w9WgXcQ"));
    }

    @Test
    void deveGerarLinkWhatsappSuporte() {
        ManualProperties props = new ManualProperties();
        props.setWhatsappNumero("5537998550994");
        props.setWhatsappMensagemPadrao("Ola! Preciso de suporte com a Agenda Afetto.");

        assertTrue(props.temWhatsappSuporte());
        assertEquals(
                "https://wa.me/5537998550994?text=Ola%21%20Preciso%20de%20suporte%20com%20a%20Agenda%20Afetto.",
                props.resolverLinkWhatsapp()
        );
    }

    @Test
    void deveGerarLinkWhatsappClinica() {
        ManualProperties props = new ManualProperties();
        props.setWhatsappNumeroClinica("553182835857");
        props.setWhatsappMensagemClinica("Ola! Gostaria de falar com a clinica Afetto.");

        assertTrue(props.temWhatsappClinica());
        assertEquals("(31) 8283-5857", props.resolverRotuloWhatsappClinicaExibicao());
        assertEquals(
                "https://wa.me/553182835857?text=Ola%21%20Gostaria%20de%20falar%20com%20a%20clinica%20Afetto.",
                props.resolverLinkWhatsappClinica()
        );
    }

    @Test
    void deveUsarModoDemoQuandoVideoNaoConfigurado() {
        ManualProperties props = new ManualProperties();
        props.setVideoUrl("");

        assertEquals("demo", props.resolverModoVideo());
    }
}
