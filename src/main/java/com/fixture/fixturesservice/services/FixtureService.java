package com.fixture.fixturesservice.services;

import com.fixture.fixturesservice.DTOS.*;
import com.fixture.fixturesservice.DTOS.EstadoFecha;
import com.fixture.fixturesservice.entities.*;
import com.fixture.fixturesservice.enums.*;
import com.fixture.fixturesservice.mappers.FixtureMapper;
import com.fixture.fixturesservice.repositories.*;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FixtureService {

    @Autowired
    private EquipoRepository equipoRepository;
    @Autowired
    private FechaRepository fechaRepository;

    private List<Fecha> mejorSolucionA;
    private List<Fecha> mejorSolucionB;
    private int menorCantidadQuiebres = Integer.MAX_VALUE;
    private long tiempoLimite;

    public ResponseDTO generar() {
        fechaRepository.deleteAll();
        List<Equipo> equiposA = equipoRepository.findAllByJuegaA(true);
        List<Equipo> equiposB = equipoRepository.findAllByJuegaA(false);

        this.mejorSolucionA = null;
        this.mejorSolucionB = null;
        this.menorCantidadQuiebres = Integer.MAX_VALUE;
        this.tiempoLimite = System.currentTimeMillis() + 10000; // 10 segundos

        List<Fecha> fechasA = generarEsqueletoRobin(equiposA, Liga.A);
        List<Fecha> fechasB = generarEsqueletoRobin(equiposB, Liga.B);

        EstadoFecha historialInicial = new EstadoFecha();
        inicializarHistorial(historialInicial, equiposA, equiposB);

        resolverPartidosFecha(fechasA, fechasB, 0, historialInicial);

        if (mejorSolucionA != null) {
            persistirFixture(mejorSolucionA);
            persistirFixture(mejorSolucionB);
            return new ResponseDTO("Fixture optimizado con " + menorCantidadQuiebres + " quiebres.", true);
        }
        return new ResponseDTO("No se pudo generar un fixture válido.", false);
    }

    private boolean resolverPartidosFecha(List<Fecha> fechasA, List<Fecha> fechasB, int fIdX, EstadoFecha estadoGlobal) {
        if (fIdX >= fechasA.size() && fIdX >= fechasB.size()) {
            int quiebresActuales = calcularPenalizacion(fechasA) + calcularPenalizacion(fechasB);

            if (quiebresActuales < menorCantidadQuiebres) {
                menorCantidadQuiebres = quiebresActuales;
                mejorSolucionA = clonarFechas(fechasA);
                mejorSolucionB = clonarFechas(fechasB);
            }
            return false;
        }

        if (System.currentTimeMillis() > tiempoLimite) return false;

        EstadoFecha snapshotSeguridad = estadoGlobal.snapshot();
        estadoGlobal.getSedesUsadas().clear();

        List<Partido> partidosDelDia = recolectarPartidos(fechasA, fechasB, fIdX);

        backtrackingPartidos(fechasA, fechasB, partidosDelDia, 0, fIdX, estadoGlobal);

        estadoGlobal.restore(snapshotSeguridad);
        return false;
    }

    private boolean backtrackingPartidos(List<Fecha> fechasA, List<Fecha> fechasB, List<Partido> partidos, int pIdx, int fIdx, EstadoFecha estadoFecha) {
        if (partidos.size() == pIdx) {
            return resolverPartidosFecha(fechasA, fechasB, fIdx + 1, estadoFecha);
        }

        Partido p = partidos.get(pIdx);
        Equipo e1 = p.getLocal();
        Equipo e2 = p.getVisitante();

        boolean e1PrefiereLocal = estadoFecha.getEstado(e1.getId()).getUltimasConsecutivas() < 0;

        if (e1PrefiereLocal) {
            probarOpcion(e1, e2, p, estadoFecha, fechasA, fechasB, partidos, pIdx, fIdx);
            probarOpcion(e2, e1, p, estadoFecha, fechasA, fechasB, partidos, pIdx, fIdx);
        } else {
            probarOpcion(e2, e1, p, estadoFecha, fechasA, fechasB, partidos, pIdx, fIdx);
            probarOpcion(e1, e2, p, estadoFecha, fechasA, fechasB, partidos, pIdx, fIdx);
        }
        return false;
    }

    private boolean probarOpcion(Equipo loc, Equipo vis, Partido p, EstadoFecha estado, List<Fecha> fA, List<Fecha> fB, List<Partido> pts, int pIdx, int fIdx) {
        if (esValido(loc, vis, estado)) {
            EstadoFecha efm = estado.snapshot();
            Partido pm1 = p.memento();

            aplicarLocalia(loc, vis, p, estado);
            backtrackingPartidos(fA, fB, pts, pIdx + 1, fIdx, estado);

            estado.restore(efm);
            p.restore(pm1);
        }
        return false;
    }

    private List<Fecha> clonarFechas(List<Fecha> originales) {
        List<Fecha> copias = new ArrayList<>();
        for (Fecha f : originales) {
            Fecha copiaF = new Fecha(f.getNroFecha());
            copiaF.setLiga(f.getLiga());
            for (Partido p : f.getPartidos()) {
                Partido copiaP = new Partido();
                copiaP.setLocal(p.getLocal());
                copiaP.setVisitante(p.getVisitante());
                copiaP.setCancha(p.getCancha());
                copiaP.setFecha(copiaF);
                copiaF.getPartidos().add(copiaP);
            }
            copias.add(copiaF);
        }
        return copias;
    }

    private int calcularPenalizacion(List<Fecha> fechas) {
        int quiebres = 0;
        Map<Integer, Integer> ultEstado = new HashMap<>(); // ID -> última condición (1 local, -1 vis)

        for (Fecha f : fechas) {
            for (Partido p : f.getPartidos()) {
                if (p.getLocal() == null) continue;

                int locId = p.getLocal().getId();
                int visId = p.getVisitante().getId();

                if (ultEstado.getOrDefault(locId, 0) == 1) quiebres++;
                if (ultEstado.getOrDefault(visId, 0) == -1) quiebres++;

                ultEstado.put(locId, 1);
                ultEstado.put(visId, -1);
            }
        }
        return quiebres;
    }


    private void inicializarHistorial(EstadoFecha historial, List<Equipo> equiposA, List<Equipo> equiposB) {
        for (Equipo e : equiposA) historial.getEstadoPorEquipo().put(e.getId(), new EstadoEquipo());
        for (Equipo e : equiposB) historial.getEstadoPorEquipo().put(e.getId(), new EstadoEquipo());
    }

    private List<Partido> recolectarPartidos(List<Fecha> fA, List<Fecha> fB, int idx) {
        List<Partido> partidos = new ArrayList<>();
        validarYAgregar(partidos, fA, idx);
        validarYAgregar(partidos, fB, idx);
        partidos.sort((p1, p2) -> Boolean.compare(p2.getLocal().getSede().isCompartida(), p1.getLocal().getSede().isCompartida()));
        return partidos;
    }

    private void validarYAgregar(List<Partido> destino, List<Fecha> fuente, int idx) {

        if (idx < fuente.size()) {

            for (Partido p : fuente.get(idx).getPartidos()) {
                if (p != null && p.getLocal() != null && p.getVisitante() != null) {
                    destino.add(p);
                }
            }
        }
    }
    private void aplicarLocalia(Equipo loc, Equipo vis, Partido p, EstadoFecha estado) {
        p.setLocal(loc);
        p.setVisitante(vis);
        p.setCancha(loc.getSede());
        estado.addSedeUsada(loc.getSede().getId());
        estado.getEstado(loc.getId()).actualizar(true);
        estado.getEstado(vis.getId()).actualizar(false);
    }

    public boolean esValido(Equipo loc, Equipo vis, EstadoFecha estadoFecha) {
        if (estadoFecha.getSedesUsadas().contains(loc.getSede().getId())) return false;
        EstadoEquipo el = estadoFecha.getEstado(loc.getId());
        EstadoEquipo ev = estadoFecha.getEstado(vis.getId());
        return el.getUltimasConsecutivas() < 2 && ev.getUltimasConsecutivas() > -2;
    }

    public List<Fecha> generarEsqueletoRobin(List<Equipo> equipos, Liga liga) {
        List<Equipo> copia = new ArrayList<>(equipos);
        Collections.shuffle(copia);
        Equipo libre = new Equipo("LIBRE");
        libre.setId(-99);
        if (copia.size() % 2 != 0) copia.add(libre);


        int n = copia.size();
        List<Fecha> torneo = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            Fecha f = new Fecha(i + 1);
            f.setLiga(liga);
            for (int j = 0; j < n / 2; j++) {
                agregarPartido(f, copia.get(j), copia.get(n - 1 - j));
            }
            torneo.add(f);
            rotarCircleMethod(copia);
        }
        return torneo;
    }

    private void rotarCircleMethod(List<Equipo> lista) {
        lista.add(1, lista.remove(lista.size() - 1));
    }

    private void agregarPartido(Fecha f, Equipo loc, Equipo vis) {
        Partido p = new Partido();
        p.setLocal(loc); p.setVisitante(vis); p.setFecha(f);
        f.getPartidos().add(p);
    }

    private void persistirFixture(List<Fecha> fechas) {
        fechaRepository.saveAll(fechas);
    }

    public @Nullable List<Equipo> getEquipos() {

        return equipoRepository.findAll();

    }

    public List<FechaDTO> obtenerFixture(Liga liga) {
        liga = liga == null ? Liga.A : liga;
        List<Fecha> fechas = fechaRepository.findAllByLigaOrderByNroFechaAsc(liga);
        return fechas.stream()

                .map(FixtureMapper::toFechaDTO)

                .toList();

    }
}