package com.fixture.fixturesservice.services;

import com.fixture.fixturesservice.DTOS.*;
import com.fixture.fixturesservice.DTOS.EstadoFecha;
import com.fixture.fixturesservice.entities.*;
import com.fixture.fixturesservice.enums.*;
import com.fixture.fixturesservice.repositories.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class FixtureService {

    @Autowired
    private EquipoRepository equipoRepository;
    @Autowired
    private FechaRepository fechaRepository;

    // 2. ACTUALIZAR GENERAR() para incluir FEMENINO
    @Transactional
    public ResponseDTO generar() {
        fechaRepository.deleteAll();
        List<Equipo> todosLosEquipos = equipoRepository.findAll();

        if (todosLosEquipos.isEmpty())
            return new ResponseDTO("No hay equipos.", false);

        Map<String, List<Equipo>> agrupadosPorTorneo = agruparEquiposPorTorneo(todosLosEquipos);
        int MAX_INTENTOS = 500;
        long TIMEOUT_GLOBAL_POR_DIA_MS = 20000;

        // Resolvemos SÁBADO (Incluye Juveniles Masc + Femenino)
        boolean exitoSabado = resolverBloqueConReinicios(agrupadosPorTorneo, DiaJuego.SABADO, MAX_INTENTOS,
                TIMEOUT_GLOBAL_POR_DIA_MS);

        // Resolvemos DOMINGO (Mayores Masc + Infantiles Masc)
        boolean exitoDomingo = resolverBloqueConReinicios(agrupadosPorTorneo, DiaJuego.DOMINGO, MAX_INTENTOS,
                TIMEOUT_GLOBAL_POR_DIA_MS);

        if (exitoSabado && exitoDomingo) {
            return new ResponseDTO("Fixture MASCULINO y FEMENINO generado con éxito.", true);
        }
        return new ResponseDTO("Sabado: " + exitoSabado + " | Domingo: " + exitoDomingo, false);
    }

    // 3. ACTUALIZAR LA LÓGICA DE PRIORIDAD (Aquí está la clave de "Prioridad
    // Absoluta")
    private List<Partido> recolectarPartidosMulti(List<List<Fecha>> ligas, int fIdx) {
        List<Partido> partidos = new ArrayList<>();
        for (List<Fecha> liga : ligas) {
            if (fIdx < liga.size()) {
                for (Partido p : liga.get(fIdx).getPartidos()) {
                    if (p != null && p.getLocal() != null && p.getVisitante() != null) {
                        partidos.add(p);
                    }
                }
            }
        }

        partidos.sort((p1, p2) -> {
            // CRITERIO 1: Equipos con Sede Compartida PRIMERO (Logística pura)
            boolean comp1 = p1.getLocal().getClub().getSede().isCompartida();
            boolean comp2 = p2.getLocal().getClub().getSede().isCompartida();
            if (comp1 != comp2)
                return Boolean.compare(comp2, comp1);

            // CRITERIO 2: Jerarquía de Bloques (MASCULINO MATA FEMENINO)
            // 1. Mayores Masc (Domingo)
            // 2. Juveniles Masc (Sábado)
            // 3. Infantiles Masc (Domingo)
            // 4. Femenino Mayores (Sábado) -> Se acomoda al final
            // 5. Femenino Menores (Sábado)

            int score1 = getScoreJerarquia(p1.getLocal().getBloque());
            int score2 = getScoreJerarquia(p2.getLocal().getBloque());

            return Integer.compare(score1, score2);
        });

        return partidos;
    }

    // Helper para dar puntaje de prioridad (Menor número = Mayor prioridad)
    private int getScoreJerarquia(Bloque b) {
        switch (b) {
            case MAYORES:
                return 1; // Prioridad Absoluta Domingo
            case JUVENILES:
                return 2; // Prioridad Absoluta Sábado
            case INFANTILES:
                return 3;
            case FEM_MAYORES:
                return 4; // Se acomoda si hay lugar
            case FEM_MENORES:
                return 5;
            default:
                return 99;
        }
    }

    private Bloque determinarBloque(Categoria cat) {
        // Mapeo simple
        switch (cat) {
            case PRIMERA:
            case RESERVA:
                return Bloque.MAYORES;
            case QUINTA:
            case SEXTA:
            case SEPTIMA:
            case OCTAVA:
                return Bloque.JUVENILES;
            case NOVENA:
            case DECIMA:
            case UNDECIMA:
                return Bloque.INFANTILES;
            // Categorías Femeninas (Asegurate que existan en el Enum Categoria)
            case FEM_PRIMERA:
            case FEM_SUB16:
                return Bloque.FEM_MAYORES;
            case FEM_SUB14:
            case FEM_SUB12:
                return Bloque.FEM_MENORES;
            default:
                return Bloque.MAYORES;
        }
    }

    private boolean resolverBloqueConReinicios(Map<String, List<Equipo>> agrupados, DiaJuego dia, int maxIntentos,
            long timeoutMs) {
        System.out.println("--- INICIANDO CÁLCULO DE " + dia + " ---");

        for (int intento = 1; intento <= maxIntentos; intento++) {
            List<List<Fecha>> torneos = generarMejorCombinacionEsqueletos(agrupados, dia);
            if (torneos.isEmpty())
                return true;

            GeneracionContexto ctx = new GeneracionContexto(timeoutMs, equipoRepository.findAll());
            EstadoFecha estado = inicializarHistorialGlobal(torneos);

            resolverDia(torneos, 0, estado, ctx);

            if (ctx.mejorSolucion != null) {
                System.out.println("✅ EXITO para " + dia + " en intento " + intento);
                ctx.mejorSolucion.forEach(this::persistirFixture);
                return true;
            }
        }
        System.out.println("❌ FRACASO para " + dia + " después de " + maxIntentos + " intentos.");
        return false;
    }

    // --- MOTOR DE RESOLUCIÓN ---

    private boolean resolverDia(List<List<Fecha>> ligas, int fIdX, EstadoFecha estadoGlobal, GeneracionContexto ctx) {
        if (ctx.abortar || System.currentTimeMillis() > ctx.fin) {
            ctx.abortar = true;
            return false;
        }

        boolean torneosCompletados = true;
        for (List<Fecha> liga : ligas) {
            if (fIdX < liga.size())
                torneosCompletados = false;
        }

        if (torneosCompletados) {
            int quiebresActuales = calcularPenalizacion(ligas);
            if (quiebresActuales < ctx.menorQuiebres) {
                ctx.menorQuiebres = quiebresActuales;
                ctx.mejorSolucion = clonarTodasLasFechas(ligas);
            }
            return true;
        }

        EstadoFecha snapshotSeguridad = estadoGlobal.snapshot();
        estadoGlobal.getSedesUsadas().clear();
        estadoGlobal.getClubesLocales().clear(); // Limpieza vital
        estadoGlobal.getVisitantes().clear();

        List<Partido> partidosDelDia = recolectarPartidosMulti(ligas, fIdX);

        backtrackingMulti(ligas, partidosDelDia, 0, fIdX, estadoGlobal, ctx);

        estadoGlobal.restore(snapshotSeguridad);
        return ctx.mejorSolucion != null;
    }

    private boolean backtrackingMulti(List<List<Fecha>> ligas, List<Partido> partidos, int pIdx, int fIdx,
            EstadoFecha estadoFecha, GeneracionContexto ctx) {
        if (ctx.abortar)
            return false;

        if (partidos.size() == pIdx) {
            return resolverDia(ligas, fIdx + 1, estadoFecha, ctx);
        }

        Partido p = partidos.get(pIdx);
        Equipo e1 = p.getLocal();
        Equipo e2 = p.getVisitante();
        if (e1.getId() == -99 || e2.getId() == -99) {
            return backtrackingMulti(ligas, partidos, pIdx + 1, fIdx, estadoFecha, ctx);
        }
        // 🔥 FIX: Blindaje de Tipos (Number -> intValue)
        int e1ClubId = ((Number) e1.getClub().getId()).intValue();
        int e2ClubId = ((Number) e2.getClub().getId()).intValue();

        // INTELIGENCIA DE TIRA
        boolean e1YaAbrioCancha = estadoFecha.getClubesLocales().contains(e1ClubId);
        boolean e2YaAbrioCancha = estadoFecha.getClubesLocales().contains(e2ClubId);

        if (e1YaAbrioCancha) {
            return probarOpcion(e1, e2, p, estadoFecha, ligas, partidos, pIdx, fIdx, ctx);
        }
        if (e2YaAbrioCancha) {
            return probarOpcion(e2, e1, p, estadoFecha, ligas, partidos, pIdx, fIdx, ctx);
        }

        // Decisión por estadística
        boolean e1PrefiereLocal = estadoFecha.getEstado(e1.getId()).getUltimasConsecutivas() < 0;

        if (e1PrefiereLocal) {
            if (probarOpcion(e1, e2, p, estadoFecha, ligas, partidos, pIdx, fIdx, ctx))
                return true;
            if (ctx.abortar)
                return false;
            return probarOpcion(e2, e1, p, estadoFecha, ligas, partidos, pIdx, fIdx, ctx);
        } else {
            if (probarOpcion(e2, e1, p, estadoFecha, ligas, partidos, pIdx, fIdx, ctx))
                return true;
            if (ctx.abortar)
                return false;
            return probarOpcion(e1, e2, p, estadoFecha, ligas, partidos, pIdx, fIdx, ctx);
        }
    }

    private boolean probarOpcion(Equipo loc, Equipo vis, Partido p, EstadoFecha estado, List<List<Fecha>> ligas,
            List<Partido> pts, int pIdx, int fIdx, GeneracionContexto ctx) {
        if (ctx.abortar)
            return false;

        if (esValido(loc, vis, estado, ctx)) {
            EstadoFecha efm = estado.snapshot();
            Partido pm1 = p.memento();

            aplicarLocalia(loc, vis, p, estado);

            boolean exito = backtrackingMulti(ligas, pts, pIdx + 1, fIdx, estado, ctx);

            if (exito)
                return true;

            estado.restore(efm);
            p.restore(pm1);
            if (ctx.abortar)
                return false;
        }
        return false;
    }

    public boolean esValido(Equipo loc, Equipo vis, EstadoFecha estadoFecha, GeneracionContexto ctx) {
        int sedeId = ((Number) loc.getClub().getSede().getId()).intValue();
        int clubLocalId = ((Number) loc.getClub().getId()).intValue();
        if (estadoFecha.getClubesLocales().contains(clubLocalId)) {
            return true;
        }
        if (estadoFecha.getSedesUsadas().containsKey(sedeId)) {
            // EXCEPCIÓN DE ESPEJOS (Double Header permitido)
            // Si la sede está ocupada por mi "Espejo", entonces SÍ puedo jugar (compartimos
            // localía)
            Integer ocupanteId = estadoFecha.getOcupanteSede(sedeId);
            if (ocupanteId != null && esEspejo(loc, ocupanteId, ctx)) {
                return true; // Permitido compartir
            }
            return false;
        }

        // CHEQUEO DE RACHAS
        EstadoEquipo el = estadoFecha.getEstado(loc.getId());
        EstadoEquipo ev = estadoFecha.getEstado(vis.getId());

        return el.getUltimasConsecutivas() < 3 && ev.getUltimasConsecutivas() > -3
                && validarRestriccionesEspecíficas(loc, vis, estadoFecha, ctx);
    }

    private boolean validarRestriccionesEspecíficas(Equipo loc, Equipo vis, EstadoFecha estado,
            GeneracionContexto ctx) {
        // 1. CUPO AYACUCHO (Máx 2 locales)
        if (esDeAyacucho(loc)) {
            long localesAyacucho = estado.getClubesLocales().stream()
                    .map(id -> ctx.equipoCache.get(id))
                    .filter(e -> e != null && esDeAyacucho(e))
                    .count();
            if (localesAyacucho >= 2)
                return false;
        }

        // 2. SEGURIDAD JUARENSE (Si Juarense es Local -> Alumni Visitante)
        if (loc.getNombre().equalsIgnoreCase("Juarense")) {
            // Verificar si Alumni ya es local en esta fecha (lo cual rompería la regla
            // "Alumni Visitante")
            // Buscamos si Alumni está en clubesLocales
            Integer idAlumni = ctx.nombreIdCache.get("ALUMNI");
            if (idAlumni != null && estado.getClubesLocales().contains(idAlumni)) {
                return false;
            }
        }
        // Viceversa: Si Alumni es Local, Juarense debe ser Visitante (no puede ser
        // Local)
        if (loc.getNombre().equalsIgnoreCase("Alumni")) {
            Integer idJuarense = ctx.nombreIdCache.get("JUARENSE");
            if (idJuarense != null && estado.getClubesLocales().contains(idJuarense)) {
                return false;
            }
        }

        // 3. SINCRONIZACIÓN FEMENINA (Mirroring)
        // Si Independiente Fem (Local), Indep Rojo DEBE ser Local (si ya está definido)
        // Si Indep Rojo (Local), Indep Fem DEBE ser Local (si ya está definido)
        if (tieneEspejo(loc, ctx)) {
            Integer espejoId = ctx.espejos.get(loc.getId());
            // Si el espejo YA es local, yo DEBO ser local. (Cumplido implícitamente si
            // llego aquí y soy local)
            // Si el espejo YA jugó pero NO es local (es visitante), yo NO puedo ser local.
            if (estado.jugoComoVisitante(espejoId)) {
                return false;
            }
        }
        return true;
    }

    private boolean tieneEspejo(Equipo e, GeneracionContexto ctx) {
        return ctx.espejos.containsKey(e.getId());
    }

    private boolean esEspejo(Equipo e1, Integer e2Id, GeneracionContexto ctx) {
        Integer espejoDe1 = ctx.espejos.get(e1.getId());
        return espejoDe1 != null && espejoDe1.equals(e2Id);
    }

    private boolean esDeAyacucho(Equipo e) {
        if (e == null)
            return false;
        String nombre = e.getNombre().toUpperCase();
        return nombre.contains("AYACUCHO") || nombre.contains("SARMIENTO") || nombre.contains("ESTRADA")
                || nombre.contains("BOTAFOGO");
    }

    private void aplicarLocalia(Equipo loc, Equipo vis, Partido p, EstadoFecha estado) {
        p.setLocal(loc);
        p.setVisitante(vis);
        p.setCancha(loc.getClub().getSede());

        // 🔥 FIX: Blindaje de Tipos al guardar
        int clubId = ((Number) loc.getClub().getId()).intValue();
        int sedeId = ((Number) loc.getClub().getSede().getId()).intValue();

        estado.getClubesLocales().add(clubId);
        estado.addSedeUsada(sedeId, clubId); // Guardamos quién la usa para chequear espejo
        estado.addVisitante(vis.getId());

        estado.getEstado(loc.getId()).actualizar(true);
        estado.getEstado(vis.getId()).actualizar(false);
    }

    private int calcularChoquesGlobales(List<List<Fecha>> torneos) {
        int totalChoques = 0;
        int maxFechas = torneos.stream().mapToInt(List::size).max().orElse(0);

        for (int fIdx = 0; fIdx < maxFechas; fIdx++) {
            // Mapa de Sede ID -> Club ID Ocupante
            Map<Integer, Integer> sedesUsadas = new HashMap<>();

            for (List<Fecha> torneo : torneos) {
                if (fIdx < torneo.size()) {
                    for (Partido p : torneo.get(fIdx).getPartidos()) {
                        if (p.getLocal().getId() == -99 || p.getVisitante().getId() == -99)
                            continue;

                        int sedeId = ((Number) p.getLocal().getClub().getSede().getId()).intValue();
                        int clubId = ((Number) p.getLocal().getClub().getId()).intValue();

                        Integer ocupanteActual = sedesUsadas.get(sedeId);

                        // Si la sede está ocupada por un CLUB DISTINTO, entonces sí es un choque real
                        if (ocupanteActual != null && !ocupanteActual.equals(clubId)) {
                            totalChoques++;
                        } else {
                            // Si está libre o la está usando el mismo club (Tira), la registramos
                            sedesUsadas.put(sedeId, clubId);
                        }
                    }
                }
            }
        }
        return totalChoques;
    }

    public List<Fecha> generarEsqueletoBerger(List<Equipo> equipos, Bloque bloque, Liga liga) {
        List<Equipo> lista = new ArrayList<>(equipos);

        if (lista.size() % 2 != 0) {
            Equipo libre = new Equipo();
            // ID negativo para no chocar con IDs reales
            libre.setId(-99);
            lista.add(libre);
        }

        int n = lista.size();
        int numFechas = n - 1;
        List<Fecha> torneo = new ArrayList<>();

        for (int fechaIdx = 0; fechaIdx < numFechas; fechaIdx++) {
            Fecha f = new Fecha(fechaIdx + 1);
            f.setBloque(bloque);
            f.setLiga(liga);

            for (int i = 0; i < n / 2; i++) {
                Equipo local, visitante;
                int a = i;
                int b = n - 1 - i;

                if (i == 0) {
                    if (fechaIdx % 2 == 0) {
                        local = lista.get(b);
                        visitante = lista.get(a);
                    } else {
                        local = lista.get(a);
                        visitante = lista.get(b);
                    }
                } else {
                    if ((fechaIdx + i) % 2 == 0) {
                        local = lista.get(b);
                        visitante = lista.get(a);
                    } else {
                        local = lista.get(a);
                        visitante = lista.get(b);
                    }
                }

                if (local.getId() != -99 && visitante.getId() != -99) {
                    agregarPartido(f, local, visitante);
                }
            }
            rotarBerger(lista);
            torneo.add(f);
        }
        return torneo;
    }

    public List<Fecha> generarEsqueletoBergerIdaVuelta(List<Equipo> equipos, Bloque bloque, Liga liga) {

        List<Fecha> ida = generarEsqueletoBerger(equipos, bloque, liga);

        int offset = ida.size();
        List<Fecha> vuelta = new ArrayList<>();

        for (Fecha fIda : ida) {

            Fecha fVuelta = new Fecha(fIda.getNroFecha() + offset);
            fVuelta.setBloque(bloque);
            fVuelta.setLiga(liga);

            for (Partido pIda : fIda.getPartidos()) {

                Equipo nuevoLocal = pIda.getVisitante();
                Equipo nuevoVisitante = pIda.getLocal();

                if (nuevoLocal.getId() != -99 && nuevoVisitante.getId() != -99) {
                    agregarPartido(fVuelta, nuevoLocal, nuevoVisitante);
                }
            }

            vuelta.add(fVuelta);
        }

        List<Fecha> torneoCompleto = new ArrayList<>();
        torneoCompleto.addAll(ida);
        torneoCompleto.addAll(vuelta);

        return torneoCompleto;
    }

    private void rotarBerger(List<Equipo> lista) {
        int n = lista.size();
        Equipo ultimoMovible = lista.remove(n - 2);
        lista.add(0, ultimoMovible);
    }

    private List<List<Fecha>> generarMejorCombinacionEsqueletos(Map<String, List<Equipo>> agrupados,
            DiaJuego diaEsperado) {
        List<List<Equipo>> torneosEquipos = new ArrayList<>();
        List<Bloque> bloques = new ArrayList<>();
        List<Liga> ligas = new ArrayList<>();

        // 1. Separar datos
        for (Map.Entry<String, List<Equipo>> entry : agrupados.entrySet()) {
            List<Equipo> equipos = entry.getValue();
            if (!equipos.isEmpty() && equipos.get(0).getDiaDeJuego() == diaEsperado) {
                torneosEquipos.add(new ArrayList<>(equipos));
                String[] partes = entry.getKey().split("-");
                bloques.add(Bloque.valueOf(partes[0]));
                ligas.add(Liga.valueOf(partes[1]));
            }
        }

        if (torneosEquipos.isEmpty())
            return new ArrayList<>();

        // 2. Estado inicial (Aleatorio)
        for (List<Equipo> lista : torneosEquipos) {
            Collections.shuffle(lista);
        }

        List<List<Fecha>> mejorSolucion = generarTodasLasFechas(torneosEquipos, bloques, ligas);
        int minChoques = calcularChoquesGlobales(mejorSolucion);

        System.out.println("Arrancando optimizador con " + minChoques + " choques base...");

        // 3. Algoritmo de Escalada (Hill Climbing) - Permutación Inteligente
        Random rand = new Random();
        int iteracionesSinMejora = 0;
        int MAX_ITERACIONES = 3000;

        for (int i = 0; i < MAX_ITERACIONES; i++) {
            if (minChoques == 0)
                break; // Fixture base perfecto alcanzado

            // Elegir un torneo al azar para mutar
            int tIdx = rand.nextInt(torneosEquipos.size());
            List<Equipo> torneoMutado = torneosEquipos.get(tIdx);

            if (torneoMutado.size() < 2)
                continue;

            // Elegir dos posiciones al azar y permutar sus equipos
            int idx1 = rand.nextInt(torneoMutado.size());
            int idx2 = rand.nextInt(torneoMutado.size());
            Collections.swap(torneoMutado, idx1, idx2);

            // Generar nuevo esqueleto evaluarlo
            List<List<Fecha>> solucionPrueba = generarTodasLasFechas(torneosEquipos, bloques, ligas);
            int choquesPrueba = calcularChoquesGlobales(solucionPrueba);

            if (choquesPrueba < minChoques) {
                // Mejora encontrada: Aceptamos el cambio
                minChoques = choquesPrueba;
                mejorSolucion = solucionPrueba;
                iteracionesSinMejora = 0;
            } else {
                // Empeoró o quedó igual: Deshacemos el cambio
                Collections.swap(torneoMutado, idx1, idx2);
                iteracionesSinMejora++;
            }

            // Si se estanca en un mínimo local, forzamos un sacudón parcial
            if (iteracionesSinMejora > 1500) {
                Collections.shuffle(torneoMutado);
                iteracionesSinMejora = 0;
            }
        }

        System.out.println("Esqueleto final pulido entregado al Backtracker con " + minChoques + " choques base.");
        return mejorSolucion;
    }

    private List<List<Fecha>> generarTodasLasFechas(List<List<Equipo>> torneosEquipos, List<Bloque> bloques,
            List<Liga> ligas) {
        List<List<Fecha>> todasLasFechas = new ArrayList<>();
        for (int i = 0; i < torneosEquipos.size(); i++) {
            // Como le quitamos el shuffle interno a generarEsqueletoBerger,
            // ahora respeta el orden estricto que le pasa el Hill Climbing.
            todasLasFechas.add(generarEsqueletoBergerIdaVuelta(torneosEquipos.get(i), bloques.get(i), ligas.get(i)));
        }
        return todasLasFechas;
    }

    private void agregarPartido(Fecha f, Equipo loc, Equipo vis) {
        Partido p = new Partido();
        p.setLocal(loc);
        p.setVisitante(vis);
        p.setFecha(f);
        f.addPartido(p);
    }

    private Map<String, List<Equipo>> agruparEquiposPorTorneo(List<Equipo> todos) {
        return todos.stream().collect(Collectors.groupingBy(
                e -> e.getBloque().name() + "-" + e.getDivisionMayor().name()));
    }

    private EstadoFecha inicializarHistorialGlobal(List<List<Fecha>> torneos) {
        EstadoFecha estado = new EstadoFecha();
        for (List<Fecha> torneo : torneos) {
            for (Fecha f : torneo) {
                for (Partido p : f.getPartidos()) {
                    estado.getEstadoPorEquipo().putIfAbsent(p.getLocal().getId(), new EstadoEquipo());
                    estado.getEstadoPorEquipo().putIfAbsent(p.getVisitante().getId(), new EstadoEquipo());
                }
            }
        }
        return estado;
    }

    private int calcularPenalizacion(List<List<Fecha>> ligas) {

        int penalizacion = 0;

        // idEquipo -> racha actual (positivo local, negativo visitante)
        Map<Integer, Integer> rachas = new HashMap<>();

        for (List<Fecha> liga : ligas) {
            for (Fecha f : liga) {
                for (Partido p : f.getPartidos()) {

                    if (p.getLocal().getId() == -99 || p.getVisitante().getId() == -99)
                        continue;

                    int locId = p.getLocal().getId();
                    int visId = p.getVisitante().getId();

                    // --- LOCAL ---
                    int rachaLocal = rachas.getOrDefault(locId, 0);

                    if (rachaLocal > 0)
                        rachaLocal++;
                    else
                        rachaLocal = 1;

                    rachas.put(locId, rachaLocal);

                    if (Math.abs(rachaLocal) == 2)
                        penalizacion += 2; // leve

                    if (Math.abs(rachaLocal) >= 3)
                        penalizacion += 1000; // casi prohibido

                    // --- VISITANTE ---
                    int rachaVis = rachas.getOrDefault(visId, 0);

                    if (rachaVis < 0)
                        rachaVis--;
                    else
                        rachaVis = -1;

                    rachas.put(visId, rachaVis);

                    if (Math.abs(rachaVis) == 2)
                        penalizacion += 2;

                    if (Math.abs(rachaVis) >= 3)
                        penalizacion += 1000;
                }
            }
        }

        return penalizacion;
    }

    private List<List<Fecha>> clonarTodasLasFechas(List<List<Fecha>> originales) {
        List<List<Fecha>> copiasMulti = new ArrayList<>();
        for (List<Fecha> ligaOriginal : originales) {
            List<Fecha> copias = new ArrayList<>();
            for (Fecha f : ligaOriginal) {
                Fecha copiaF = new Fecha(f.getNroFecha());
                copiaF.setLiga(f.getLiga());
                copiaF.setBloque(f.getBloque());
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
            copiasMulti.add(copias);
        }
        return copiasMulti;
    }

    private void persistirFixture(List<Fecha> fechas) {
        fechaRepository.saveAll(fechas);
    }

    public List<FechaDTO> obtenerFixturePorCategoria(Liga liga, Categoria categoriaSolicitada) {

        Bloque bloque = determinarBloque(categoriaSolicitada);
        List<Fecha> fechasMaestras = fechaRepository.findAllByLigaAndBloqueOrderByNroFechaAsc(liga, bloque);

        List<FechaDTO> fixtureProyectado = new ArrayList<>();

        for (Fecha f : fechasMaestras) {

            FechaDTO fDto = new FechaDTO(f.getNroFecha(), f.getLiga().name());

            for (Partido p : f.getPartidos()) {

                Equipo local = p.getLocal();
                Equipo visitante = p.getVisitante();

                boolean localTiene = local.getCategoriasHabilitadas() != null
                        && local.getCategoriasHabilitadas().contains(categoriaSolicitada)
                        && local.getDivisionMayor().equals(liga);

                boolean visitanteTiene = visitante.getCategoriasHabilitadas() != null
                        && visitante.getCategoriasHabilitadas().contains(categoriaSolicitada)
                        && visitante.getDivisionMayor().equals(liga);

                // --- CASO 1: Ambos tienen la categoría ---
                if (localTiene && visitanteTiene) {
                    String nombreCancha = p.getCancha() != null ? p.getCancha().getName() : "A DEFINIR";

                    fDto.addPartido(new PartidoDTO(
                            local.getNombre(),
                            visitante.getNombre(),
                            nombreCancha));
                }

                // --- CASO 2: Visitante no tiene la categoría -> Local queda libre ---
                else if (localTiene && !visitanteTiene) {
                    fDto.addPartido(new PartidoDTO(
                            local.getNombre(),
                            "LIBRE",
                            "DESCANSA"));
                }

                // --- CASO 3: Local no tiene la categoría -> Visitante queda libre ---
                else if (!localTiene && visitanteTiene) {
                    fDto.addPartido(new PartidoDTO(
                            "LIBRE",
                            visitante.getNombre(),
                            "DESCANSA"));
                }
            }

            if (!fDto.getPartidos().isEmpty()) {
                fixtureProyectado.add(fDto);
            }
        }

        return fixtureProyectado;
    }

    // Contexto
    private class GeneracionContexto {
        List<List<Fecha>> mejorSolucion;
        int menorQuiebres = Integer.MAX_VALUE;
        long fin;
        boolean abortar = false;

        // Cache optimization
        Map<Integer, Equipo> equipoCache = new HashMap<>();
        Map<String, Integer> nombreIdCache = new HashMap<>();
        Map<Integer, Integer> espejos = new HashMap<>(); // ID -> ID

        public GeneracionContexto(long duracionMs, List<Equipo> todosLosEquipos) {
            this.fin = System.currentTimeMillis() + duracionMs;
            for (Equipo e : todosLosEquipos) {
                equipoCache.put(e.getId(), e);
                nombreIdCache.put(e.getNombre().toUpperCase(), e.getId());
            }
            // Configurar Espejos
            configurarEspejo("INDEPENDIENTE FEMENINO", "INDEPENDIENTE (ROJO)");
            configurarEspejo("FERROCARRIL SUD FEMENINO", "FERROCARRIL SUD (AZUL)");
        }

        private void configurarEspejo(String n1, String n2) {
            Integer id1 = nombreIdCache.get(n1);
            Integer id2 = nombreIdCache.get(n2);
            if (id1 != null && id2 != null) {
                espejos.put(id1, id2);
                espejos.put(id2, id1);
            }
        }
    }

    // Mover equipos() aquí para evitar problemas de parsing al final del archivo
    public List<EquipoDTO> equipos() {
        return equipoRepository.findAll().stream().map(EquipoDTO::toDTO).toList();
    }
}
