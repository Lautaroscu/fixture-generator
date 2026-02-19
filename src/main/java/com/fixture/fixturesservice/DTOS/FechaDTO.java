package com.fixture.fixturesservice.DTOS;


import com.fixture.fixturesservice.enums.Categoria;
import com.fixture.fixturesservice.enums.Liga;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class FechaDTO {

    private int nroFecha;
    private List<PartidoDTO> partidos = new ArrayList<>();
    private String liga;
    public FechaDTO(){}
    public FechaDTO(int nroFecha , String liga) {
        this.nroFecha = nroFecha;
        this.liga = liga;
    }
    public FechaDTO(int nroFecha , String liga , List<PartidoDTO> partidos) {
        this(nroFecha , liga);
        this.partidos = partidos;
    }

    public void addPartido(PartidoDTO normal) {
        partidos.add(normal);
    }
}
