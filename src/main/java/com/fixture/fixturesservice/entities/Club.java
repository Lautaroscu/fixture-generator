package com.fixture.fixturesservice.entities;

import com.fixture.fixturesservice.enums.Categoria;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
}