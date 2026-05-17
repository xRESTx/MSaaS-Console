package com.msaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MsaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsaasApplication.class, args);
    }
}
