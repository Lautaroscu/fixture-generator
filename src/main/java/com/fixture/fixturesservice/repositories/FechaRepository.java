package com.fixture.fixturesservice.repositories;

import com.fixture.fixturesservice.entities.Fecha;
import com.fixture.fixturesservice.enums.Bloque;
import com.fixture.fixturesservice.enums.Categoria;
import com.fixture.fixturesservice.enums.Liga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FechaRepository extends JpaRepository<Fecha ,Integer> {
    List<Fecha> findAllByLigaOrderByNroFechaAsc(Liga liga);


    List<Fecha> findAllByLigaAndBloqueOrderByNroFechaAsc(Liga liga, Bloque bloque);
}
