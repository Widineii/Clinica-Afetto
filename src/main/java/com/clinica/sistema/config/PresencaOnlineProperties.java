package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.presenca-online")
public class PresencaOnlineProperties {

    /** Minutos sem atividade para considerar que a pessoa saiu. */
    private int minutosAtivos = 5;

    /** Somente local: simula pessoas online para testar o visual. */
    private boolean demoLocal = false;

    private List<String> demoNomes = List.of(
            "Polyana",
            "Lucas",
            "Carol",
            "Julia",
            "Breno"
    );

    public int getMinutosAtivos() {
        return minutosAtivos;
    }

    public void setMinutosAtivos(int minutosAtivos) {
        this.minutosAtivos = Math.max(1, minutosAtivos);
    }

    public boolean isDemoLocal() {
        return demoLocal;
    }

    public void setDemoLocal(boolean demoLocal) {
        this.demoLocal = demoLocal;
    }

    public List<String> getDemoNomes() {
        return demoNomes;
    }

    public void setDemoNomes(List<String> demoNomes) {
        if (demoNomes == null || demoNomes.isEmpty()) {
            this.demoNomes = List.of();
            return;
        }
        this.demoNomes = demoNomes.stream()
                .filter(nome -> nome != null && !nome.isBlank())
                .map(String::trim)
                .toList();
    }
}
