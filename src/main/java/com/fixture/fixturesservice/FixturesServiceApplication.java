package com.fixture.fixturesservice;

import com.fixture.fixturesservice.services.FixtureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FixturesServiceApplication {
    public static void main(String[] args) {

        SpringApplication.run(FixturesServiceApplication.class, args);
    }

}
