package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clinica.financeiro")
public class FinanceiroProperties {

    private final Polyana polyana = new Polyana();

    public Polyana getPolyana() {
        return polyana;
    }

    public static class Polyana {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
