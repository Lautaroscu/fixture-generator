package com.fixture.fixturesservice.DTOS;

import com.fixture.fixturesservice.entities.Equipo;
import com.fixture.fixturesservice.enums.Bloque;
import com.fixture.fixturesservice.enums.Categoria;
import com.fixture.fixturesservice.enums.DiaJuego;
import com.fixture.fixturesservice.enums.Liga;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@Getter
@AllArgsConstructor
public class EquipoDTO {

    private int id;
    private String nombre;
    private int jerarquia;
    private Bloque bloque;
    private Set<Categoria> categoriasHabilitadas;
    private Integer clubId;
    private String clubNombre;
    private Liga divisionMayor;
    private DiaJuego diaDeJuego;
    public static EquipoDTO toDTO(Equipo e) {
        return new EquipoDTO(
                e.getId(),
                e.getNombre(),
                e.getJerarquia(),
                e.getBloque(),
                e.getCategoriasHabilitadas(),
                e.getClub() != null ? e.getClub().getId() : null,
                e.getClub() != null ? e.getClub().getNombre() : null,
                e.getDivisionMayor(),
                e.getDiaDeJuego()
        );
    }

}
