package com.fixture.fixturesservice.controllers;

import com.fixture.fixturesservice.services.FixtureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fixture")
public class FixtureController {
    @Autowired
    private FixtureService fixtureService;


    @GetMapping("/generar")
    public ResponseEntity<String> generarFixture() {
        return ResponseEntity.ok(fixtureService.generar());
    }

}
