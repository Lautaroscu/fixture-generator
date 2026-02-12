package com.fixture.fixturesservice.services;

import com.fixture.fixturesservice.DTOS.EquipoConfig;
import com.fixture.fixturesservice.DTOS.EquiposConfig;
import com.fixture.fixturesservice.entities.Cancha;
import com.fixture.fixturesservice.entities.Equipo;
import com.fixture.fixturesservice.enums.Categoria;
import com.fixture.fixturesservice.repositories.CanchaRepository;
import com.fixture.fixturesservice.repositories.EquipoRepository;
import com.fixture.fixturesservice.repositories.FechaRepository;
import com.fixture.fixturesservice.repositories.PartidoRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
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

        @Transactional
        public void initDesdeJson() throws IOException {

            this.deleteAll();
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource("data/equipos.json").getInputStream();

            EquiposConfig config = mapper.readValue(is, EquiposConfig.class);

            for (EquipoConfig ec : config.equipos) {

                Cancha cancha = (Cancha) canchaRepository
                        .findByName(ec.estadioLocal)
                        .orElseGet(() -> canchaRepository.save(new Cancha(ec.estadioLocal)));

                if(equipoRepo.existsBySede(cancha)) {
                    cancha.setCompartida(true);
                    canchaRepository.save(cancha);
                }

                Equipo equipo = new Equipo();
                equipo.setNombre(ec.nombre);
                equipo.setJuegaA("A".equals(ec.divisionMayor));
                equipo.setSede(cancha);
                equipo.setJerarquia(ec.jerarquia);

                Set<Categoria> cats = ec.categorias.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(e -> Categoria.valueOf(e.getKey().toUpperCase()))
                        .collect(Collectors.toSet());

                equipo.setCategorias(cats);

                equipoRepo.save(equipo);
            }
        }
    private void deleteAll(){
        partidoRepository.deleteAll();
        fechaRepository.deleteAll();
        equipoRepo.deleteAll();
        canchaRepository.deleteAll();
    }
}