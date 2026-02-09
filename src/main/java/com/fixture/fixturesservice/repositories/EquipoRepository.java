package com.fixture.fixturesservice.repositories;

import com.fixture.fixturesservice.entities.Cancha;
import com.fixture.fixturesservice.entities.Equipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipoRepository extends JpaRepository<Equipo , Integer> {
    List<Equipo> findAllByJuegaA(boolean b);

    boolean existsBySede(Cancha cancha);
}
