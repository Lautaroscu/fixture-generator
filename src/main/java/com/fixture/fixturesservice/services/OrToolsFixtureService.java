package com.fixture.fixturesservice.services;

import com.fixture.fixturesservice.DTOS.ResponseDTO;
import com.fixture.fixturesservice.entities.Equipo;
import com.fixture.fixturesservice.entities.Fecha;
import com.fixture.fixturesservice.entities.Partido;
import com.fixture.fixturesservice.enums.Bloque;
import com.fixture.fixturesservice.enums.DiaJuego;
import com.fixture.fixturesservice.enums.Liga;
import com.fixture.fixturesservice.enums.Categoria;
import com.fixture.fixturesservice.repositories.EquipoRepository;
import com.fixture.fixturesservice.repositories.FechaRepository;
import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fixture.fixturesservice.DTOS.JobStatusDTO;
import org.springframework.scheduling.annotation.Async;
import java.util.concurrent.ConcurrentHashMap;
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
// Mapa en memoria para rastrear el estado de los procesos
    private final Map<String, JobStatusDTO> jobTracker = new ConcurrentHashMap<>();

    // Método para consultar el estado desde el Controller
    public JobStatusDTO obtenerEstadoTrabajo(String jobId) {
        return jobTracker.getOrDefault(jobId, new JobStatusDTO("NOT_FOUND", "El trabajo no existe."));
    }
    // El nuevo punto de entrada asíncrono
    @Async
    public void generarFixtureAsync(String jobId) {
        try {
            jobTracker.put(jobId, new JobStatusDTO("PROCESSING", "El motor OR-Tools está calculando el fixture..."));
            
            // Llamamos a tu método pesado original
            ResponseDTO resultado = generarConOrTools(); 
            
            if (resultado.isSuccess()) {
                jobTracker.put(jobId, new JobStatusDTO("COMPLETED", resultado.getMessage()));
            } else {
                jobTracker.put(jobId, new JobStatusDTO("ERROR", resultado.getMessage()));
            }
        } catch (Exception e) {
            jobTracker.put(jobId, new JobStatusDTO("ERROR", "Fallo crítico en el hilo secundario: " + e.getMessage()));
        }
    }
    @Transactional
    public ResponseDTO generarConOrTools() {
        List<Equipo> todos = equipoRepository.findAll();
        if (todos.isEmpty()) {
            return new ResponseDTO("No hay equipos cargados.", false);
        }

        fechaRepository.deleteAll();
        Map<String, List<Equipo>> torneosMap = agruparEquiposPorTorneo(todos);

        List<List<Equipo>> torneosList = new ArrayList<>(torneosMap.values());
        List<String> claves = new ArrayList<>(torneosMap.keySet());

        try {
            boolean exito = resolverModeloUnificado(torneosList, claves);
            if (exito) {
                return new ResponseDTO("Fixture Generado con OR-TOOLS (CP-SAT) exitosamente.", true);
            } else {
                return new ResponseDTO(
                        "INFEASIBLE: El solver no pudo encontrar una solución con las restricciones actuales.", false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseDTO("Error nativo durante la generación: " + e.getMessage(), false);
        }
    }

    private boolean resolverModeloUnificado(List<List<Equipo>> torneos, List<String> claves) {
        CpModel model = new CpModel();
        int maxFechasGlobal = 0;

        List<Literal[][][]> varsPorTorneo = new ArrayList<>();
        List<Literal[][]> esLocalPorTorneo = new ArrayList<>();
        Map<Integer, Literal[]> mapaLocaliaVars = new HashMap<>();

        // 1. Instanciar variables
        for (int t = 0; t < torneos.size(); t++) {
            List<Equipo> equipos = torneos.get(t);
            int n = equipos.size();
            boolean impar = n % 2 != 0;
            int nReal = impar ? n + 1 : n;
            int numFechas = (nReal - 1) * 2;
            maxFechasGlobal = Math.max(maxFechasGlobal, numFechas);

            Literal[][][] partidos = new Literal[numFechas][n][n];
            Literal[][] esLocal = new Literal[numFechas][n];

            for (int f = 0; f < numFechas; f++) {
                for (int i = 0; i < n; i++) {
                    esLocal[f][i] = model.newBoolVar("loc_" + t + "_" + f + "_" + i);
                    for (int j = 0; j < n; j++) {
                        if (i == j)
                            continue;
                        partidos[f][i][j] = model.newBoolVar("p_" + t + "_" + f + "_" + i + "_" + j);
                    }
                }
            }
            varsPorTorneo.add(partidos);
            esLocalPorTorneo.add(esLocal);

            for (int i = 0; i < n; i++) {
                mapaLocaliaVars.put(equipos.get(i).getId(), esLocal[i]);
            }

            aplicarRestriccionesTorneo(model, n, numFechas, partidos, esLocal);
            aplicarRestriccionesSecuencia(model, n, numFechas, esLocal);
        }

        // 2. Aplicar Restricciones de Negocio Globales (Comentadas por pedido)
        aplicarCapacidadEstadios(model, torneos, varsPorTorneo);
        aplicarStadiumSharing(model, torneos, mapaLocaliaVars);
        aplicarCupoAyacucho(model, torneos, mapaLocaliaVars, maxFechasGlobal);
        aplicarJuarenseAlumniConstraint(model, torneos, mapaLocaliaVars);
        aplicarSincronizaciones(model, torneos, mapaLocaliaVars);
        aplicarLogisticaFlorida(model, torneos, mapaLocaliaVars);

        // 3. Minimizar Breaks (Optimización)
        List<IntVar> breaks = new ArrayList<>();
        for (int t = 0; t < torneos.size(); t++) {
            Literal[][] esLocal = esLocalPorTorneo.get(t);
            int n = torneos.get(t).size();
            int numFechas = esLocal.length;
            for (int i = 0; i < n; i++) {
                for (int f = 0; f < numFechas - 1; f++) {
                    BoolVar breakVar = model.newBoolVar("brk_" + t + "_" + i + "_" + f);
                    model.addEquality(esLocal[f][i], esLocal[f + 1][i]).onlyEnforceIf(breakVar);
                    model.addDifferent(esLocal[f][i], esLocal[f + 1][i]).onlyEnforceIf(breakVar.not());
                    breaks.add(breakVar);
                }
            }
        }
        if (!breaks.isEmpty()) {
            model.minimize(LinearExpr.sum(breaks.toArray(new IntVar[0])));
        }

        // 4. Iniciar Solver
        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(90.0); // 90 segundos para permitir resolver todo junto
        CpSolverStatus status = solver.solve(model);

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            guardarSolucion(solver, varsPorTorneo, torneos, claves);
            return true;
        }
        return false;
    }

    private void aplicarRestriccionesTorneo(CpModel model, int n, int numFechas, Literal[][][] p, Literal[][] esLocal) {
        for (int f = 0; f < numFechas; f++) {
            for (int i = 0; i < n; i++) {
                List<Literal> partidosI = new ArrayList<>();
                List<Literal> soyLocalI = new ArrayList<>();
                for (int j = 0; j < n; j++) {
                    if (i == j)
                        continue;
                    partidosI.add(p[f][i][j]);
                    partidosI.add(p[f][j][i]);
                    soyLocalI.add(p[f][i][j]);
                }
                model.addAtMostOne(partidosI);
                model.addEquality(LinearExpr.sum(soyLocalI.toArray(new Literal[0])), esLocal[f][i]);
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j)
                    continue;
                List<Literal> enfrentamiento = new ArrayList<>();
                for (int f = 0; f < numFechas; f++)
                    enfrentamiento.add(p[f][i][j]);
                model.addEquality(LinearExpr.sum(enfrentamiento.toArray(new Literal[0])), 1);
            }
        }
    }

    private void aplicarRestriccionesSecuencia(CpModel model, int n, int numFechas, Literal[][] esLocal) {
        for (int i = 0; i < n; i++) {
            for (int f = 0; f <= numFechas - 4; f++) {
                // Hard constraint: Máximo 3 locales consecutivos (No 4 locales)
                model.addLessOrEqual(
                        LinearExpr.sum(new Literal[] { esLocal[f][i], esLocal[f + 1][i], esLocal[f + 2][i],
                                esLocal[f + 3][i] }),
                        3);

                // Hard constraint: Máximo 3 visitantes consecutivos (No 4 visitantes)
                model.addGreaterOrEqual(
                        LinearExpr.sum(new Literal[] { esLocal[f][i], esLocal[f + 1][i], esLocal[f + 2][i],
                                esLocal[f + 3][i] }),
                        1);
            }
        }
    }

    private void aplicarCapacidadEstadios(CpModel model, List<List<Equipo>> torneos,
            List<Literal[][][]> varsPorTorneo) {
        int penaltyCapacidad = 1000;
        Map<Integer, List<Literal>> partidosPorEstadioSabado = new HashMap<>();
        Map<Integer, List<Literal>> partidosPorEstadioDomingo = new HashMap<>();

        for (int t = 0; t < torneos.size(); t++) {
            List<Equipo> equipos = torneos.get(t);
            Literal[][][] p = varsPorTorneo.get(t);
            int n = equipos.size();
            for (int i = 0; i < n; i++) {
                int estadioId = equipos.get(i).getClub().getSede().getId();
                DiaJuego diaReal = calcularDiaRealJuego(equipos.get(i));
                Map<Integer, List<Literal>> mapaDia = (diaReal == DiaJuego.SABADO) ? partidosPorEstadioSabado
                        : partidosPorEstadioDomingo;

                for (int f = 0; f < p.length; f++) {
                    for (int j = 0; j < n; j++) {
                        if (i == j)
                            continue;
                        mapaDia.computeIfAbsent(estadioId * 1000 + f, k -> new ArrayList<>()).add(p[f][i][j]);
                    }
                }
            }
        }

        for (Map.Entry<Integer, List<Literal>> entry : partidosPorEstadioSabado.entrySet()) {
            aplicarPenalizacionCapacidad(model, entry.getValue(), 6, "S_" + entry.getKey(), penaltyCapacidad);
        }
        for (Map.Entry<Integer, List<Literal>> entry : partidosPorEstadioDomingo.entrySet()) {
            aplicarPenalizacionCapacidad(model, entry.getValue(), 6, "D_" + entry.getKey(), penaltyCapacidad);
        }
    }

    private void aplicarPenalizacionCapacidad(CpModel model, List<Literal> partidos, int limite, String id,
            int penalty) {
        if (partidos.size() <= limite)
            return;
        IntVar sumPartidos = model.newIntVar(0, partidos.size(), "sum_" + id);
        model.addEquality(sumPartidos, LinearExpr.sum(partidos.toArray(new Literal[0])));

        IntVar exceso = model.newIntVar(0, partidos.size(), "exceso_" + id);
        model.addGreaterOrEqual(exceso, LinearExpr.newBuilder().addSum(new IntVar[] { sumPartidos })
                .addTerm(model.newConstant(limite), -1).build());
        model.minimize(LinearExpr.term(exceso, penalty));
    }

    private void aplicarStadiumSharing(CpModel model, List<List<Equipo>> torneos, Map<Integer, Literal[]> mapaLocalia) {
        int penaltySharing = 1000;
        List<String[]> pares = Arrays.asList(
                new String[] { "Independiente", "Independiente (rojo)" },
                new String[] { "Ferrocarril Sud", "Ferro Azul" },
                new String[] { "UNICEN", "Grupo Universitario" },
                new String[] { "SARMIENTO (AYACUCHO)", "ATENEO ESTRADA" },
                new String[] { "Santamarina", "Oficina" },
                new String[] { "Excursionistas", "San José" });

        for (String[] par : pares) {
            Integer id1 = buscarIdPorNombreExacto(par[0], torneos);
            Integer id2 = buscarIdPorNombreExacto(par[1], torneos);
            if (id1 != null && id2 != null) {
                Literal[] l1 = mapaLocalia.get(id1);
                Literal[] l2 = mapaLocalia.get(id2);
                int len = Math.min(l1.length, l2.length);
                for (int f = 0; f < len; f++) {
                    // Soft constraint: Preferiblemente no juegan los dos de local
                    BoolVar choqueSede = model.newBoolVar("choque_sede_" + id1 + "_" + id2 + "_f" + f);
                    model.addLessOrEqual(LinearExpr.sum(new Literal[] { l1[f], l2[f] }), 1)
                            .onlyEnforceIf(choqueSede.not());
                    model.minimize(LinearExpr.term(choqueSede, penaltySharing));
                }
            }
        }
    }

    private void aplicarSincronizaciones(CpModel model, List<List<Equipo>> torneos,
            Map<Integer, Literal[]> mapaLocalia) {
        sincronizar(model, torneos, mapaLocalia, "Independiente Femenino", "Independiente (rojo)");
        sincronizar(model, torneos, mapaLocalia, "Ferrocarril Sud Femenino", "Ferro Azul");
        sincronizar(model, torneos, mapaLocalia, "Loma Negra Femenino", "Loma Negra");
    }

    private void sincronizar(CpModel model, List<List<Equipo>> torneos, Map<Integer, Literal[]> mapaLocalia, String n1,
            String n2) {
        Integer id1 = buscarIdPorNombreExacto(n1, torneos);
        Integer id2 = buscarIdPorNombreExacto(n2, torneos);
        if (id1 != null && id2 != null) {
            Literal[] l1 = mapaLocalia.get(id1);
            Literal[] l2 = mapaLocalia.get(id2);
            int len = Math.min(l1.length, l2.length);

            for (int f = 0; f < len; f++) {
                // 1. Creamos una variable booleana auxiliar
                BoolVar espejoRoto = model.newBoolVar("espejo_roto_" + id1 + "_" + id2 + "_f" + f);

                // 2. Condición: Si espejoRoto es FALSE, entonces l1 TIENE que ser igual a l2
                model.addEquality(l1[f], l2[f]).onlyEnforceIf(espejoRoto.not());

                // 3. Le decimos al solver que su objetivo en la vida es minimizar esta variable
                // Al ponerle un peso enorme (500), el solver va a intentar por todos los
                // medios mantener espejoRoto en FALSE. Solo la pasará a TRUE si una regla
                // física (como un choque de estadios) lo obliga a hacerlo para no dar
                // INFEASIBLE.
                model.minimize(LinearExpr.term(espejoRoto, 500));
            }
        }
    }

    private void aplicarLogisticaFlorida(CpModel model, List<List<Equipo>> torneos,
            Map<Integer, Literal[]> mapaLocalia) {
        Integer idUnion = buscarIdPorNombreExacto("Unión y Progreso", torneos);
        Integer idJuve = buscarIdPorNombreExacto("Juventud Unida", torneos);

        if (idUnion != null && idJuve != null) {
            Literal[] lUnion = mapaLocalia.get(idUnion);
            Literal[] lJuve = mapaLocalia.get(idJuve);
            int len = Math.min(lUnion.length, lJuve.length);
            for (int f = 0; f < len; f++) {
                // Soft constraint
                BoolVar choqueFlorida = model.newBoolVar("choque_florida_f" + f);
                model.addLessOrEqual(LinearExpr.sum(new Literal[] { lUnion[f], lJuve[f] }), 1)
                        .onlyEnforceIf(choqueFlorida.not());
                model.minimize(LinearExpr.term(choqueFlorida, 800));
            }
        }
    }

    private void aplicarCupoAyacucho(CpModel model, List<List<Equipo>> torneos, Map<Integer, Literal[]> mapaLocalia,
            int maxFechas) {
        List<Integer> idsAyacucho = new ArrayList<>();
        for (List<Equipo> liga : torneos) {
            for (Equipo e : liga) {
                if (esDeAyacucho(e)) {
                    idsAyacucho.add(e.getId());
                }
            }
        }
        for (int f = 0; f < maxFechas; f++) {
            List<Literal> locales = new ArrayList<>();
            for (Integer id : idsAyacucho) {
                Literal[] v = mapaLocalia.get(id);
                if (v != null && f < v.length)
                    locales.add(v[f]);
            }
            if (!locales.isEmpty()) {
                // Soft constraint: Intentar no pasar de 2, pero permitir hasta 3 con penalidad
                IntVar nLocalesAyacucho = model.newIntVar(0, locales.size(), "ayac_locales_f" + f);
                model.addEquality(nLocalesAyacucho, LinearExpr.sum(locales.toArray(new Literal[0])));

                BoolVar cupoExcedido = model.newBoolVar("ayac_exceso_f" + f);
                model.addGreaterOrEqual(nLocalesAyacucho, 3).onlyEnforceIf(cupoExcedido);
                model.addLessOrEqual(nLocalesAyacucho, 2).onlyEnforceIf(cupoExcedido.not());
                model.minimize(LinearExpr.term(cupoExcedido, 300));
            }
        }
    }

    private void aplicarJuarenseAlumniConstraint(CpModel model, List<List<Equipo>> torneos,
            Map<Integer, Literal[]> mapaLocalia) {
        Integer idJ = buscarIdPorNombreExacto("Juarense", torneos);
        Integer idA = buscarIdPorNombreExacto("Alumni", torneos);
        if (idJ != null && idA != null) {
            Literal[] lJ = mapaLocalia.get(idJ);
            Literal[] lA = mapaLocalia.get(idA);
            int len = Math.min(lJ.length, lA.length);
            for (int f = 0; f < len; f++) {
                // Soft constraint
                BoolVar choqueSeguridad = model.newBoolVar("choque_seguridad_f" + f);
                model.addLessOrEqual(LinearExpr.sum(new Literal[] { lJ[f], lA[f] }), 1)
                        .onlyEnforceIf(choqueSeguridad.not());
                model.minimize(LinearExpr.term(choqueSeguridad, 1500));
            }
        }
    }

    // --- HELPERS REFINADOS ---

    private DiaJuego calcularDiaRealJuego(Equipo e) {
        String n = e.getNombre().toUpperCase();
        if (n.contains("DEFENSORES DEL CERRO") && e.getCategoriasHabilitadas().contains(Categoria.QUINTA))
            return DiaJuego.DOMINGO;
        if (n.contains("LOMA NEGRA") && (e.getBloque() == Bloque.FEM_MAYORES || e.getBloque() == Bloque.FEM_MENORES))
            return DiaJuego.DOMINGO;
        return e.getDiaDeJuego();
    }

    private boolean esDeAyacucho(Equipo e) {
        if (e == null)
            return false;
        String nombre = e.getNombre().toUpperCase();
        return nombre.contains("AYACUCHO") || nombre.contains("SARMIENTO")
                || nombre.contains("ESTRADA") || nombre.contains("BOTAFOGO");
    }

    private Map<String, List<Equipo>> agruparEquiposPorTorneo(List<Equipo> todos) {
        return todos.stream().collect(Collectors.groupingBy(e -> e.getBloque().name() + "-" + e.getLiga().name()));
    }

    // 🔥 FIX: Búsqueda estricta para evitar confundir "Independiente" con
    // "Independiente Femenino"
    private Integer buscarIdPorNombreExacto(String n, List<List<Equipo>> torneos) {
        for (List<Equipo> liga : torneos) {
            for (Equipo e : liga) {
                if (e.getNombre().trim().equalsIgnoreCase(n.trim())) {
                    return e.getId();
                }
            }
        }
        return null;
    }

    private void guardarSolucion(CpSolver solver, List<Literal[][][]> varsPorTorneo, List<List<Equipo>> torneos,
            List<String> claves) {
        List<Fecha> total = new ArrayList<>();
        for (int t = 0; t < torneos.size(); t++) {
            List<Equipo> equipos = torneos.get(t);
            Literal[][][] p = varsPorTorneo.get(t);
            String[] parts = claves.get(t).split("-");
            Bloque b = Bloque.valueOf(parts[0]);
            Liga l = Liga.valueOf(parts[1]);

            for (int f = 0; f < p.length; f++) {
                Fecha fecha = new Fecha(f + 1);
                fecha.setBloque(b);
                fecha.setLiga(l);
                fecha.setPartidos(new ArrayList<>());
                for (int i = 0; i < equipos.size(); i++) {
                    for (int j = 0; j < equipos.size(); j++) {
                        if (i == j)
                            continue;
                        if (solver.booleanValue(p[f][i][j])) {
                            Partido part = new Partido();
                            part.setLocal(equipos.get(i));
                            part.setVisitante(equipos.get(j));
                            part.setFecha(fecha);
                            part.setCancha(equipos.get(i).getClub().getSede());
                            fecha.getPartidos().add(part);
                        }
                    }
                }
                if (!fecha.getPartidos().isEmpty())
                    total.add(fecha);
            }
        }
        fechaRepository.saveAll(total);
    }
}
