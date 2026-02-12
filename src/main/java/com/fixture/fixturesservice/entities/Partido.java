package com.fixture.fixturesservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Setter
@Getter
public class Partido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_id", nullable = false)
    private Equipo local;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visitante_id", nullable = false)
    private Equipo visitante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancha_id")
    private Cancha cancha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fecha_id")
    private Fecha fecha;

    public Partido memento() {
        Partido copia = new Partido();
        copia.setId(this.getId());
        copia.setLocal(this.getLocal());
        copia.visitante = this.getVisitante();
        copia.cancha = this.getCancha();
        copia.fecha = this.getFecha();
        return copia;
    }

    public void restore(Partido pm1) {
        this.id = pm1.getId();
        this.local = pm1.getLocal();
        this.visitante = pm1.getVisitante();
        this.cancha = pm1.getCancha();
        this.fecha = pm1.getFecha();
    }
}

