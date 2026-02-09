package com.fixture.fixturesservice.DTOS;

import com.fixture.fixturesservice.entities.Equipo;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
@Getter
@Setter
public class EstadoFecha {
    Set<Integer> sedesUsadas;
    Map<Integer, EstadoEquipo> estadoPorEquipo; //equipo id -> Estado Equipo

    public EstadoFecha() {
        sedesUsadas = new HashSet<>();
        estadoPorEquipo = new HashMap<>();
    }


    public void addSedeUsada(int id) {
        sedesUsadas.add(id);
    }
    public void removeSedeUsada(int id) {sedesUsadas.remove(id);}

    public EstadoEquipo getEstado(Integer loc) {
        return  estadoPorEquipo.get(loc);
    }

    public EstadoFecha snapshot() {
        EstadoFecha copia = new EstadoFecha();
        copia.sedesUsadas = new HashSet<>(this.sedesUsadas);

        // Copiamos el mapa de estados de equipos (CREANDO nuevos objetos EstadoEquipo)
        for (Map.Entry<Integer, EstadoEquipo> entry : this.estadoPorEquipo.entrySet()) {
            EstadoEquipo original = entry.getValue();
            EstadoEquipo clon = new EstadoEquipo();
            clon.setUltimasConsecutivas(original.getUltimasConsecutivas());
            clon.setUsoQuiebre(original.isUsoQuiebre());
            clon.setTotalPartidosLocal(original.getTotalPartidosLocal());
            copia.estadoPorEquipo.put(entry.getKey(), clon);
        }
        return copia;
    }

    public void restore(EstadoFecha snapshot) {
        this.sedesUsadas = new HashSet<>(snapshot.getSedesUsadas());
        this.estadoPorEquipo = new HashMap<>();
        for (Map.Entry<Integer, EstadoEquipo> entry : snapshot.getEstadoPorEquipo().entrySet()) {
            this.estadoPorEquipo.put(entry.getKey(), new EstadoEquipo(entry.getValue()));
        }
    }
}
