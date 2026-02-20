package com.fixture.fixturesservice.controllers;

import com.fixture.fixturesservice.DTOS.EquipoDTO;
import com.fixture.fixturesservice.DTOS.FechaDTO;
import com.fixture.fixturesservice.DTOS.ResponseDTO;
import com.fixture.fixturesservice.entities.Equipo;
import com.fixture.fixturesservice.enums.Categoria;
import com.fixture.fixturesservice.enums.Liga;
import com.fixture.fixturesservice.services.DataInitializer;
import com.fixture.fixturesservice.services.ExcelService;
import com.fixture.fixturesservice.services.FixtureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/fixture")
public class FixtureController {
    @Autowired
    private FixtureService fixtureService;
    @Autowired
    private com.fixture.fixturesservice.services.OrToolsFixtureService orToolsFixtureService;
    @Autowired
    private ExcelService excelService;
    @Autowired
    private DataInitializer dataInitializer;

    @GetMapping("/generar")
    public ResponseEntity<ResponseDTO> generarFixture() {
        return ResponseEntity.ok(fixtureService.generar());
    }

    @GetMapping("/generar-ortools")
    public ResponseEntity<ResponseDTO> generarFixtureOrTools() {
        return ResponseEntity.ok(orToolsFixtureService.generarConOrTools());
    }

    @GetMapping
    public ResponseEntity<List<FechaDTO>> obtenerFixture(@RequestParam Liga liga, @RequestParam Categoria categoria) {
        return ResponseEntity.ok(fixtureService.obtenerFixturePorCategoria(liga, categoria));
    }

    @GetMapping("/equipos")
    public ResponseEntity<List<EquipoDTO>> obtenerFixture() {
        return ResponseEntity.ok(fixtureService.equipos());
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarFixture(@RequestParam Liga liga, @RequestParam Categoria categoria)
            throws IOException {
        List<FechaDTO> fechas = fixtureService.obtenerFixturePorCategoria(liga, categoria);
        byte[] excelContent = excelService.generarExcelFixture(fechas);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fixture_liga_" + liga + ".xlsx")
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelContent);
    }

    @GetMapping("/update-db")
    public ResponseEntity<ResponseDTO> updateDb() throws IOException {
        try {
            dataInitializer.initDesdeJson();
            ResponseDTO responseDTO = new ResponseDTO("Base cargada correctamente", true);
            return ResponseEntity.ok(responseDTO);
        } catch (IOException e) {
            ResponseDTO responseDTO = new ResponseDTO(e.getMessage(), false);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
        }

    }

    @GetMapping("/ping")
    public ResponseEntity<ResponseDTO> ping() {
        return ResponseEntity.ok(new ResponseDTO("pong", true));

    }
}
