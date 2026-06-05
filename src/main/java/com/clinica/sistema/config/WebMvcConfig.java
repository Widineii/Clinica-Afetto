package com.clinica.sistema.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PrimeiroAcessoInterceptor primeiroAcessoInterceptor;
    private final PerfilFotoProperties perfilFotoProperties;

    public WebMvcConfig(
            PrimeiroAcessoInterceptor primeiroAcessoInterceptor,
            PerfilFotoProperties perfilFotoProperties
    ) {
        this.primeiroAcessoInterceptor = primeiroAcessoInterceptor;
        this.perfilFotoProperties = perfilFotoProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(primeiroAcessoInterceptor);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String diretorio = perfilFotoProperties.resolverDiretorio().toUri().toString();
        registry.addResourceHandler("/uploads/perfis/**")
                .addResourceLocations(diretorio.endsWith("/") ? diretorio : diretorio + "/");
    }
}
