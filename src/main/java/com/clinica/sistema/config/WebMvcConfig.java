package com.clinica.sistema.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PrimeiroAcessoInterceptor primeiroAcessoInterceptor;

    public WebMvcConfig(PrimeiroAcessoInterceptor primeiroAcessoInterceptor) {
        this.primeiroAcessoInterceptor = primeiroAcessoInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(primeiroAcessoInterceptor);
    }
}
