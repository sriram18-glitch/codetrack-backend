package com.codetrack.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CodeTrackBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeTrackBackendApplication.class, args);
    }
}
