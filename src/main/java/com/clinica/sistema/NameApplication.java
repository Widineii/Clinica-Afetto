package com.clinica.sistema;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NameApplication {

    public static void main(String[] args) {
        SpringApplication.run(NameApplication.class, args);
    }

}
