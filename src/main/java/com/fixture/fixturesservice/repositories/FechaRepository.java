package com.fixture.fixturesservice.repositories;

import com.fixture.fixturesservice.entities.Fecha;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FechaRepository extends JpaRepository<Fecha ,Integer> {
    List<Fecha> findAllByOrderByNroFechaAsc();
}
