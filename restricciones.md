Para implementar este sistema en Java, el código será más extenso y estructurado que en Python, pero te brindará un control total sobre los tipos de datos y la integración con PostgreSQL.
A continuación, presento la traducción de todas las restricciones a la API de Java para OR-Tools CP-SAT, organizadas por su naturaleza lógica.
1. Configuración de Variables Base
En Java, trabajaremos con arreglos multidimensionales de BoolVar. La variable principal es x[fecha][local][visitante].

Java


// Variables de decisión: x[f][i][j] es true si i es local contra j en fecha f
// Suponemos que tenemos una estructura para cada Categoría
BoolVar[][][] x = new BoolVar[totalFechas][numEquipos][numEquipos];
// Variable auxiliar: localia[e][f] es true si el equipo 'e' es local en fecha 'f'
BoolVar[][] localia = new BoolVar[numEquipos][totalFechas];


2. Restricciones de Integridad Deportiva (Hard)
A. Formato Round Robin (Todos contra todos)
Cada equipo $i$ debe jugar contra el equipo $j$ exactamente una vez como local en el torneo.

$$\sum_{f} x_{f,i,j} = 1$$

Java


for (int i = 0; i < numEquipos; i++) {
    for (int j = 0; j < numEquipos; j++) {
        if (i == j) continue;
        // En todo el torneo, i es local contra j exactamente 1 vez
        model.addExactlyOne(new BoolVar[]{ /* todas las x[f][i][j] para f */ });
    }
}


B. Un partido por fecha
Un equipo solo puede jugar un partido (sea local o visitante) por fecha.

Java


for (int f = 0; f < totalFechas; f++) {
    for (int i = 0; i < numEquipos; i++) {
        List<BoolVar> partidosEnFecha = new ArrayList<>();
        for (int j = 0; j < numEquipos; j++) {
            if (i == j) continue;
            partidosEnFecha.add(x[f][i][j]); // i local
            partidosEnFecha.add(x[f][j][i]); // i visitante
        }
        model.addAtMostOne(partidosEnFecha.toArray(new BoolVar[0]));
    }
}


3. Restricciones Logísticas de Tandil
A. Alternancia Máxima (Rachas de 2)
Impide que un equipo sea local o visitante 3 veces seguidas.

Java


for (int i = 0; i < numEquipos; i++) {
    for (int f = 0; f <= totalFechas - 3; f++) {
        // Máximo 2 locales seguidas (suma <= 2)
        model.addLessOrEqual(LinearExpr.sum(new BoolVar[]{
            localia[i][f], localia[i][f+1], localia[i][f+2]
        }), 2);
        // Máximo 2 visitas seguidas (suma >= 1, porque 0-0-0 localías = 3 visitas)
        model.addGreaterOrEqual(LinearExpr.sum(new BoolVar[]{
            localia[i][f], localia[i][f+1], localia[i][f+2]
        }), 1);
    }
}


B. Sedes Compartidas (Exclusión Mutua)
Si el equipo $A$ y $B$ comparten estadio, no pueden ser locales en la misma fecha.

Java


// Ejemplo: Independiente (0) e Independiente Rojo (1)
for (int f = 0; f < totalFechas; f++) {
    model.addLessOrEqual(LinearExpr.sum(new BoolVar[]{
        localia[equipoA][f], localia[equipoB][f]
    }), 1);
}


C. Seguridad: Cupo Ayacucho y Benito Juárez
Restricción de máximo 2 locales en Ayacucho y alternancia obligatoria en Juárez.

Java


// Ayacucho: Máx 2 por fecha
for (int f = 0; f < totalFechas; f++) {
    model.addLessOrEqual(LinearExpr.sum(new BoolVar[]{
        localia[botafogo][f], localia[atletico][f], localia[sarmiento][f], localia[ateneo][f]
    }), 2);
}

// Benito Juárez: Juarense (A) y Alumni (B) cruzados
for (int f = 0; f < totalFechas; f++) {
    model.addExactlyOne(new BoolVar[]{ localia[juarense][f], localia[alumni][f] });
}


4. Sincronización y Clústeres (Implicaciones Logísticas)
A. Espejo Femenino (Independiente y Ferro)
El femenino debe seguir la localía del equipo masculino específico (Rojo/Azul).

Java


for (int f = 0; f < totalFechas; f++) {
    // Femenino Local <=> Independiente Rojo Local
    model.addEquality(localia[indepFem][f], localia[indepRojo][f]);
}


B. Clúster Quinta La Florida (La Gran Dependencia)
Aquí usamos OnlyEnforceIf para activar la localía de Juventud Unida cuando Unión y Progreso viaja.

Java


for (int f = 0; f < totalFechas; f++) {
    // Si Unión es Visitante (localia == 0) -> Juventud Unida Masculino es Local
    model.addEquality(localia[juventudUnida][f], 1).onlyEnforceIf(localia[unionProgreso][f].not());
    // Si Unión es Local -> Juventud Unida DEBE ser Visitante
    model.addEquality(localia[juventudUnida][f], 0).onlyEnforceIf(localia[unionProgreso][f]);
}


5. Optimización (Soft Constraints)
En Java, para evitar que el solver se trabe, añadimos una penalización por cada "break" (romper la alternancia perfecta $L-V-L-V$).

Java


for (int i = 0; i < numEquipos; i++) {
    for (int f = 0; f < totalFechas - 1; f++) {
        BoolVar breakOcurrido = model.newBoolVar("break_" + i + "_" + f);
        // Si localia[f] == localia[f+1], entonces breakOcurrido es true
        model.addEquality(localia[i][f], localia[i][f+1]).onlyEnforceIf(breakOcurrido);
        model.minimize(LinearExpr.term(breakOcurrido, 1)); 
    }
}



