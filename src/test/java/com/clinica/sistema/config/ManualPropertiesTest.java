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
    void deveUsarModoDemoQuandoVideoNaoConfigurado() {
        ManualProperties props = new ManualProperties();
        props.setVideoUrl("");

        assertEquals("demo", props.resolverModoVideo());
    }
}
