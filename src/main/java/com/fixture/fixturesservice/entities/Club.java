package com.fixture.fixturesservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Entity
@Setter
@Getter
public class Club {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String nombre;
    @ManyToOne
    @JoinColumn(name = "cancha_id")
    private Cancha sede;
    @OneToMany(mappedBy = "club")
    private List<Equipo> equipos;
    private String localidad;
}