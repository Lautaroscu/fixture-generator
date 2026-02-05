package com.fixture.fixturesservice.repositories;

import com.fixture.fixturesservice.entities.Cancha;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CanchaRepository extends JpaRepository<Cancha , Integer> {
    <T> Optional<T> findByName(String estadioLocal);
}
