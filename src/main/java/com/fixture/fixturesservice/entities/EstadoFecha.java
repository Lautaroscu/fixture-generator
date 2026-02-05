package com.fixture.fixturesservice.entities;

import java.util.HashSet;
import java.util.Set;

public class EstadoFecha {

    private Set<Integer> canchasOcupadas = new HashSet<>();
    private Set<Integer> clubesLocalesA = new HashSet<>();

    public boolean canchaLibre(Cancha c) {
        return !canchasOcupadas.contains(c.getId());
    }

    public void ocuparCancha(Cancha c) {
        canchasOcupadas.add(c.getId());
    }

    public void liberarCancha(Cancha c) {
        canchasOcupadas.remove(c.getId());
    }

    public void marcarLocalA(int clubId) {
        clubesLocalesA.add(clubId);
    }

    public boolean fueLocalA(int clubId) {
        return clubesLocalesA.contains(clubId);
    }
}
