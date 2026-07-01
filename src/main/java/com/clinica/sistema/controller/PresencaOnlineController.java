package com.clinica.sistema.controller;

import com.clinica.sistema.dto.PresencaOnlineView;
import com.clinica.sistema.service.PresencaOnlineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agendamentos/api")
public class PresencaOnlineController {

    private final PresencaOnlineService presencaOnlineService;

    public PresencaOnlineController(PresencaOnlineService presencaOnlineService) {
        this.presencaOnlineService = presencaOnlineService;
    }

    @GetMapping("/presenca-online")
    public PresencaOnlineView presencaOnline() {
        return presencaOnlineService.montarVisaoAtual();
    }
}
