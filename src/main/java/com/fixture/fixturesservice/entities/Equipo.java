package com.fixture.fixturesservice.entities;

import com.fixture.fixturesservice.enums.Bloque;
import com.fixture.fixturesservice.enums.Categoria;
import com.fixture.fixturesservice.enums.DiaJuego;
import com.fixture.fixturesservice.enums.Liga;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Setter
@Getter
public class Equipo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String nombre;

    // Campos para el algoritmo (no se guardan en DB)
    @Transient
    private int ultimasConsecutivas;

    @Transient
    private boolean usoQuiebre;

    @Transient
    private int totalPartidosLocal;
    private int jerarquia;
    @Enumerated(EnumType.STRING)
    private Bloque bloque; // EJ: JUVENILES
    @ElementCollection
    private Set<Categoria> categoriasHabilitadas; // EJ: [QUINTA, SEXTA, SEPTIMA]
    @ManyToOne
    @JoinColumn(name = "club_id")
    private Club club;
    @Enumerated(EnumType.STRING)
    private Liga divisionMayor;

    public Liga getLiga() {
        return divisionMayor;
    }

    @Enumerated(EnumType.STRING)
    private DiaJuego diaDeJuego;

    // Constructor para JPA
    public Equipo() {
    }

    public Equipo(String nombre) {
        this.nombre = nombre;
        this.ultimasConsecutivas = 0;
        this.usoQuiebre = false;
        this.totalPartidosLocal = 0;
    }

    public Equipo(Equipo equipo) {
    }

    public Equipo memento() {
        Equipo copia = new Equipo();
        copia.setUltimasConsecutivas(this.ultimasConsecutivas);
        copia.setUsoQuiebre(this.usoQuiebre);
        copia.setTotalPartidosLocal(this.totalPartidosLocal);
        copia.setId(this.id);
        copia.setNombre(this.nombre);
        return copia;
    }

    public void restaurarEstado(Equipo e) {
        this.ultimasConsecutivas = e.getUltimasConsecutivas();
        this.usoQuiebre = e.isUsoQuiebre();
        this.totalPartidosLocal = e.getTotalPartidosLocal();
    }

}
