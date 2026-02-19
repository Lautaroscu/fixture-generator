package com.fixture.fixturesservice.services;

import com.fixture.fixturesservice.DTOS.EquipoConfig;
import com.fixture.fixturesservice.DTOS.EquiposConfig;
import com.fixture.fixturesservice.entities.Cancha;
import com.fixture.fixturesservice.entities.Club;
import com.fixture.fixturesservice.entities.Equipo;
import com.fixture.fixturesservice.enums.Bloque;
import com.fixture.fixturesservice.enums.Categoria;
import com.fixture.fixturesservice.enums.DiaJuego;
import com.fixture.fixturesservice.enums.Liga;
import com.fixture.fixturesservice.repositories.*;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataInitializer {

    @Autowired
    private EquipoRepository equipoRepo;

    @Autowired
    private CanchaRepository canchaRepository;

    @Autowired
    private PartidoRepository partidoRepository;
    @Autowired
    private FechaRepository fechaRepository;
    @Autowired
    private ClubRepository clubRepo;

    @Transactional
    public void initDesdeJson() throws IOException {
        this.deleteAll();
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = new ClassPathResource("data/equipos.json").getInputStream();
        EquiposConfig config = mapper.readValue(is, EquiposConfig.class);

        // 1. ACÁ AGREGAMOS LOS BLOQUES FEMENINOS
        Map<Bloque, List<String>> definicionBloques = Map.of(
                Bloque.MAYORES, List.of("primera", "reserva"),
                Bloque.JUVENILES, List.of("quinta", "sexta", "septima", "octava"),
                Bloque.INFANTILES, List.of("novena", "decima", "undecima"),
                // ¡IMPORTANTE! Agregar esto para que lea el femenino
                Bloque.FEM_MAYORES, List.of("femenino_primera", "femenino_sub16"),
                Bloque.FEM_MENORES, List.of("femenino_sub14", "femenino_sub12")
        );

        for (EquipoConfig ec : config.equipos) {
            Cancha cancha = (Cancha) canchaRepository.findByName(ec.estadioLocal)
                    .orElseGet(() -> canchaRepository.save(new Cancha(ec.estadioLocal)));

            if (clubRepo.existsBySede(cancha)) {
                cancha.setCompartida(true);
                canchaRepository.save(cancha);
            }

            Club club = clubRepo.findByNombre(ec.nombre).orElseGet(() -> {
                Club nuevoClub = new Club();
                nuevoClub.setNombre(ec.nombre);
                nuevoClub.setSede(cancha);
                return clubRepo.save(nuevoClub);
            });

           // Iteramos sobre TODOS los bloques (Masc y Fem)
            for (Map.Entry<Bloque, List<String>> def : definicionBloques.entrySet()) {
                Bloque tipoBloque = def.getKey();
                List<String> categoriasDelBloque = def.getValue();

                // Filtramos y MAPEAMOS los nombres
                Set<Categoria> habilitadas = categoriasDelBloque.stream()
                        .filter(catKey -> ec.categorias.containsKey(catKey) && Boolean.TRUE.equals(ec.categorias.get(catKey)))
                        .map(catKey -> {
                            // TRANSFORMACIÓN DE NOMBRE JSON -> ENUM
                            // Si en el JSON dice "femenino_primera", lo convertimos a "FEM_PRIMERA" (o como esté en tu Enum)
                            if (catKey.startsWith("femenino_")) {
                                return Categoria.valueOf("FEM_" + catKey.replace("femenino_", "").toUpperCase());
                            }
                            return Categoria.valueOf(catKey.toUpperCase());
                        })
                        .collect(Collectors.toSet());

                // Si tiene categorías habilitadas, creamos el "Equipo" (Bloque)
                if (!habilitadas.isEmpty()) {
                    Equipo equipo = new Equipo();
                    equipo.setClub(club); // Usamos el club que creamos arriba
                    equipo.setBloque(tipoBloque);
                    equipo.setCategoriasHabilitadas(habilitadas);
                    equipo.setNombre(ec.nombre);
                    // --- ASIGNACIÓN INTELIGENTE DE LIGA ---
                    // --- ASIGNACIÓN INTELIGENTE DE LIGA ---
                    if (tipoBloque == Bloque.FEM_MAYORES || tipoBloque == Bloque.FEM_MENORES) {
                        equipo.setDivisionMayor(Liga.A);
                    }
                    else if (tipoBloque == Bloque.INFANTILES && ec.divisionInfantiles != null && !ec.divisionInfantiles.isEmpty()) {
                        // Forzamos mayúscula para evitar errores de tipeo en el JSON
                        equipo.setDivisionMayor(Liga.valueOf(ec.divisionInfantiles.toUpperCase()));
                    }
                    else {
                        equipo.setDivisionMayor(Liga.valueOf(ec.divisionMayor.toUpperCase()));
                    }
                    // Asignamos días de juego
                    if (tipoBloque == Bloque.JUVENILES || tipoBloque == Bloque.FEM_MAYORES || tipoBloque == Bloque.FEM_MENORES) {
                        equipo.setDiaDeJuego(DiaJuego.SABADO);
                    } else {
                        equipo.setDiaDeJuego(DiaJuego.DOMINGO);
                    }
                    equipoRepo.save(equipo);
                }
            }
        }
    }
    private void deleteAll() {
        partidoRepository.deleteAllInBatch();
        fechaRepository.deleteAllInBatch();
        equipoRepo.deleteAllInBatch();
        clubRepo.deleteAllInBatch();
        canchaRepository.deleteAllInBatch();
    }
}