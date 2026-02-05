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

    @ManyToOne
    private Equipo local;

    @ManyToOne
    private Equipo visitante;

    @ManyToOne
    private Cancha cancha;

    @ManyToOne
    private Fecha fecha;
}

