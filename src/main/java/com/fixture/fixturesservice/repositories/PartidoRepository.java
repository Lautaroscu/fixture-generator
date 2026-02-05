package com.fixture.fixturesservice.repositories;

import com.fixture.fixturesservice.entities.Partido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartidoRepository extends JpaRepository<Partido ,Integer> {
}
