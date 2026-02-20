package com.fixture.fixturesservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FixturesServiceApplication {
    public static void main(String[] args) {

        SpringApplication.run(FixturesServiceApplication.class, args);
    }

}
