package com.clinica.sistema.config;

import com.clinica.sistema.service.ContratoLicenciamentoService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ContratoLicenciamentoBootstrap {

    private final ContratoLicenciamentoService contratoLicenciamentoService;

    public ContratoLicenciamentoBootstrap(ContratoLicenciamentoService contratoLicenciamentoService) {
        this.contratoLicenciamentoService = contratoLicenciamentoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrarRascunhosAntigos() {
        contratoLicenciamentoService.migrarRascunhoPadraoSeNecessario();
    }
}
