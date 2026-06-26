package com.clinica.sistema.config;

import com.clinica.sistema.security.ClinicaAuthenticationSuccessHandler;
import com.clinica.sistema.security.ClinicaAuthenticationProvider;
import com.clinica.sistema.security.ClinicaUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ClinicaAuthenticationProvider clinicaAuthenticationProvider,
            ClinicaAuthenticationSuccessHandler authenticationSuccessHandler,
            ClinicaUserDetailsService clinicaUserDetailsService,
            SegurancaProperties segurancaProperties
    ) throws Exception {
        http
                .authenticationProvider(clinicaAuthenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/primeiro-acesso", "/senha/**", "/error", "/actuator/health", "/actuator/health/**", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/uploads/perfis/**").authenticated()
                        .requestMatchers("/api/webhooks/**").permitAll()
                        .requestMatchers("/agendamentos/**", "/conta/**", "/pagamentos/**", "/indicacoes/**").authenticated()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/webhooks/**"))
                .rememberMe(remember -> remember
                        .key(segurancaProperties.getRememberMeKey())
                        .tokenValiditySeconds(segurancaProperties.getRememberMeValiditySeconds())
                        .rememberMeParameter("remember-me")
                        .rememberMeCookieName("remember-me")
                        .userDetailsService(clinicaUserDetailsService)
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("login")
                        .passwordParameter("senha")
                        .successHandler(authenticationSuccessHandler)
                        .failureHandler((request, response, exception) -> {
                            String destino = exception instanceof DisabledException
                                    ? "/login?pendente=1"
                                    : "/login?erro=1";
                            response.sendRedirect(destino);
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=1")
                        .deleteCookies("remember-me", "JSESSIONID")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
