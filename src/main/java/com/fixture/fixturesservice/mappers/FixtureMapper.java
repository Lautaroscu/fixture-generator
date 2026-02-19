package com.fixture.fixturesservice.mappers;

import com.fixture.fixturesservice.DTOS.FechaDTO;
import com.fixture.fixturesservice.DTOS.PartidoDTO;
import com.fixture.fixturesservice.entities.Fecha;
import com.fixture.fixturesservice.entities.Partido;

import java.util.List;

public class FixtureMapper {

    public static FechaDTO toFechaDTO(Fecha fecha) {
        List<PartidoDTO> partidos = fecha.getPartidos()
                .stream()
                .map(FixtureMapper::toPartidoDTO)
                .toList();

        return new FechaDTO(fecha.getNroFecha(), partidos, fecha.getLiga().name());
    }

    private static PartidoDTO toPartidoDTO(Partido p) {
        return new PartidoDTO(
                p.getLocal().getNombre(),
                p.getVisitante().getNombre(),
                p.getCancha().getName()
        );
    }
}
