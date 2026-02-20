package com.fixture.fixturesservice.services;

import com.fixture.fixturesservice.DTOS.ResponseDTO;
import com.fixture.fixturesservice.entities.Equipo;
import com.fixture.fixturesservice.entities.Fecha;
import com.fixture.fixturesservice.entities.Partido;
import com.fixture.fixturesservice.enums.Bloque;
import com.fixture.fixturesservice.enums.DiaJuego;
import com.fixture.fixturesservice.enums.Liga;
import com.fixture.fixturesservice.repositories.EquipoRepository;
import com.fixture.fixturesservice.repositories.FechaRepository;
import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrToolsFixtureService {

    @Autowired
    private EquipoRepository equipoRepository;

    @Autowired
    private FechaRepository fechaRepository;

    @PostConstruct
    public void init() {
        Loader.loadNativeLibraries();
    }

    public ResponseDTO generarConOrTools() {
        List<Equipo> todos = equipoRepository.findAll();
        Map<String, List<Equipo>> agrupados = agruparEquiposPorTorneo(todos);

        // Limpiar
        fechaRepository.deleteAll();

        boolean exitoSabado = resolverBloque(agrupados, DiaJuego.SABADO);
        boolean exitoDomingo = resolverBloque(agrupados, DiaJuego.DOMINGO);

        if (exitoSabado && exitoDomingo) {
            return new ResponseDTO("Fixture Generado con OR-TOOLS (CP-SAT) exitosamente.", true);
        } else {
            return new ResponseDTO("No se pudo encontrar solución factible para todas las restricciones.", false);
        }
    }

    private boolean resolverBloque(Map<String, List<Equipo>> agrupados, DiaJuego dia) {
        // Filtrar torneos del día
        List<List<Equipo>> torneosDelDia = new ArrayList<>();
        List<Bloque> bloques = new ArrayList<>();
        List<Liga> ligas = new ArrayList<>();

        for (Map.Entry<String, List<Equipo>> entry : agrupados.entrySet()) {
            if (!entry.getValue().isEmpty() && entry.getValue().get(0).getDiaDeJuego() == dia) {
                torneosDelDia.add(entry.getValue());
                String[] parts = entry.getKey().split("-");
                bloques.add(Bloque.valueOf(parts[0]));
                ligas.add(Liga.valueOf(parts[1]));
            }
        }

        if (torneosDelDia.isEmpty())
            return true;

        try {
            return resolverModeloCP(torneosDelDia, bloques, ligas);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private Map<String, List<Equipo>> agruparEquiposPorTorneo(List<Equipo> todos) {
        Map<String, List<Equipo>> map = new HashMap<>();
        for (Equipo e : todos) {
            String key = e.getBloque().name() + "-" + e.getLiga().name();
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        return map;
    }

    private boolean resolverModeloCP(List<List<Equipo>> torneos, List<Bloque> bloques, List<Liga> ligas) {
        CpModel model = new CpModel();
        int maxFechas = 0;

        // Mapeos Globales para Restricciones Cruzadas
        Map<Integer, Literal[]> mapaLocaliaVars = new HashMap<>();

        // Estructuras de Variables
        // vars[t][f][i][j] -> Partido en torneo t, fecha f, local i, visitante j
        List<Literal[][][]> varsPorTorneo = new ArrayList<>();
        List<Literal[][]> esLocalPorTorneo = new ArrayList<>(); // [t][f][i]

        // [t][f][i] -> Si equipo i juega de visitante en fecha f

        for (int t = 0; t < torneos.size(); t++) {
            List<Equipo> equipos = torneos.get(t);
            int n = equipos.size();
            boolean impar = n % 2 != 0;
            int nReal = impar ? n + 1 : n; // Para Berger
            int numFechas = (nReal - 1) * 2; // Ida y Vuelta
            maxFechas = Math.max(maxFechas, numFechas);

            Literal[][][] partidos = new Literal[numFechas][n][n];
            Literal[][] esLocal = new Literal[numFechas][n];
            Literal[][] esVisitante = new Literal[numFechas][n]; // Auxiliar útil

            // Crear Variables
            for (int f = 0; f < numFechas; f++) {
                for (int i = 0; i < n; i++) {
                    esLocal[f][i] = model.newBoolVar("loc_" + t + "_" + f + "_" + i);
                    esVisitante[f][i] = model.newBoolVar("vis_" + t + "_" + f + "_" + i);

                    for (int j = 0; j < n; j++) {
                        if (i == j)
                            continue;
                        partidos[f][i][j] = model.newBoolVar("p_" + t + "_" + f + "_" + i + "_" + j);
                    }
                }
            }

            varsPorTorneo.add(partidos);
            esLocalPorTorneo.add(esLocal);

            // Llenar mapaLocaliaVars
            for (int i = 0; i < n; i++) {
                Literal[] vectorLocalia = new Literal[numFechas];
                for (int f = 0; f < numFechas; f++)
                    vectorLocalia[f] = esLocal[f][i];
                mapaLocaliaVars.put(equipos.get(i).getId(), vectorLocalia);
            }

            // Restricciones Básicas de Torneo (Round Robin)
            aplicarRestriccionesTorneo(model, n, numFechas, partidos, esLocal, esVisitante);

            // Restricciones de Secuencia (Max 2 Local/Visitante)
            aplicarRestriccionesSecuencia(model, n, numFechas, esLocal, esVisitante);
        }

        // --- RESTRICCIONES CRUZADAS (Cross-Tournament) ---
        // 1. Stadium Sharing (Cruzados)
        aplicarStadiumSharing(model, torneos, mapaLocaliaVars, maxFechas);

        // 2. Cupo Ayacucho
        aplicarCupoAyacucho(model, torneos, mapaLocaliaVars, maxFechas);

        // 3. Juarense - Alumni
        Integer idJuarense = buscarIdPorNombre("JUARENSE", torneos);
        Integer idAlumni = buscarIdPorNombre("ALUMNI", torneos);

        if (idJuarense != null && idAlumni != null) {
            aplicarJuarenseAlumniConstraint(model, mapaLocaliaVars, idJuarense, idAlumni);
        }

        // 4. Mirroring (Sincronización de Localías)
        aplicarMirroring(model, torneos, mapaLocaliaVars, maxFechas);

        // Solver
        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(30.0);

        CpSolverStatus status = solver.solve(model);

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            System.out.println("SOLUCIÓN ENCONTRADA: " + status);
            guardarSolucion(solver, varsPorTorneo, torneos, bloques, ligas);
            return true;
        } else {
            System.out.println("NO SE ENCONTRÓ SOLUCIÓN: " + status);
            return false;
        }
    }

    private void aplicarRestriccionesTorneo(CpModel model, int n, int numFechas, Literal[][][] p, Literal[][] esLocal,
            Literal[][] esVisitante) {
        // 1. Un equipo juega máx 1 vez por fecha
        for (int f = 0; f < numFechas; f++) {
            for (int i = 0; i < n; i++) {
                List<Literal> partidosDelEquipo = new ArrayList<>();
                // Como local
                for (int j = 0; j < n; j++) {
                    if (i != j)
                        partidosDelEquipo.add(p[f][i][j]);
                }
                // Como visit
                for (int j = 0; j < n; j++) {
                    if (i != j)
                        partidosDelEquipo.add(p[f][j][i]);
                }
                model.addAtMostOne(partidosDelEquipo);

                // Vincular aux vars
                // esLocal[f][i] <=> sum(p[f][i][j]) == 1
                List<Literal> soyLocal = new ArrayList<>();
                for (int j = 0; j < n; j++)
                    if (i != j)
                        soyLocal.add(p[f][i][j]);
                model.addEquality(LinearExpr.sum(soyLocal.toArray(new Literal[0])), esLocal[f][i]);

                // esVisitante[f][i] <=> sum(p[f][j][i]) == 1
                List<Literal> soyVis = new ArrayList<>();
                for (int j = 0; j < n; j++)
                    if (i != j)
                        soyVis.add(p[f][j][i]);
                model.addEquality(LinearExpr.sum(soyVis.toArray(new Literal[0])), esVisitante[f][i]);
            }
        }

        // 2. Todos contra todos (Ida y Vuelta)
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j)
                    continue;
                // Juegan exactamente 1 vez como i local j visitante en TODO el torneo
                List<Literal> partidos = new ArrayList<>();
                for (int f = 0; f < numFechas; f++) {
                    partidos.add(p[f][i][j]);
                }
                model.addEquality(LinearExpr.sum(partidos.toArray(new Literal[0])), 1);
            }
        }
    }

    private void aplicarRestriccionesSecuencia(CpModel model, int n, int numFechas, Literal[][] esLocal,
            Literal[][] esVisitante) {
        // Max 3 seguidas local (implica que en ventana de 4 no puede haber 4)
        for (int i = 0; i < n; i++) {
            for (int f = 0; f < numFechas - 3; f++) {
                // local[f] + ... + local[f+3] <= 3
                model.addLessOrEqual(
                        LinearExpr.sum(new Literal[] { esLocal[f][i], esLocal[f + 1][i], esLocal[f + 2][i],
                                esLocal[f + 3][i] }),
                        3);
                // vis[f] + ... + vis[f+3] <= 3
                model.addLessOrEqual(LinearExpr
                        .sum(new Literal[] { esVisitante[f][i], esVisitante[f + 1][i], esVisitante[f + 2][i],
                                esVisitante[f + 3][i] }),
                        3);
            }
        }
    }

    // --- HELPERS DE NEGOCIO ---

    private void aplicarStadiumSharing(CpModel model, List<List<Equipo>> torneos, Map<Integer, Literal[]> mapaLocalia,
            int maxFechas) {
        List<Equipo> flatList = torneos.stream().flatMap(List::stream).toList();
        int penalty = 1000; // Penalización alta por choque de estadio

        for (int i = 0; i < flatList.size(); i++) {
            for (int j = i + 1; j < flatList.size(); j++) {
                Equipo e1 = flatList.get(i);
                Equipo e2 = flatList.get(j);

                if (compartenSede(e1, e2) && !sonEspejos(e1, e2)) {
                    Literal[] loc1 = mapaLocalia.get(e1.getId());
                    Literal[] loc2 = mapaLocalia.get(e2.getId());

                    if (loc1 == null || loc2 == null)
                        continue;

                    int len = Math.min(loc1.length, loc2.length);
                    for (int f = 0; f < len; f++) {
                        // Hard Constraint Original: loc1[f] + loc2[f] <= 1
                        // Soft Constraint: Penalizar si suman > 1
                        // Var auxiliar: choque = (loc1 && loc2)
                        BoolVar choque = model.newBoolVar("choque_estadio_" + e1.getId() + "_" + e2.getId() + "_f" + f);
                        // model.addHtmlString(choque, "Choque de Estadio"); // Metadata no disponible
                        // en Java API

                        // choque => loc1 y choque => loc2
                        // loc1 + loc2 - 1 <= choque (Si ambos son 1, choque DEBE ser 1)
                        // Logica booleana: AND
                        model.addBoolAnd(List.of(loc1[f], loc2[f])).onlyEnforceIf(choque);
                        model.addBoolOr(List.of(loc1[f].not(), loc2[f].not())).onlyEnforceIf(choque.not());

                        // Minimizar choques
                        model.minimize(LinearExpr.term(choque, penalty));
                    }
                }
            }
        }
    }

    private void aplicarCupoAyacucho(CpModel model, List<List<Equipo>> torneos, Map<Integer, Literal[]> mapaLocalia,
            int maxFechas) {
        List<Literal[]> varsAyacucho = new ArrayList<>();
        List<Equipo> flatList = torneos.stream().flatMap(List::stream).toList();
        for (Equipo e : flatList) {
            if (esDeAyacucho(e)) {
                Literal[] v = mapaLocalia.get(e.getId());
                if (v != null)
                    varsAyacucho.add(v);
            }
        }

        if (varsAyacucho.size() < 3)
            return;

        int penalty = 500; // Penalización media

        for (int f = 0; f < maxFechas; f++) {
            List<Literal> localesEnFecha = new ArrayList<>();
            for (Literal[] v : varsAyacucho) {
                if (f < v.length)
                    localesEnFecha.add(v[f]);
            }
            // Hard Constraint Original: sum <= 2
            // Soft Constraint: Exceso
            // variable exceso >= sum - 2

            // Crear variable para contar cuántos locales hay: count
            // LinearExpr sumLocales = LinearExpr.sum(localesEnFecha...);
            // Esto es complejo combinando BoolVars.
            // MEJOR: Relax simple. Si son > 2, penalizar CADA UNO extra.
            // Pero, queremos permitir que haya 2 gratis.

            // IntVar count = model.newIntVar(0, varsAyacucho.size(), "count_ayacucho_f" +
            // f);
            // model.addEquality(count, LinearExpr.sum(localesEnFecha.toArray(new
            // Literal[0])));

            // IntVar exceso = model.newIntVar(0, varsAyacucho.size(), "exceso_ayacucho_f" +
            // f);
            // model.addMaxEquality(exceso, List.of(model.newConstant(0),
            // LinearExpr.newBuilder().addTerm(count, 1).addTerm(model.newConstant(-2),
            // 1).build()));
            // API LinearExpr no es tan directa para aritmetica.

            // Simplificación v2:
            // "Soft Upper Bound"
            // Podemos usar una variable de holgura 'slack' positiva.
            // sum(locales) <= 2 + slack
            // Minimize(slack * penalty)

            IntVar slack = model.newIntVar(0, varsAyacucho.size(), "slack_ayacucho_f" + f);
            model.addLessOrEqual(
                    LinearExpr.newBuilder().addSum(localesEnFecha.toArray(new Literal[0])).addTerm(slack, -1),
                    2);
            model.minimize(LinearExpr.term(slack, penalty));
        }
    }

    private void aplicarMirroring(CpModel model, List<List<Equipo>> torneos, Map<Integer, Literal[]> mapaLocalia,
            int maxFechas) {
        // Pares definidos hardcoded por ahora
        Map<String, String> paresEspejo = new HashMap<>();
        paresEspejo.put("INDEPENDIENTE FEMENINO", "INDEPENDIENTE (ROJO)");
        paresEspejo.put("FERROCARRIL SUD FEMENINO", "FERROCARRIL SUD (AZUL)");

        for (Map.Entry<String, String> entry : paresEspejo.entrySet()) {
            Integer id1 = buscarIdPorNombre(entry.getKey(), torneos);
            Integer id2 = buscarIdPorNombre(entry.getValue(), torneos);

            if (id1 != null && id2 != null) {
                Literal[] vars1 = mapaLocalia.get(id1);
                Literal[] vars2 = mapaLocalia.get(id2);

                if (vars1 != null && vars2 != null) {
                    int len = Math.min(vars1.length, vars2.length);
                    for (int f = 0; f < len; f++) {
                        // loc1[f] == loc2[f]
                        model.addEquality(vars1[f], vars2[f]);
                    }
                }
            }
        }
    }

    private void aplicarJuarenseAlumniConstraint(CpModel model, Map<Integer, Literal[]> mapaLocalia, Integer idJuarense,
            Integer idAlumni) {
        Literal[] varsJ = mapaLocalia.get(idJuarense);
        Literal[] varsA = mapaLocalia.get(idAlumni);

        if (varsJ == null || varsA == null)
            return;

        int penalty = 2000; // Penalización muy alta, prioridad

        int len = Math.min(varsJ.length, varsA.length);
        for (int f = 0; f < len; f++) {
            // Soft Constraint
            // slack >= (J + A) - 1
            // J + A <= 1 + slack
            IntVar slack = model.newIntVar(0, 2, "slack_ja_f" + f);
            model.addLessOrEqual(
                    LinearExpr.newBuilder().addTerm(varsJ[f], 1).addTerm(varsA[f], 1).addTerm(slack, -1),
                    1);
            model.minimize(LinearExpr.term(slack, penalty));
        }
    }

    // Método auxiliar para buscar ID por nombre en los torneos cargados
    private Integer buscarIdPorNombre(String nombreParcial, List<List<Equipo>> torneos) {
        for (List<Equipo> lista : torneos) {
            for (Equipo e : lista) {
                if (e.getNombre().toUpperCase().contains(nombreParcial.toUpperCase())) {
                    return e.getId();
                }
            }
        }
        return null;
    }

    private boolean compartenSede(Equipo e1, Equipo e2) {
        if (e1.getClub() == null || e2.getClub() == null)
            return false;
        return e1.getClub().getSede().getId() == e2.getClub().getSede().getId();
    }

    private boolean sonEspejos(Equipo e1, Equipo e2) {
        // Implementar lógica de nombres (Indep Rojo/Fem)
        String n1 = e1.getNombre().toUpperCase();
        String n2 = e2.getNombre().toUpperCase();
        if (n1.contains("INDEPENDIENTE") && n2.contains("INDEPENDIENTE"))
            return true;
        if (n1.contains("FERRO") && n2.contains("FERRO"))
            return true;
        return false;
    }

    private boolean esDeAyacucho(Equipo e) {
        String n = e.getNombre().toUpperCase();
        return n.contains("AYACUCHO") || n.contains("SARMIENTO") || n.contains("ESTRADA") || n.contains("BOTAFOGO");
    }

    private void guardarSolucion(CpSolver solver, List<Literal[][][]> varsPorTorneo, List<List<Equipo>> torneos,
            List<Bloque> bloques, List<Liga> ligas) {
        // Reconstruir objetos Fecha y Partido
        List<Fecha> fechasTotal = new ArrayList<>();

        for (int t = 0; t < torneos.size(); t++) {
            List<Equipo> equipos = torneos.get(t);
            Literal[][][] pVars = varsPorTorneo.get(t);
            int n = equipos.size();
            int numFechas = pVars.length;

            for (int f = 0; f < numFechas; f++) {
                Fecha fecha = new Fecha();
                fecha.setNroFecha(f + 1);
                fecha.setBloque(bloques.get(t));
                fecha.setLiga(ligas.get(t));
                fecha.setPartidos(new ArrayList<>());

                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        if (i == j)
                            continue;
                        if (solver.booleanValue(pVars[f][i][j])) {
                            Partido p = new Partido();
                            p.setLocal(equipos.get(i));
                            p.setVisitante(equipos.get(j));
                            p.setFecha(fecha);
                            p.setCancha(equipos.get(i).getClub().getSede());
                            // Check for duplicates in list? No, new Partido per loop
                            fecha.getPartidos().add(p);
                        }
                    }
                }
                fechasTotal.add(fecha);
            }
        }
        fechaRepository.saveAll(fechasTotal);
    }
}
