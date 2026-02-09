package com.fixture.fixturesservice.DTOS;


import com.fixture.fixturesservice.enums.Categoria;
import com.fixture.fixturesservice.enums.Liga;
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
    private Categoria categoria;
    private Liga liga;
}
