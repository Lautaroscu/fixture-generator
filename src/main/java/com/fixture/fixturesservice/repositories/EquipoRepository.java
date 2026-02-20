package com.fixture.fixturesservice.repositories;

import com.fixture.fixturesservice.entities.Equipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EquipoRepository extends JpaRepository<Equipo, Integer> {
    @Query("SELECT DISTINCT e FROM Equipo e " +
            "LEFT JOIN FETCH e.categoriasHabilitadas " +
            "LEFT JOIN FETCH e.club c " +
            "LEFT JOIN FETCH c.sede")
    List<Equipo> findAllConRelaciones();
}
