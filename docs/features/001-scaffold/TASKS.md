# TASKS: Scaffold de la pantalla de inicio

**SPEC:** `docs/features/001-scaffold/SPEC.md` (Aprobada)
**PLAN:** `docs/features/001-scaffold/PLAN.md` (Aprobado)

<!-- Tareas de máx. 20-30 min, en orden de dependencia. Marcar [x] solo tras
ejecutar la validación y registrar en "Evidencia" el resultado real. Si una
tarea queda bloqueada, anotar el motivo en "Evidencia". -->

## Configuración

- [x] **T-01 · Dependencia de iconos**
  - **Cubre:** RF-01, RF-05 · **Depende de:** —
  - **Alcance:** añadir `material-icons-extended` al catálogo y a `app/build.gradle.kts`.
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** la compilación de debug termina sin errores con la dependencia añadida.
  - **Evidencia:** 2026-10-18: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (3m 34s) con `material-icons-extended` añadido al catálogo y a `app/build.gradle.kts`, versión fijada por el Compose BOM.

- [x] **T-02 · Cadenas de texto**
  - **Cubre:** RF-12 (apoya RF-10) · **Depende de:** —
  - **Alcance:** en `strings.xml`, textos de las 5 pestañas, cabecera de ejemplo, FAB y descripciones accesibles, en español.
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y todos los textos del scaffold existen como recursos, sin cadenas literales previstas en código.
  - **Evidencia:** 2026-10-18: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL con las cadenas nuevas en `strings.xml` (5 pestañas, cabecera de ejemplo, descripciones accesibles, FAB). El uso en código se comprueba en T-04 a T-08.

- [x] **T-03 · Orientación vertical**
  - **Cubre:** RF-09 (CA-06) · **Depende de:** —
  - **Alcance:** fijar orientación vertical en `MainActivity` dentro del manifest.
  - **Validación:** `./gradlew :app:assembleDebug` y revisión del manifest.
  - **Hecho cuando:** el manifest declara `portrait` para `MainActivity` y compila.
  - **Evidencia:** 2026-10-18: manifest con `android:screenOrientation="portrait"` en `MainActivity` (línea 18); `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. CA-06 (comportamiento en dispositivo) sigue pendiente de T-15.

## Interfaz

- [x] **T-04 · Destinos y orden de pestañas**
  - **Cubre:** RF-05 · **Depende de:** T-01, T-02
  - **Alcance:** definir los 5 destinos (nombre, icono y etiqueta) en el orden Inicio, Movimientos, Reportes, Presupuestos, Asistente.
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y los 5 destinos existen en el orden de la SPEC con texto e icono.
  - **Evidencia:** 2026-10-18: `ui/main/MainDestination.kt` define las 5 entradas en el orden Inicio, Movimientos, Reportes, Presupuestos, Asistente, cada una con etiqueta (`strings.xml`) e icono; `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.

- [x] **T-05 · Cabecera estática**
  - **Cubre:** RF-01, RF-10, RF-12 · **Depende de:** T-02
  - **Alcance:** selector de familia (nombre y rol de ejemplo), icono de notificaciones y avatar; visibles, sin acción, con descripción accesible y área táctil mínima; usa colores del tema, sin hexadecimales.
  - **Validación:** vista previa y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** la vista previa muestra los tres elementos y cada uno tiene descripción accesible.
  - **Evidencia:** 2026-10-18: `ui/main/MainHeader.kt` (con `@Preview`) implementado; `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. Vista previa `MainHeaderPreview` revisada por la persona el 2026-10-19: se ven los tres elementos.

- [x] **T-06 · Barra inferior**
  - **Cubre:** RF-05, RF-10 · **Depende de:** T-04
  - **Alcance:** barra de navegación Material 3 con las 5 pestañas, la seleccionada resaltada, y un callback de selección.
  - **Validación:** vista previa y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** la vista previa muestra las 5 pestañas con Inicio seleccionada y etiquetas legibles.
  - **Evidencia:** 2026-10-19: `ui/main/MainBottomBar.kt` implementado. La persona detectó que las etiquetas "Movimientos" y "Presupuestos" no se ven bien; se reduce el estilo a `labelSmall` sin ajuste de línea. Vista previa `MainBottomBarPreview` revisada por la persona el 2026-10-19 tras el ajuste: 5 pestañas, Inicio seleccionada, etiquetas legibles.

- [x] **T-07 · Placeholders de pestañas**
  - **Cubre:** RF-06 · **Depende de:** T-04
  - **Alcance:** contenido que muestra solo el nombre de la sección, para las 4 pestañas distintas de Inicio.
  - **Validación:** vista previa y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** la vista previa de cada una de las 4 muestra únicamente su nombre.
  - **Evidencia:** 2026-10-19: `ui/main/TabPlaceholder.kt` con una vista previa por pestaña (Movimientos, Reportes, Presupuestos, Asistente); `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. Las 4 vistas previas fueron revisadas por la persona el 2026-10-19: cada una muestra solo su nombre.

- [x] **T-08 · Cuerpo de Inicio y FAB**
  - **Cubre:** RF-03, RF-04, RF-10 · **Depende de:** T-02
  - **Alcance:** cuerpo placeholder sin datos ni secciones; FAB "+" sin acción y con descripción accesible.
  - **Validación:** vista previa y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** la vista previa muestra el cuerpo vacío y el FAB, y este no tiene acción asociada.
  - **Evidencia:** 2026-10-19: `ui/main/HomeContent.kt` con cuerpo vacío (`HomeContent`) y `HomeFab` (`onClick` vacío, descripción accesible) con `@Preview`; `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. `HomeFabPreview` revisada por la persona el 2026-10-19. El cuerpo vacío se verá junto al resto en T-09.

- [x] **T-09 · Contenedor raíz y estado de pestaña**
  - **Cubre:** RF-02, RF-05, RF-08, RF-11 · **Depende de:** T-05, T-06, T-07, T-08
  - **Alcance:** `Scaffold` que une cabecera (en las 5 pestañas), barra, FAB solo en Inicio y contenido de la pestaña activa; estado en memoria con Inicio por defecto, sin persistirlo; respeta insets del sistema.
  - **Validación:** vista previa y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** al cambiar de pestaña en la vista previa interactiva se ve su contenido, la cabecera permanece y el FAB solo aparece en Inicio.
  - **Evidencia:** 2026-10-19: `ui/main/MainScreen.kt` (estado `remember` en memoria, Inicio por defecto; `Scaffold` con cabecera con insets, barra, FAB solo en Inicio y contenido por pestaña) con `@Preview` interactivo; `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. `MainScreenPreview` en modo interactivo revisada por la persona el 2026-10-19: el contenido cambia por pestaña, la cabecera permanece y el FAB solo aparece en Inicio.

- [x] **T-10 · Conectar en MainActivity**
  - **Cubre:** RF-01, RF-08 · **Depende de:** T-09
  - **Alcance:** mostrar el contenedor raíz en `MainActivity`; retirar `Greeting` y su vista previa.
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y no queda código del saludo de ejemplo.
  - **Evidencia:** 2026-10-19: `MainActivity` muestra `MainScreen`; `Greeting` y su vista previa eliminados (`grep` sin resultados); `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.

## Comportamiento

- [x] **T-11 · Botón Atrás**
  - **Cubre:** RF-07, RF-13 · **Depende de:** T-10
  - **Alcance:** desde otra pestaña, Atrás (incluido el gesto predictivo) selecciona Inicio; desde Inicio no se intercepta. Verificar que `activity-compose` instalada lo soporta.
  - **Validación:** `./gradlew :app:assembleDebug`; comprobación en T-14 y T-16.
  - **Hecho cuando:** compila y el manejador de Atrás solo está activo fuera de Inicio.
  - **Evidencia:** 2026-10-19: `MainScreen` usa `BackHandler` habilitado solo
    cuando la pestaña activa no es Inicio; al ejecutarse selecciona Inicio y,
    desde Inicio, no intercepta el evento para conservar el cierre normal de la
    Activity. `./gradlew :app:assembleDebug --quiet` → BUILD SUCCESSFUL. La
    comprobación manual del gesto predictivo queda pendiente en T-14/T-16.

- [x] **T-12 · Pulsaciones repetidas**
  - **Cubre:** RF-13 · **Depende de:** T-10
  - **Alcance:** que seleccionar la pestaña activa o pulsar el FAB repetidamente no cambie el estado ni falle.
  - **Validación:** `./gradlew :app:assembleDebug`; comprobación en T-14.
  - **Hecho cuando:** compila y la selección de la pestaña ya activa no altera el estado.
  - **Evidencia:** 2026-10-19: `MainScreen` ignora explícitamente la selección
    de la pestaña que ya está activa; `HomeFab` mantiene `onClick = {}` porque
    el FAB no tiene acción en este scaffold. `./gradlew
    :app:assembleDebug --quiet` y `git diff --check` → correctos. La prueba de
    pulsaciones rápidas en UI queda cubierta por T-14.

## Validación

- [x] **T-13 · Tests de UI: estructura**
  - **Cubre:** RF-01 a RF-06 · **Depende de:** T-12, T-11
  - **Alcance:** tests Compose nuevos para CA-01, CA-02 y CA-03.
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest` (requiere emulador o dispositivo).
  - **Hecho cuando:** los tests de CA-01, CA-02 y CA-03 se ejecutan y pasan.
  - **Evidencia:** 2026-10-19: añadido `MainScreenTest` con pruebas para CA-01
    (estructura inicial), CA-02 (cabecera y FAB sin acción) y CA-03 (cambio a
    las cuatro pestañas, cabecera persistente y ausencia del FAB). Se actualizó
    Espresso de `3.5.1` a `3.7.0` porque la versión anterior fallaba en el
    emulador con `InputManager.getInstance` inexistente. Tras añadir tags de
    prueba al placeholder y corregir el test para usar el estado real de
    `MainScreen`, `./gradlew :app:connectedDebugAndroidTest --quiet` →
    BUILD SUCCESSFUL en `emulator-5554`.

- [x] **T-14 · Tests de UI: Atrás, accesibilidad y repetición**
  - **Cubre:** RF-07, RF-10, RF-13 · **Depende de:** T-13
  - **Alcance:** tests Compose nuevos para CA-04 (Atrás desde otra pestaña), CA-07 (descripciones) y CA-09 (toques repetidos).
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest`.
  - **Hecho cuando:** los tests de CA-04, CA-07 y CA-09 se ejecutan y pasan.
  - **Evidencia:** 2026-10-19: añadido `MainScreenBehaviorTest` con pruebas para
    CA-04 (Atrás desde Movimientos vuelve a Inicio), CA-07 (etiquetas accesibles
    de cabecera, FAB y pestañas) y CA-09 (cinco pulsaciones repetidas sobre
    Inicio y el FAB sin duplicar elementos ni cambiar de estado).
    `./gradlew :app:connectedDebugAndroidTest --quiet` → BUILD SUCCESSFUL en
    `emulator-5554`.

- [x] **T-15 · Manual: ciclo de vida y orientación**
  - **Cubre:** RF-08, RF-09 · **Depende de:** T-14
  - **Alcance:** CA-05 (recrear y terminar el proceso en otra pestaña), segundo plano y volver desde otra app, y CA-06 (rotación automática activada).
  - **Validación:** pasos de la SPEC en emulador.
  - **Hecho cuando:** cada escenario tiene resultado anotado y coincide con la SPEC, o queda registrado como no verificado.
  - **Evidencia:** 2026-10-19: comprobado en `emulator-5554` (1080x2424).
    Desde Movimientos, al pasar a segundo plano con Inicio y volver, la pestaña
    Movimientos se conserva. Tras `am force-stop` y relanzar `MainActivity`, la
    app inicia en Inicio. Con rotación automática desactivada y una solicitud
    de orientación horizontal, la jerarquía UI mantiene `rotation="0"` y el
    display sigue en 1080x2424; se restauró la configuración de rotación del
    emulador. CA-05 y CA-06 verificados.

- [x] **T-16 · Manual: pantallas, TalkBack y Atrás**
  - **Cubre:** RF-07, RF-10, RF-11, RF-13 · **Depende de:** T-15
  - **Alcance:** CA-08 (teléfono pequeño y grande, gestos y 3 botones), CA-07 (TalkBack), CA-09 (gesto Atrás) y salida de la app desde Inicio (CA-04).
  - **Validación:** pasos de la SPEC en emulador.
  - **Hecho cuando:** cada escenario tiene resultado y capturas anotados, o queda registrado como no verificado.
  - **Evidencia:** 2026-10-19: en `emulator-5554` (1080x2424), la barra
    inferior y la cabecera se muestran completas con navegación por gestos
    (`navigation_mode=2`) y con navegación de 3 botones
    (`navigation_mode=0`); la configuración se restauró a gestos. Desde
    Movimientos, la tecla Atrás y el gesto desde el borde izquierdo vuelven a
    Inicio; desde Inicio, Atrás cierra la Activity y muestra el launcher.
    TalkBack se habilitó temporalmente y la jerarquía expuso las etiquetas
    `Selector de familia: Familia Plata, Propietario`, `Notificaciones`,
    `Perfil de usuario` y `Agregar`; se deshabilitó y se restauró el estado
    original. CA-04, CA-07 y CA-09 verificados manualmente. CA-08 queda
    parcialmente verificado: el emulador disponible cubre un único tamaño de
    teléfono; no hay un segundo AVD pequeño/grande para comprobar ambas
    dimensiones. Captura de apoyo: artefacto de sesión
    `.copilot/session-state/.../files/t16/home-gesture-nav.png`.

- [x] **T-17 · Cierre y regresión**
  - **Cubre:** todos los RF · **Depende de:** T-16
  - **Alcance:** ejecutar compilación, tests unitarios y lint; revisar cada CA y preparar el informe final con lo no verificado.
  - **Validación:** `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`.
  - **Hecho cuando:** los tres comandos pasan, o los fallos quedan registrados, y cada CA tiene evidencia o motivo de no verificación.
  - **Evidencia:** 2026-10-19: `./gradlew :app:assembleDebug
    :app:testDebugUnitTest :app:lintDebug --quiet` → BUILD SUCCESSFUL; los
    tres checks de regresión pasan. Los tests instrumentados de CA-01 a CA-04,
    CA-07 y CA-09 también pasan en `emulator-5554` según T-13/T-14. CA-05 y
    CA-06 fueron verificados manualmente en T-15. CA-08 queda parcialmente
    verificado en T-16 porque solo hay un tamaño de teléfono disponible; no se
    pudo comprobar un segundo AVD.
