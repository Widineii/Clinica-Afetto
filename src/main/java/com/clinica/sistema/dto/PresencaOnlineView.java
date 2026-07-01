package com.clinica.sistema.dto;

import java.util.List;

public record PresencaOnlineView(int online, int minutosAtivos, List<PresencaOnlineUsuarioView> usuarios) {
}
