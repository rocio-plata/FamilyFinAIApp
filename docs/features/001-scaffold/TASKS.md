# TASKS: Scaffold de la pantalla de inicio

**SPEC:** `docs/features/001-scaffold/SPEC.md` (Aprobada)
**PLAN:** `docs/features/001-scaffold/PLAN.md` (Aprobado)

<!-- Tareas de máx. 20-30 min, en orden de dependencia. Marcar [x] solo tras
ejecutar la validación y registrar en "Evidencia" el resultado real. Si una
tarea queda bloqueada, anotar el motivo en "Evidencia". -->

## Configuración

- [ ] **T-01 · Dependencia de iconos**
  - **Cubre:** RF-01, RF-05 · **Depende de:** —
  - **Alcance:** añadir `material-icons-extended` al catálogo y a `app/build.gradle.kts`.
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** la compilación de debug termina sin errores con la dependencia añadida.
  - **Evidencia:** —

- [ ] **T-02 · Cadenas de texto**
  - **Cubre:** RF-12 (apoya RF-10) · **Depende de:** —
  - **Alcance:** en `strings.xml`, textos de las 5 pestañas, cabecera de ejemplo, FAB y descripciones accesibles, en español.
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y todos los textos del scaffold existen como recursos, sin cadenas literales previstas en código.
  - **Evidencia:** —

- [ ] **T-03 · Orientación vertical**
  - **Cubre:** RF-09 (CA-06) · **Depende de:** —
  - **Alcance:** fijar orientación vertical en `MainActivity` dentro del manifest.
  - **Validación:** `./gradlew :app:assembleDebug` y revisión del manifest.
  - **Hecho cuando:** el manifest declara `portrait` para `MainActivity` y compila.
  - **Evidencia:** —

## Interfaz

- [ ] **T-04 · Destinos y orden de pestañas**
  - **Cubre:** RF-05 · **Depende de:** T-01, T-02
  - **Alcance:** definir los 5 destinos (nombre, icono y etiqueta) en el orden Inicio, Movimientos, Reportes, Presupuestos, Asistente.
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y los 5 destinos existen en el orden de la SPEC con texto e icono.
  - **Evidencia:** —

- [ ] **T-05 · Cabecera estática**
  - **Cubre:** RF-01, RF-10, RF-12 · **Depende de:** T-02
  - **Alcance:** selector de familia (nombre y rol de ejemplo), icono de notificaciones y avatar; visibles, sin acción, con descripción accesible y área táctil mínima; usa colores del tema, sin hexadecimales.
  - **Validación:** vista previa y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** la vista previa muestra los tres elementos y cada uno tiene descripción accesible.
  - **Evidencia:** —

- [ ] **T-06 · Barra inferior**
  - **Cubre:** RF-05, RF-10 · **Depende de:** T-04
  - **Alcance:** barra de navegación Material 3 con las 5 pestañas, la seleccionada resaltada, y un callback de selección.
  - **Validación:** vista previa y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** la vista previa muestra las 5 pestañas con Inicio seleccionada y etiquetas legibles.
  - **Evidencia:** —

- [ ] **T-07 · Placeholders de pestañas**
  - **Cubre:** RF-06 · **Depende de:** T-04
  - **Alcance:** contenido que muestra solo el nombre de la sección, para las 4 pestañas distintas de Inicio.
  - **Validación:** vista previa y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** la vista previa de cada una de las 4 muestra únicamente su nombre.
  - **Evidencia:** —

- [ ] **T-08 · Cuerpo de Inicio y FAB**
  - **Cubre:** RF-03, RF-04, RF-10 · **Depende de:** T-02
  - **Alcance:** cuerpo placeholder sin datos ni secciones; FAB "+" sin acción y con descripción accesible.
  - **Validación:** vista previa y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** la vista previa muestra el cuerpo vacío y el FAB, y este no tiene acción asociada.
  - **Evidencia:** —

- [ ] **T-09 · Contenedor raíz y estado de pestaña**
  - **Cubre:** RF-02, RF-05, RF-08, RF-11 · **Depende de:** T-05, T-06, T-07, T-08
  - **Alcance:** `Scaffold` que une cabecera (en las 5 pestañas), barra, FAB solo en Inicio y contenido de la pestaña activa; estado en memoria con Inicio por defecto, sin persistirlo; respeta insets del sistema.
  - **Validación:** vista previa y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** al cambiar de pestaña en la vista previa interactiva se ve su contenido, la cabecera permanece y el FAB solo aparece en Inicio.
  - **Evidencia:** —

- [ ] **T-10 · Conectar en MainActivity**
  - **Cubre:** RF-01, RF-08 · **Depende de:** T-09
  - **Alcance:** mostrar el contenedor raíz en `MainActivity`; retirar `Greeting` y su vista previa.
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y no queda código del saludo de ejemplo.
  - **Evidencia:** —

## Comportamiento

- [ ] **T-11 · Botón Atrás**
  - **Cubre:** RF-07, RF-13 · **Depende de:** T-10
  - **Alcance:** desde otra pestaña, Atrás (incluido el gesto predictivo) selecciona Inicio; desde Inicio no se intercepta. Verificar que `activity-compose` instalada lo soporta.
  - **Validación:** `./gradlew :app:assembleDebug`; comprobación en T-14 y T-16.
  - **Hecho cuando:** compila y el manejador de Atrás solo está activo fuera de Inicio.
  - **Evidencia:** —

- [ ] **T-12 · Pulsaciones repetidas**
  - **Cubre:** RF-13 · **Depende de:** T-10
  - **Alcance:** que seleccionar la pestaña activa o pulsar el FAB repetidamente no cambie el estado ni falle.
  - **Validación:** `./gradlew :app:assembleDebug`; comprobación en T-14.
  - **Hecho cuando:** compila y la selección de la pestaña ya activa no altera el estado.
  - **Evidencia:** —

## Validación

- [ ] **T-13 · Tests de UI: estructura**
  - **Cubre:** RF-01 a RF-06 · **Depende de:** T-12, T-11
  - **Alcance:** tests Compose nuevos para CA-01, CA-02 y CA-03.
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest` (requiere emulador o dispositivo).
  - **Hecho cuando:** los tests de CA-01, CA-02 y CA-03 se ejecutan y pasan.
  - **Evidencia:** —

- [ ] **T-14 · Tests de UI: Atrás, accesibilidad y repetición**
  - **Cubre:** RF-07, RF-10, RF-13 · **Depende de:** T-13
  - **Alcance:** tests Compose nuevos para CA-04 (Atrás desde otra pestaña), CA-07 (descripciones) y CA-09 (toques repetidos).
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest`.
  - **Hecho cuando:** los tests de CA-04, CA-07 y CA-09 se ejecutan y pasan.
  - **Evidencia:** —

- [ ] **T-15 · Manual: ciclo de vida y orientación**
  - **Cubre:** RF-08, RF-09 · **Depende de:** T-14
  - **Alcance:** CA-05 (recrear y terminar el proceso en otra pestaña), segundo plano y volver desde otra app, y CA-06 (rotación automática activada).
  - **Validación:** pasos de la SPEC en emulador.
  - **Hecho cuando:** cada escenario tiene resultado anotado y coincide con la SPEC, o queda registrado como no verificado.
  - **Evidencia:** —

- [ ] **T-16 · Manual: pantallas, TalkBack y Atrás**
  - **Cubre:** RF-07, RF-10, RF-11, RF-13 · **Depende de:** T-15
  - **Alcance:** CA-08 (teléfono pequeño y grande, gestos y 3 botones), CA-07 (TalkBack), CA-09 (gesto Atrás) y salida de la app desde Inicio (CA-04).
  - **Validación:** pasos de la SPEC en emulador.
  - **Hecho cuando:** cada escenario tiene resultado y capturas anotados, o queda registrado como no verificado.
  - **Evidencia:** —

- [ ] **T-17 · Cierre y regresión**
  - **Cubre:** todos los RF · **Depende de:** T-16
  - **Alcance:** ejecutar compilación, tests unitarios y lint; revisar cada CA y preparar el informe final con lo no verificado.
  - **Validación:** `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`.
  - **Hecho cuando:** los tres comandos pasan, o los fallos quedan registrados, y cada CA tiene evidencia o motivo de no verificación.
  - **Evidencia:** —
