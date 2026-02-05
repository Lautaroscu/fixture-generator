package com.fixture.fixturesservice.entities;

import com.fixture.fixturesservice.enums.Categoria;
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

    @ManyToOne
    @JoinColumn(name = "cancha_id")
    private Cancha sede;

    // Campos para el algoritmo (no se guardan en DB)
    @Transient
    private int ultimasConsecutivas;

    @Transient
    private boolean usoQuiebre;

    @Transient
    private int totalPartidosLocal;
    private boolean juegaA;
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    private Set<Categoria> categorias = new HashSet<>();

    private int jerarquia;

    // Constructor para JPA
    public Equipo() {}

    public Equipo(String name) {
        this.nombre = name;

    }

    // Constructor para negocio
    public Equipo(String nombre, Cancha sede) {
        this.nombre = nombre;
        this.sede = sede;
        this.ultimasConsecutivas = 0;
        this.usoQuiebre = false;
        this.totalPartidosLocal = 0;
    }
    public Equipo memento() {
        Equipo copia = new Equipo();
        copia.ultimasConsecutivas = this.ultimasConsecutivas;
        copia.usoQuiebre = this.usoQuiebre;
        copia.totalPartidosLocal = this.totalPartidosLocal;
        return copia;
    }

    public void restaurarEstado(Equipo e) {
        this.ultimasConsecutivas = e.getUltimasConsecutivas();
        this.usoQuiebre = e.isUsoQuiebre();
        this.totalPartidosLocal = e.getTotalPartidosLocal();
    }
    public void actualizarEstado(boolean seraLocal) {
        if (seraLocal) {
            if (this.ultimasConsecutivas == 1) this.usoQuiebre = true;
            this.ultimasConsecutivas = 1;
            this.totalPartidosLocal++;
        } else {
            if (this.ultimasConsecutivas == -1) this.usoQuiebre = true;
            this.ultimasConsecutivas = -1;
        }
    }

}
