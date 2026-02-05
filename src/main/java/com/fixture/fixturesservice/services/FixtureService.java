package com.fixture.fixturesservice.services;

import com.fixture.fixturesservice.DTOS.FechaDTO;
import com.fixture.fixturesservice.entities.Equipo;
import com.fixture.fixturesservice.entities.Fecha;
import com.fixture.fixturesservice.entities.Partido;
import com.fixture.fixturesservice.enums.Categoria;
import com.fixture.fixturesservice.enums.DiaJuego;
import com.fixture.fixturesservice.mappers.FixtureMapper;
import com.fixture.fixturesservice.repositories.CanchaRepository;
import com.fixture.fixturesservice.repositories.EquipoRepository;
import com.fixture.fixturesservice.repositories.FechaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class FixtureService {



        @Autowired
        private EquipoRepository equipoRepository;
        @Autowired
        private CanchaRepository canchaRepository;
        @Autowired
        private FechaRepository fechaRepository;

    public List<FechaDTO> obtenerFixture() {
        List<Fecha> fechas = fechaRepository.findAllByOrderByNroFechaAsc();

        return fechas.stream()
                .map(FixtureMapper::toFechaDTO)
                .toList();
    }

    public String generar() {
            List<Equipo> equiposA = equipoRepository.findAllByJuegaA(true);
            List<Equipo> equiposB = equipoRepository.findAllByJuegaA(false);
            List<Fecha> fechasA = generarEsqueletoRobin(equiposA);
        List<Fecha> fechasB = generarEsqueletoRobin(equiposB);
        Map<Categoria, DiaJuego> diaPorCategoria = Map.of(
                Categoria.PRIMERA, DiaJuego.DOMINGO,
                Categoria.RESERVA, DiaJuego.DOMINGO,
                Categoria.NOVENA, DiaJuego.DOMINGO,
                Categoria.DECIMA, DiaJuego.DOMINGO,
                Categoria.UNDECIMA, DiaJuego.DOMINGO,
                Categoria.QUINTA, DiaJuego.SABADO,
                Categoria.SEXTA, DiaJuego.SABADO,
                Categoria.SEPTIMA, DiaJuego.SABADO
        );




        if (resolverLocalia(fechasA, 0) && resolverLocalia(fechasB , 0)) {
                return  "¡Fixture generado con éxito!";
            } else {
                return  "Infactible con las restricciones dadas.";
            }
        }

    private boolean resolverLocalia(List<Fecha> fixture, int fIdx) {
        if (fIdx == fixture.size()) {
            return true;
        }

        Fecha fechaActual = fixture.get(fIdx);
        Set<Integer> sedesUsadasHoy = new HashSet<>();

        return backtrackingPartidos(fechaActual.getPartidos(), 0, fIdx, fixture, sedesUsadasHoy);
    }

    private boolean backtrackingPartidos(List<Partido> partidos, int pIdx, int fIdx,
                                         List<Fecha> fixture, Set<Integer> sedesUsadas) {
        if (pIdx == partidos.size()) {
            return resolverLocalia(fixture, fIdx + 1);
        }

        Partido p = partidos.get(pIdx);
        Equipo e1 = p.getLocal();
        Equipo e2 = p.getVisitante();

        // PROBAR OPCIÓN A: E1 Local, E2 Visitante
        if (esValido(e1, e2, sedesUsadas)) {
            Equipo m1 = e1.memento();
            Equipo m2 = e2.memento();

            aplicarLocalia(e1, e2, p, sedesUsadas);
            if (backtrackingPartidos(partidos, pIdx + 1, fIdx, fixture, sedesUsadas)) return true;

            deshacerLocalia(e1, e2, m1, m2, sedesUsadas, p);
        }

        // PROBAR OPCIÓN B: E2 Local, E1 Visitante
        if (esValido(e2, e1, sedesUsadas)) {
            Equipo m1 = e1.memento();
            Equipo m2 = e2.memento();

            aplicarLocalia(e2, e1, p, sedesUsadas);
            if (backtrackingPartidos(partidos, pIdx + 1, fIdx, fixture, sedesUsadas)) return true;

            deshacerLocalia(e1, e2, m1, m2, sedesUsadas, p);
        }
        return false;
    }

    private void aplicarLocalia(Equipo loc, Equipo vis, Partido p, Set<Integer> sedesUsadas) {
        p.setLocal(loc);
        p.setVisitante(vis);
        p.setCancha(loc.getSede());

        // Actualizar lógica de negocio del equipo
        loc.actualizarEstado(true);  // Método que incrementa consecutivas y marca quiebre si corresponde
        vis.actualizarEstado(false); // Método que decrementa consecutivas y marca quiebre si corresponde

        sedesUsadas.add(loc.getSede().getId());
    }

    private void deshacerLocalia(Equipo e1, Equipo e2, Equipo m1, Equipo m2, Set<Integer> sedesUsadas, Partido p) {
        // Restaurar los equipos a su estado anterior usando el memento
        e1.restaurarEstado(m1);
        e2.restaurarEstado(m2);

        // Quitar la sede de la lista de ocupadas para esta fecha
        if (p.getLocal() != null) {
            sedesUsadas.remove(p.getLocal().getSede().getId());
        }
    }


    public boolean esValido(Equipo loc, Equipo vis, Set<Integer> sedesOcupadas) {
        if (sedesOcupadas.contains(loc.getSede().getId())) return false;

        // Solo rebota si YA es local (1) e intenta ser local de nuevo teniendo el quiebre usado
        if (loc.getUltimasConsecutivas() == 1 && loc.isUsoQuiebre()) return false;

        // Solo rebota si YA es visitante (-1) e intenta ser visitante de nuevo teniendo el quiebre usado
        if (vis.getUltimasConsecutivas() == -1 && vis.isUsoQuiebre()) return false;

        return true;
    }
    public List<Fecha> generarEsqueletoRobin(List<Equipo> equipos) {

        List<Fecha> torneo = new ArrayList<>();
        int numEquipos = equipos.size();
        int numFechas = numEquipos - 1;
        int partidosPorFecha = numEquipos / 2;

        List<Equipo> rotativo = new ArrayList<>(equipos);

        for (int i = 0; i < numFechas; i++) {
            Fecha fecha = new Fecha(i + 1);

            for (int j = 0; j < partidosPorFecha; j++) {
                Equipo local = rotativo.get(j);
                Equipo visitante = rotativo.get(numEquipos - 1 - j);

                Partido partido = new Partido();
                partido.setLocal(local);
                partido.setVisitante(visitante);
                partido.setFecha(fecha);

                fecha.getPartidos().add(partido);
            }

            torneo.add(fecha);
            rotarLista(rotativo);
        }

        return torneo;
    }


    private void rotarLista(List<Equipo> lista) {
        // El primer equipo se queda fijo, el resto rota
        Equipo fijo = lista.get(0);
        Equipo ultimo = lista.get(lista.size() - 1);

        // Desplazamos hacia la derecha
        for (int i = lista.size() - 1; i > 1; i--) {
            lista.set(i, lista.get(i - 1));
        }
        lista.set(1, ultimo);
    }
}
