package com.clinica.sistema.config;

import com.clinica.sistema.security.ClinicaAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
            ClinicaAuthenticationProvider clinicaAuthenticationProvider
    ) throws Exception {
        http
                .authenticationProvider(clinicaAuthenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/error", "/actuator/health", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/api/webhooks/**").permitAll()
                        .requestMatchers("/agendamentos/**", "/conta/**", "/pagamentos/**").authenticated()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/webhooks/**"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("login")
                        .passwordParameter("senha")
                        .defaultSuccessUrl("/agendamentos/dashboard", true)
                        .failureUrl("/login?erro=1")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=1")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
