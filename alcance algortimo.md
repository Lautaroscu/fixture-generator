
```markdown
# Documentación de Alcance de la Solución: Motor de Fixtures "Liga de Tandil"

**Objetivo del Documento:** Servir como Base de Conocimiento (Knowledge Base) integral para agentes de IA y equipos de desarrollo. Contiene el 100% de las reglas de negocio, entidades, restricciones lógicas y logísticas necesarias para modelar el problema de optimización combinatoria utilizando herramientas de Programación por Restricciones (CP-SAT) y bases de datos relacionales.

---

## 1. Universo de Entidades y Datos Base

El sistema debe gestionar la programación de partidos para un ecosistema compuesto por 31 entidades de competición masculina y 13 entidades femeninas, distribuidas en múltiples categorías y divisiones.

### 1.1. Categorías y Formato de Competición

Todas las categorías inician el Torneo Apertura en la misma fecha calendario (Fecha 1), pero poseen diferentes duraciones y formatos.

| Categoría | Equipos | Formato Base | Fechas Totales | Día Regular de Juego |
| --- | --- | --- | --- | --- |
| **Primera A** | 12 | Ida y Vuelta | 22 | Domingo |
| **Primera B** | 14 | Ida y Vuelta | 26 | Domingo |
| **Infantiles A** | 10 | Ida y Vuelta | 18 | Sábado |
| **Infantiles B** | 10 | Ida y Vuelta | 18 | Sábado |
| **Infantiles C** | 7 nodos | Doble Ida y Vuelta | 20 | Sábado |
| **Femenino** | 12 | Ida y Vuelta | 22 | Sábado |

### 1.2. Bloques de Divisiones y Días Asignados

El sistema cuenta con dos cronogramas paralelos que consumen los mismos recursos físicos (Estadios).

* **Bloque Sábado:** 5ta, 6ta, 7ma, 8va (Masculino) + Femenino (Primera, Sub 16, Sub 14, Sub 12).
* **Bloque Domingo:** 1ra, Reserva, 9na, 10ma, 11ma (Masculino).
* **Manejo de Divisiones Faltantes:** Si un club no posee una división específica (ej. no tiene 6ta), el algoritmo genera un "Hueco" (Free Date) únicamente para esa división, manteniendo el emparejamiento del resto de las categorías de ese club contra su rival de turno.

---

## 2. Restricciones Fundamentales del Modelo (Hard Constraints)

Estas reglas son inquebrantables; si alguna no se cumple, el fixture es considerado inválido (Infeasible).

### 2.1. Unicidad y Direccionalidad del Enfrentamiento

1. **Enfrentamiento Único por Vuelta:** Un Equipo A solo puede enfrentar a un Equipo B una vez en cada vuelta del torneo.
2. **Localía Única contra un mismo Rival:** Un equipo **NO puede ser local dos veces contra el mismo equipo** en el mismo torneo. Si en la ida el partido es `Equipo A (Local) vs Equipo B (Visitante)`, obligatoriamente en la vuelta debe ser `Equipo B (Local) vs Equipo A (Visitante)`.

### 2.2. Exclusión Mutua de Recursos Compartidos (Estadios Cruzados)

Cuando dos entidades comparten la misma infraestructura física, bajo ningún concepto pueden ser locales en la misma fecha. Obligatoriamente, si uno es Local, el otro debe ser Visitante o tener fecha libre.

* **Agustín F. Berroeta:** Independiente vs. Independiente (Rojo)
* **Dámaso Latasa:** Ferrocarril Sud vs. Ferro Azul
* **La Movediza:** UNICEN vs. Grupo Universitario
* **Municipal Ayacucho:** Sarmiento vs. Ateneo Estrada
* **Predio Centenario:** Santamarina vs. Oficina
* **Excursionistas:** Excursionistas vs. San José (Masculino)
* **Sedes Únicas Exclusivas:** "Boca de la Base" es uso exclusivo de El Potrero (Femenino). "San Lorenzo Stadium" para San Lorenzo de Rauch.

### 2.3. Restricciones de Seguridad Policial y Geográfica

1. **Cupo Ayacucho:** En una misma fecha, solo un máximo de **dos (2) equipos** de la ciudad de Ayacucho pueden oficiar de locales simultáneamente (Aplica a: Botafogo, Atlético Ayacucho, Sarmiento, Ateneo Estrada).
2. **Seguridad Benito Juárez:** Los clubes `Juarense` y `Alumni` deben ir siempre cruzados. Nunca pueden ser locales la misma fecha en la misma ciudad.


### 2.4. Espejo y Sincronización Inter-Categorías

1. **Independiente Femenino:** Obligado a tener la misma condición de localía que `Independiente Rojo` en la misma fecha (Sábados).
2. **Ferro Femenino:** Obligado a tener la misma condición de localía que `Ferro Azul` en la misma fecha (Sábados).

---

## 3. Clústeres Logísticos de Alta Complejidad

Estas reglas gestionan la ocupación de estadios donde convergen más de dos equipos y excepciones de días de juego. Ningún estadio puede albergar más de **6 partidos por día**.

### 3.1. Clúster "Quinta La Florida" y "Juve Stadium"

Dependencia en cascada basada en la localía de **Unión y Progreso**.

* **Condición Gatillo:** Cuando `Unión y Progreso` oficia de **Visitante**, se habilita el uso del predio para los equipos inquilinos.
* **Distribución de Sábado (en Quinta La Florida):** Juegan de local las categorías menores de Juventud Unida Masculino (5ta, 7ma, 8va) y Juventud Unida Negro Femenino (Sub 16, Sub 14).
* **Distribución de Domingo (en Quinta La Florida):** Juegan de local Juventud Unida Masculino (1ra y Reserva) y San José Femenino (Todas sus divisiones).
* **Distribución de Domingo (en Juve Stadium):** Para no saturar el límite de 6 partidos de la cancha principal, las categorías 9na, 10ma y 11ma de Juventud Unida Masculino ofician de local en "Juve Stadium" (Cancha sintética).

### 3.2. Clúster "Figueroa"

Infraestructura compartida mediante triangulación estricta.

* **Equipos Involucrados:** Deportivo Tandil, Defensores del Cerro y Herederos.
* **Cruzamiento:** Deportivo Tandil va cruzado con Defensores del Cerro y con Herederos.
* **Uso del Sábado:** Juventud Unida (Tandil) Femenino utiliza este estadio los sábados, cruzado también con Deportivo Tandil. Herederos (Inferiores) utiliza la cancha cruzado con Dep. Tandil.
* **Uso del Domingo:** Defensores del Cerro oficia de local con sus categorías mayores.

### 3.3. Excepciones de Calendario (Día de Juego)

1. **Defensores del Cerro (5ta División):** A diferencia de la regla general de sábados, su 5ta división se adelanta/retrasa para jugar los **Domingos**, acompañando a su 1ra, Reserva y 9na.
2. **Loma Negra (Femenino):** A diferencia del resto del femenino, juega los **Domingos** oficiando de local en la misma fecha y cancha que la 1ra División Masculina de Loma Negra.

---

## 4. Objetivos de Optimización (Soft Constraints)

Estas reglas no deben quebrar el modelo si son matemáticamente imposibles debido a los estadios compartidos, pero el algoritmo debe penalizar severamente su incumplimiento para asegurar el fixture más justo posible.

1. **Rachas de Localía (Alternancia):** La meta es un ritmo estrictamente alterno (Local - Visitante - Local - Visitante). Ningún equipo debería tener **3 localías o 3 visitas consecutivas**.
   * *Manejo del Algoritmo:* El sistema debe apuntar a un máximo de 2 rachas. Solo se permitirá una racha de 3 si (y solo si) es la única vía matemática para destrabar una colisión de estadios compartidos.

2. **Acoplamiento Institucional:** El algoritmo debe buscar maximizar la coincidencia de localía entre todas las categorías de un mismo club en la misma fecha (Ej. Si la Primera de Juarense juega en casa el domingo, intentar que todas sus inferiores jueguen en casa el sábado).

3. **Equidad de Descanso (Libres):** En la Categoría C (que posee 7 nodos y uno queda libre por fecha), la distribución de la fecha libre debe ser equitativa y tratar de no perjudicar el acoplamiento logístico general del club afectado.
```

**Notas de formato para el agente:**
- Las **Hard Constraints** (sección 2) son restricciones duras del modelo CP-SAT.
- Las **Soft Constraints** (sección 4) deben modelarse como funciones de costo/puntuación en la función objetivo.
- Los **clústeres** (sección 3) requieren lógica condicional compleja y manejo de capacidad de estadios (máximo 6 partidos/día).
- Las **sincronizaciones** (2.5) son constraints de igualdad entre variables de diferentes categorías.
