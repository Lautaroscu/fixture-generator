package com.fixture.fixturesservice.DTOS;

import com.fixture.fixturesservice.entities.Equipo;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
public class EstadoFecha {
    // SedeID -> ClubID
    Map<Integer, Integer> sedesUsadas;
    Map<Integer, EstadoEquipo> estadoPorEquipo;
    private Set<Integer> clubesLocales = new HashSet<>();
    private Set<Integer> visitantes = new HashSet<>();

    public EstadoFecha() {
        sedesUsadas = new HashMap<>();
        estadoPorEquipo = new HashMap<>();
    }

    public void addSedeUsada(int sedeId, int clubId) {
        sedesUsadas.put(sedeId, clubId);
    }

    public void removeSedeUsada(int id) {
        sedesUsadas.remove(id);
    }

    public Integer getOcupanteSede(int sedeId) {
        return sedesUsadas.get(sedeId);
    }

    public void addVisitante(int id) {
        visitantes.add(id);
    }

    public boolean jugoComoVisitante(Integer id) {
        return visitantes.contains(id);
    }

    public EstadoEquipo getEstado(Integer loc) {
        return estadoPorEquipo.get(loc);
    }

    public EstadoFecha snapshot() {
        EstadoFecha copia = new EstadoFecha();
        copia.sedesUsadas = new HashMap<>(this.sedesUsadas);
        copia.clubesLocales.addAll(this.clubesLocales);
        copia.visitantes.addAll(this.visitantes);
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
        this.sedesUsadas = new HashMap<>(snapshot.getSedesUsadas());
        this.clubesLocales = new HashSet<>(snapshot.clubesLocales);
        this.visitantes = new HashSet<>(snapshot.getVisitantes());
        this.estadoPorEquipo = new HashMap<>();
        for (Map.Entry<Integer, EstadoEquipo> entry : snapshot.getEstadoPorEquipo().entrySet()) {
            this.estadoPorEquipo.put(entry.getKey(), new EstadoEquipo(entry.getValue()));
        }
    }
}
