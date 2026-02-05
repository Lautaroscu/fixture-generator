package com.fixture.fixturesservice.DTOS;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class FechaDTO {

    private int nroFecha;
    private List<PartidoDTO> partidos;
}
