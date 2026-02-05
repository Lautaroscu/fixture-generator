package com.fixture.fixturesservice.DTOS;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PartidoDTO {

    private String local;
    private String visitante;
    private String cancha;
}