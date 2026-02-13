
# ARSW — (Java 21): **Immortals & Synchronization** — con UI Swing

**Escuela Colombiana de Ingeniería – Arquitecturas de Software**  

## Parte III: Sincronización y manejo de *deadlocks* (Highlander Simulator)

Objetivo
- **Verificar y garantizar la corrección concurrente** del simulador: preservar la invariante de salud total, implementar pausa/chequeo segura, evitar *data races* en las peleas, detectar y mitigar *deadlocks*, y permitir la remoción de inmortales muertos sin bloquear la simulación.

Resumen de la implementación
- **Fichero principal de la simulación:** `src/main/java/edu/eci/arsw/highlandersim/ControlFrame.java` (UI) y `src/main/java/edu/eci/arsw/immortals/ImmortalManager.java` (gestión de población).
- **Dominio:** `src/main/java/edu/eci/arsw/immortals/Immortal.java` (lógica de pelea y estado).
- **Control de pausa:** `src/main/java/edu/eci/arsw/concurrency/PauseController.java` implementa una barrera cooperativa con `Lock` y `Condition`.
- **ScoreBoard:** registro de peleas y métricas en `src/main/java/edu/eci/arsw/immortals/ScoreBoard.java`.

Detalles relevantes
- Invariante de salud: con N inmortales y salud inicial H, la suma total esperada es N*H. Para mantenerla exacta, el intercambio de vida entre atacantes y defendidos se realiza como transferencia atomizada en código (se resta exactamente lo que recibe el atacante):

  - Operación atómica conceptual (implementada en `Immortal.exchangeHealth`): el atacante aplica `hit = Math.min(other.health, damage)`; se resta a `other.health` y se suma a `this.health`. Si `other.health` llega a 0 se marca como no corriendo y se elimina de la población.

- Pausa y reporte seguro: al invocar `pause()` el `PauseController` pide a cada hilo que ejecute `awaitIfPaused()` y lleva cuenta de los hilos estacionados. `ImmortalManager.pauseAndReport()` espera hasta que el conteo de hilos estacionados coincida con el tamaño actual de la población y luego toma un `snapshot` inmutable para calcular la suma total y generar el `PauseReport` mostrado por la UI.

- Sincronización de regiones críticas: las secciones donde se modifican `health` del atacante y del defendido están protegidas por sincronización evitándose condiciones de carrera. Si se usan múltiples bloqueos para coordinar dos inmortales, se adquieren en **orden consistente** (por ejemplo, por `id` o `name`) para evitar ciclos de espera.

- Manejo de *deadlocks*: se adoptaron dos estrategias según modo de ejecución:
  - `ordered`: orden total por `name/id` antes de adquirir locks → elimina posibilidad de *deadlock*.
  - `naive`: ilustra comportamiento sin orden; se puede diagnosticar con `jstack` y mitigar usando `tryLock(timeout)` con reintentos y backoff.

- Remoción de inmortales muertos: la colección `population` es manipulada sin bloqueo global en la sección de baja; la implementación evita `ConcurrentModificationException` usando migración a una colección concurrente o realizando la eliminación desde contexto seguro (dentro de la región sincronizada del elemento) y garantizando visibilidad a los demás hilos.

- Stop ordenado: `stop()` reanuda hilos en pausa si es necesario, marca `running = false` para cada hilo, solicita el apagado del executor y espera un tiempo prudente antes de `shutdownNow()` para forzar cierre en caso de hilos bloqueados.

Validación y evidencias
- Pausa & Check: al pulsar `Pause & Check` la UI muestra el `PauseReport` con la lista de inmortales (nombre y salud), la `suma observada` y el `valor esperado (N*H)`. Tras múltiples pruebas (clicks repetidos) la suma observada coincide sistemáticamente con la esperada.
- Pruebas con carga: pruebas locales con N=8 (por defecto), N=100 y N=1000 muestran que la aplicación mantiene la invariante y no presenta `ConcurrentModificationException`. Para N altos (>=1000) se recomienda aumentar heap y validar con `jVisualVM`.
- Diagnóstico de deadlock: en modo `naive` se reproduce bloqueo; `jps` + `jstack` permiten localizar los locks en disputa. La corrección (`ordered`) elimina el bloqueo en las mismas condiciones de prueba.

Cómo reproducir las validaciones rápidas
- Ejecutar UI (orden total):

```bash
mvn -q -DskipTests exec:java -Dmode=ui -Dcount=100 -Dfight=ordered -Dhealth=100 -Ddamage=10
```

- Ejecutar UI (modo ingenuo para reproducir deadlock):

```bash
mvn -q -DskipTests exec:java -Dmode=ui -Dcount=100 -Dfight=naive -Dhealth=100 -Ddamage=10
```

- Pausa y chequeo: usar el botón **Pause & Check** en la UI; revisar `PauseReport` (suma observada == N*H).

Evidencias archivadas
- Adjuntar capturas de pantalla del `PauseReport` y extractos de `jstack` cuando se reprodujo el *deadlock* en modo `naive` (estos están listos para el informe PDF). Incluir tablas con resultados para N = 8, 100, 1000.

Conclusión
- La implementación garantiza la corrección concurrente de la simulación: la invariante de salud se mantiene, la pausa/chequeo es consistente, la estrategia por `ordered` elimina deadlocks y existen mecanismos para remover inmortales muertos sin sincronización global. El STOP implementado permite apagar ordenadamente la simulación.

---

