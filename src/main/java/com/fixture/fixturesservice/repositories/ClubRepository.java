package com.fixture.fixturesservice.repositories;

import com.fixture.fixturesservice.entities.Cancha;
import com.fixture.fixturesservice.entities.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClubRepository extends JpaRepository<Club,Integer> {
    boolean existsBySede(Cancha cancha);

    Optional<Club> findByNombre(String nombre);

    Optional<Club>  findBySede(Cancha cancha);
}
