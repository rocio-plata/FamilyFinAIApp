# TASKS: Dashboard

**SPEC:** `docs/features/008-dashboard/SPEC.md` (Aprobada)
**PLAN:** `docs/features/008-dashboard/PLAN.md` (Aprobado)

<!-- Tareas de máx. 20-30 min, en orden de dependencia. Marcar [x] solo tras
ejecutar la validación y registrar en "Evidencia" el resultado real. Si una
tarea queda bloqueada, anotar el motivo en "Evidencia". -->

Rutas relativas a `app/src/main/java/com/rocioplata/familyfinaiapp/` salvo
indicación. Los tests JVM van en `app/src/test/…` y los de UI en
`app/src/androidTest/…`, en el mismo paquete que lo que prueban.

## Color de categoría en datos

- [x] **T-01 · Color en el modelo y en la red**
  - **Cubre:** RF-14, RF-15 (CA-13, CA-14) y BG-01 · **Depende de:** —
  - **Alcance:** `domain/categories/Category.kt` añade `color: String? = null`; `CategoriesRepository.kt` añade `ColorUpdate` (`Unchanged`/`Remove`/`Set`, como `IconUpdate`), `color` en `createCategory` y `color: ColorUpdate` en `updateCategory`. `data/categories/CategoriesRemoteDataSource.kt` lee `color` del DTO, envía `color` en el alta solo si lo hay y `newColor` (cadena o `null`) en la edición solo si cambia. Se adaptan las llamadas existentes (`CategoryEditViewModel` pasa `null` y `ColorUpdate.Unchanged` hasta T-04) y los falsos de los tests (`Fakes.kt` de movimientos y los de categorías).
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.CategoriesRemoteDataSourceTest"` con `ktor-client-mock`: color leído, ausente y nulo; alta con y sin color; edición con `Set`, `Remove` (envía `null` explícito) y `Unchanged` (no envía `newColor`); `400` con `FINANCIAL_TRACKING.INVALID_CATEGORY_COLOR` → error genérico. `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan y la app compila sin cambios visibles.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.CategoriesRemoteDataSourceTest"` → BUILD SUCCESSFUL; 49 tests (12 nuevos), 0 fallos, 0 errores, 0 omitidos (leído de `app/build/test-results/testDebugUnitTest`, de esta ejecución). Regresión: `./gradlew :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL; suite JVM completa: 997 tests, 0 fallos, 0 errores, 0 omitidos. No se ejecutaron lint ni los tests instrumentados (T-15); ningún archivo de `androidTest` usa `createCategory`/`updateCategory`, pero tampoco se compiló esa variante. Cambios: `Category.color: String? = null` (al **final** del constructor, para no tocar los ~35 sitios que construyen `Category` por posición); `ColorUpdate` (`Unchanged`/`Remove`/`Set`) en `CategoriesRepository.kt`, con `color` en `createCategory` y `updateCategory` (valores por defecto en la interfaz y en el data source); `CategoriesRemoteDataSource` lee `color` del DTO, envía `color` en el alta solo si lo hay y `newColor` (cadena o `null` explícito) en la edición solo si cambia. Tests nuevos: color leído, ausente, nulo y desconocido (se conserva tal cual); alta con y sin color; edición con `Set`, `Remove`, `Unchanged`, y nombre, icono y color juntos; `400` con `FINANCIAL_TRACKING.INVALID_CATEGORY_COLOR` → `Unknown` (alta y edición). **Desviaciones a tener en cuenta:** (1) `CategoriesRepositoryImpl` ya reenvía el color al remoto porque cambió la interfaz, pero **todavía no lo aplica a memoria ni a caché** (es T-02); hasta entonces nada en la app pasa un color. (2) `CategoryEditViewModel` no necesitó cambios: los valores por defecto mantienen sus llamadas; el color llega en T-04. (3) Se actualizaron las firmas de los tres repositorios falsos (`Fakes.kt`, `CategoriesViewModelTest`, `CategoryEditViewModelTest`) sin cambiar su comportamiento. **Sin verificar aquí:** el comportamiento contra el backend real (T-16).
- [x] **T-02 · Color en la caché y en el repositorio**
  - **Cubre:** RF-14, RF-15 (CA-13, CA-14) · **Depende de:** T-01
  - **Alcance:** `data/categories/CategoriesLocalDataSource.kt` guarda `color` como campo opcional (por defecto `null`) sin cambiar `schemaVersion`; `CategoriesRepositoryImpl.kt` pasa el color al remoto y, tras el éxito, lo aplica a memoria y caché como el icono.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.*"`: ida y vuelta con color; una copia anterior sin el campo se lee sin color; alta con color y edición `Set`/`Remove` aplicadas al flujo y a la caché; fallo y `Offline` sin aplicar nada. Más `session.SessionEndDuringCategoriesRefreshTest` como regresión.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "…data.categories.*" --tests "…session.SessionEndDuringCategoriesRefreshTest"` → BUILD SUCCESSFUL. Después `./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun` → BUILD SUCCESSFUL; suite JVM completa: 1004 tests (7 nuevos respecto de T-01), 0 fallos, 0 errores, 0 omitidos (suma de `app/build/test-results/testDebugUnitTest`, de esta ejecución). Por clase: `CategoriesLocalDataSourceTest` 15 (2 nuevos), `CategoriesRepositoryImplTest` 47 (5 nuevos), `CategoriesRemoteDataSourceTest` 49 y `SessionEndDuringCategoriesRefreshTest` 1. No se ejecutaron lint ni los tests instrumentados (T-15). Cambios: `CategoriesLocalDataSource` guarda `color` como campo opcional del JSON (por defecto `null`), sin cambiar `schemaVersion`; `CategoriesRepositoryImpl.updateCategory` aplica `ColorUpdate` (`Unchanged` conserva, `Remove` pone `null`, `Set` guarda la clave) a memoria y caché, conservando el `savedAt` de la descarga; el alta con color ya se guardaba porque la categoría creada llega del backend con su `color` (T-01). Tests nuevos: ida y vuelta con color (clave conocida, desconocida y sin color); copia anterior sin el campo se lee sin color con `schemaVersion = 1`; alta con color aplicada a flujo y caché; edición `Set` y `Remove` aplicadas a flujo y caché; editar nombre e icono sin tocar el color lo conserva; color rechazado (`400`) y sin conexión no cambian nada y `Offline` no llama al backend; la descarga con color lo expone y lo guarda. **Cambio en un test existente (no es un test relajado):** `camposNuevosEnElJson_seIgnoran` usaba `"color":"x"` como ejemplo de campo desconocido; ahora `color` es un campo conocido, así que el ejemplo pasó a `"futuro":"x"` y la intención del test (un campo que la app no conoce se ignora) se mantiene. **Sin verificar aquí:** el comportamiento con el backend real (T-16).

## Paleta

- [x] **T-03 · Paleta y catálogo de colores**
  - **Cubre:** RF-14 (CA-13) · **Depende de:** T-01
  - **Alcance:** `ui/theme/Color.kt` con los 16 colores claro/oscuro de la tabla del plan; `ui/categories/CategoryColors.kt` (crear): opciones por clave con nombre (`@StringRes`) y colores claro/oscuro, en el orden del plan; `colorFor(clave, posición, oscuro)` devuelve el de la clave conocida o el de `posición mod 16`; función que calcula la posición de una categoría entre las de su tipo en la lista del repositorio. Nombres en `strings.xml`. `docs/DESIGN.md`: sección "Category Accents" con la paleta definitiva.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.categories.CategoryColorsTest"`: 16 claves únicas y en el orden del plan; clave conocida; nula y desconocida usan la posición; posiciones 0, 15, 16 y 17; la posición solo cuenta categorías del mismo tipo y no cambia por las de otro tipo; contraste de cada color ≥ 3:1 contra `AppSurface` y contra la superficie oscura (cálculo WCAG en el test).
  - **Hecho cuando:** los tests pasan y `DESIGN.md` coincide con `Color.kt`.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.categories.CategoryColorsTest"` → 14 tests; la primera ejecución falló en uno por un error del propio test (`assertSame` sobre `Color`, que es una clase de valor y se compara por referencia al cajearse); se cambió por `assertEquals` y pasó. Después `./gradlew :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL; suite JVM completa: 1018 tests (14 nuevos respecto de T-02), 0 fallos, 0 errores, 0 omitidos (suma de `app/build/test-results/testDebugUnitTest`, de esta ejecución). No se ejecutaron lint ni los tests instrumentados (T-15). Archivos: `ui/theme/Color.kt` (32 valores `Category<Nombre>Light/Dark`), `ui/categories/CategoryColors.kt` (nuevo: `CategoryTone` claro/oscuro, `CategoryColorOption`, `CategoryColors.options` con las 16 claves en el orden del plan, `find`, `fallback(posición)` cíclico con `floorMod`, `resolve(clave, posición)`, `positionOf(categoría, lista)` y `resolve(categoría, lista)`), 16 cadenas `categories_color_<clave>` en `strings.xml` y la sección «Category Accents» de `docs/DESIGN.md` con la tabla definitiva. Tests: 16 claves únicas, en minúsculas y en el orden del plan; nombres únicos; versión clara y oscura distintas y sin repetirse; **contraste ≥ 3:1 de cada color con `AppSurface` (claro) y con `#263331` (oscuro)**, calculado con la fórmula WCAG; clave conocida gana a la posición; nula, vacía, desconocida o en mayúsculas usa la posición; respaldo en las posiciones 0, 15, 16, 17 y −1; la posición solo cuenta categorías del mismo tipo y no cambia por las del otro; archivar una categoría no cambia el color de las demás; categoría fuera de la lista cuenta como la primera; con más de 16 categorías el color se repite de forma cíclica. Comprobación adicional con un script: la tabla de `DESIGN.md` (16 filas, claves, orden y valores claro/oscuro) coincide con `Color.kt` y con `CategoryColors.options`. **Decisiones a tener en cuenta:** (1) la superficie oscura del test (`#263331`) está duplicada porque `ColorInverseSurface` es privado en `Theme.kt`; queda un comentario que lo señala. (2) `positionOf` cuenta también las categorías archivadas, para que archivar una no cambie el color de las demás. (3) Los valores son los del plan; el ajuste en el emulador (que dos colores vecinos como Salvia y Turquesa se distingan) queda para T-05 y T-16, cuando haya pantalla. **Sin verificar aquí:** el aspecto real en claro y oscuro.

## Color en la gestión de categorías

- [ ] **T-04 · Color en el ViewModel de edición**
  - **Cubre:** RF-15 (CA-14) · **Depende de:** T-02, T-03
  - **Alcance:** `ui/categories/CategoryEditViewModel.kt` y `CategoryEditUiState.kt`: el borrador incluye `color`; `setColor(clave?)` (ignorado en solo lectura u ocupado); `hasUnsavedChanges` cuenta el color; el alta lo envía y la edición manda `ColorUpdate` solo si cambió. El estado expone el color elegido y el color efectivo (con respaldo) para la vista previa.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.categories.CategoryEditViewModelTest"`: elegir y guardar (alta y edición); quitar el color; solo color cambiado → se puede guardar y solo se envía el color; `Offline` informa y conserva el borrador; Integrante y archivada no cambian el color; salir con el color cambiado pide confirmación y sin cambios no.
  - **Hecho cuando:** los tests nuevos y existentes pasan.
  - **Evidencia:** —
- [ ] **T-05 · Selector de color y listado con color**
  - **Cubre:** RF-14, RF-15 (CA-13, CA-14) · **Depende de:** T-04
  - **Alcance:** `ui/categories/CategoryEditContent.kt`: fila desplazable con los 16 colores (círculo y nombre) sobre la cuadrícula de iconos, el elegido marcado también sin depender del color, cada uno anunciado por su nombre y estado; vista previa e iconos del catálogo en el color elegido; deshabilitada en solo lectura. `CategoriesContent.kt`: el icono de cada categoría en su color (fondo suave derivado).
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.rocioplata.familyfinaiapp.ui.categories` con `CategoryEditContentTest` (elegir un color llama al callback; el elegido aparece seleccionado en la semántica; solo lectura sin acción) y regresión de `CategoriesContentTest`.
  - **Hecho cuando:** los tests pasan; captura del selector en claro y oscuro.
  - **Evidencia:** —

## Cálculo del dashboard

- [ ] **T-06 · Totales, disponible y porcentaje**
  - **Cubre:** RF-02, RF-05, RF-13 (CA-03, CA-06, CA-15) · **Depende de:** —
  - **Alcance:** `domain/dashboard/` (crear): modelo del resumen y calculador puro (sin Android ni Compose). Totales por tipo con todos los movimientos del mes; disponible = ingresos − gastos; porcentaje gastos/ingresos con `BigDecimal` y redondeo `HALF_UP` a un decimal (100 % en ingresos, nulo sin ingresos); monedas mezcladas se señalan como datos no utilizables.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.domain.dashboard.*"`: datos de la spec (1.850.000 / 1.120.450 → disponible 729.550 y 60,6 %); disponible negativo; sin ingresos sin porcentaje; mes vacío en 0; USD con centavos; monedas mezcladas. `grep` sin imports de `android`/`androidx` en `domain/dashboard`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-07 · Categorías del tipo activo y gráfico**
  - **Cubre:** RF-04, RF-07 a RF-09 (CA-05, CA-09, CA-15) · **Depende de:** T-06
  - **Alcance:** en el calculador, la lista de categorías del tipo activo: activas en el orden del repositorio (también en $0), archivadas con monto > 0 al final, contador de mostradas; cada entrada con su monto, estado, posición para el color y su proporción en el gráfico; segmentos solo de las de monto > 0. Un movimiento de una categoría que no está en la lista cuenta en los totales y no aparece.
  - **Validación:** `DashboardCalculatorTest`: orden; $0 incluidas; archivada con y sin movimientos; contador; solo las del tipo activo; segmentos y proporciones; categoría desconocida (totales ≠ suma de la cuadrícula); sin categorías del tipo.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-08 · Comparación con el mes anterior**
  - **Cubre:** RF-06 (CA-07, CA-08) · **Depende de:** T-06
  - **Alcance:** en el calculador, la variación del tipo activo: tramo de días del mes en curso (día ≤ hoy, y en el anterior ≤ `min(hoy, longitud)`; si hoy supera la longitud del mes anterior, el mes completo); meses completos para un mes pasado; un decimal `HALF_UP`; nula con base 0 o sin datos del mes anterior; dirección (sube, baja, sin cambio si redondea a 0,0) y si es favorable según el tipo.
  - **Validación:** `DashboardCalculatorTest`: −4,8 % (95.200 vs 100.000) como baja favorable en gastos y desfavorable en ingresos; subida; base 0; anterior nulo; hoy día 10 (movimientos del anterior antes y después del 10); hoy 31 tras un mes de 30; 29, 30 y 31 de marzo frente a febrero de 28 y de 29 días; redondeo a 0,0 → sin cambio.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Estado del dashboard

- [ ] **T-09 · `DashboardViewModel`**
  - **Cubre:** RF-01, RF-03, RF-06, RF-13 (CA-01, CA-02, CA-04, CA-07, CA-12, CA-15 a CA-17) · **Depende de:** T-07, T-08
  - **Alcance:** `ui/dashboard/DashboardViewModel.kt` y `DashboardUiState.kt` (crear), con el patrón de `MovementsViewModel`: mes (en curso al crear; anterior, siguiente bloqueado en el mes en curso y "Mes actual"), tipo activo (gastos al crear), `flatMapLatest` sobre el mes mostrado y su anterior, categorías del repositorio, `refreshJobs` por mes; refresco del mes, del anterior y de categorías en `init`, `onStart`, `retry`, al cambiar de mes y al recuperar conexión; `Loading`/`Error`/`Content` con `isStale` e `isRefreshing`; `NotAllowed` → `onInsufficientRole`. Recibe familia, moneda, `MovementDates` y `ConnectivityObserver`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.dashboard.DashboardViewModelTest"` con `Fakes.kt` y reloj fijo: navegación y límite; cambio de tipo; refrescos pedidos (mes y anterior, sin duplicar en curso); caché sin red; `isStale`; sin caché y fallo → `Error`; reintento; fallo solo del anterior → sin variación y resto visible; el falso emite un mes cambiado → el resumen cambia (CA-12); el estado no depende de la pantalla; `NotAllowed`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Interfaz del dashboard

- [ ] **T-10 · Textos y formatos**
  - **Cubre:** RF-01, RF-04 a RF-07 (CA-04 a CA-07, CA-09) · **Depende de:** T-06
  - **Alcance:** `ui/dashboard/DashboardTexts.kt` (crear) y `strings.xml`: porcentaje con un decimal y formato regional ("60,6 %"); texto de la variación con signo y "vs mes anterior", y su descripción accesible ("bajó 4,8 % respecto del mes anterior"); descripción del gráfico (tipo, total y reparto por categoría); contador ("9 categorías", con plurales); títulos "Desglose de gastos/ingresos" y "Gastos/Ingresos por categoría"; "Disponible", "Mes actual", "Archivada", mes vacío, error y "Reintentar".
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.dashboard.DashboardTextsTest"` en `es-CL` y `en-US`: porcentajes, signo negativo, 0,0, plurales del contador y descripción del gráfico.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-11 · Selector de mes, desglose y gráfico**
  - **Cubre:** RF-01 a RF-06, RF-14 (CA-01, CA-03 a CA-07) · **Depende de:** T-03, T-09, T-10
  - **Alcance:** `ui/dashboard/DonutChart.kt` (crear): anillo con `Canvas` y `drawArc`, segmentos con el color de cada categoría, anillo vacío sin montos, total y porcentaje en el centro y una sola descripción accesible. `DashboardContent.kt` (crear, stateless): selector de mes (‹, mes, › deshabilitado en el mes en curso) y "Mes actual"; tarjeta de desglose con las tarjetas Gastos e Ingresos (la activa resaltada y anunciada como seleccionada, la otra tocable), "Disponible", indicador de variación (verde favorable, rojo desfavorable, neutro sin cambio; oculto si es nulo) y el gráfico. Colores del tema, sin hexadecimales en la pantalla.
  - **Validación:** `DashboardContentTest` (`androidTest`): botones de mes y su estado; tocar la tarjeta no activa llama al callback; selección en la semántica; variación visible con texto y oculta si es nula; sin porcentaje sin ingresos; descripción del gráfico. Previews en claro y oscuro.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-12 · Sección por categoría y estados**
  - **Cubre:** RF-07 a RF-11, RF-14 (CA-09, CA-11, CA-13, CA-15, CA-16) · **Depende de:** T-11
  - **Alcance:** en `DashboardContent.kt`: título según el tipo, contador y "Gestionar"; cuadrícula adaptativa (ancho mínimo por celda, 3 columnas en un teléfono normal) con icono en su color, nombre en hasta dos líneas y monto; las de $0 atenuadas; las archivadas con "Archivada" y sin acción; las activas llaman al callback con su id y tipo; cada tarjeta es una unidad para el lector de pantalla. Estados: carga, error con "Reintentar", mes vacío con la invitación a usar "+", sin categorías del tipo y aviso de datos de más de 24 h (`StaleDataBanner` o equivalente). Espacio inferior para el botón "+".
  - **Validación:** `DashboardContentTest`: orden y contador mostrados; $0 y archivada; tocar activa → callback con id y tipo; tocar archivada → nada; "Gestionar"; carga, error y "Reintentar", vacío y aviso; texto al 200 % sin cortes (fuente aumentada en el test).
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Integración en Inicio

- [ ] **T-13 · Alta con categoría elegida**
  - **Cubre:** RF-11 (CA-11) · **Depende de:** —
  - **Alcance:** `ui/main/MovementRoute.kt`: `Create` pasa a `data class Create(val categoryId: String? = null, val type: MovementType? = null)`; `MainViewModel.openMovementCreate(categoryId, type)` (sin argumentos sigue siendo el "+"); `MainScreen` y `managementBack` usan `is MovementRoute.Create`. `MovementFormViewModel` y `MovementFormScreen` reciben `initialType` e `initialCategoryId`: los campos parten de ellos, la fecha es hoy, y `hasChanges` del alta compara con esos valores; si la categoría ya no está activa al cargar, el formulario queda sin categoría.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.main.MainViewModelTest" --tests "com.rocioplata.familyfinaiapp.ui.movements.MovementFormViewModelTest"`: ruta con categoría y tipo; "+" sigue sin ellos; formulario con tipo ingreso, categoría y hoy; cerrar sin cambios no pide confirmación y cambiar algo sí; categoría archivada antes de cargar; guardar crea con esa categoría. Regresión de `MainScreenTest` y `MainScreenBehaviorTest`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-14 · Inicio con el dashboard**
  - **Cubre:** RF-01, RF-10, RF-12 (CA-02, CA-10, CA-17, CA-18) · **Depende de:** T-12, T-13
  - **Alcance:** `ui/dashboard/DashboardScreen.kt` (crear): entrada con estado que crea el ViewModel con la clave recibida y llama a `onStart` en `ON_START`. `MainViewModel`/`MainUiState`: `activeFamilyInstance`, que cambia cada vez que cambia la familia activa (también al volver a una anterior). `HomeContent.kt`: contenedor desplazable con el hueco del dashboard y debajo "Medios de pago"; sin la fila "Categorías" ni el texto provisional; el "Gestionar" de categorías conserva la etiqueta `home_manage_categories`. `MainScreen.kt`: hueco `homeDashboardContent` en la sobrecarga stateless y, en la entrada con estado, `DashboardScreen` con clave `main-<generación>-dashboard-<familia>-<activeFamilyInstance>` y callbacks a `openMovementCreate`, `openCategories` y `onInsufficientRole`. `TestMainHost` se adapta.
  - **Validación:** `MainViewModelTest` (`activeFamilyInstance` con A → B → A, sin cambio al refrescar la cabecera); `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.rocioplata.familyfinaiapp.ui.main` (`MainScreenTest`: sin sección "Categorías", ambos "Gestionar", "Medios de pago" debajo del dashboard).
  - **Hecho cuando:** los tests pasan y la app muestra el dashboard en Inicio.
  - **Evidencia:** —

## Validación

- [ ] **T-15 · Comprobaciones automáticas completas**
  - **Cubre:** todos · **Depende de:** T-05, T-14
  - **Alcance:** suite completa, incluida la regresión de cabecera, categorías, medios de pago, movimientos, sesión, `MainScreenTest`, `MainScreenBehaviorTest` y `AppRootTest`. Lint sin avisos nuevos respecto de la línea base de la 007 (24 avisos): cada cadena nueva se usa, sin `tools:ignore`.
  - **Validación:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` y `./gradlew :app:connectedDebugAndroidTest`; lectura de `app/build/reports/lint-results-debug.xml`.
  - **Hecho cuando:** todo pasa o cada fallo queda registrado con su causa, y lint no supera la línea base.
  - **Evidencia:** —
- [ ] **T-16 · Pruebas manuales contra el backend**
  - **Cubre:** CA-01 a CA-18 · **Depende de:** T-15
  - **Alcance:** emulador con el backend local, solo con datos de prueba creados para ello y eliminados al terminar: navegación de meses y "Mes actual"; mes de Inicio independiente del de Movimientos; gastos/ingresos; totales, porcentaje y variación comparados con el resumen de Movimientos de los dos meses; alta desde una categoría (activa y archivada); alta, edición y borrado reflejados; elegir, cambiar y quitar el color de una categoría (y sin conexión); categoría sin color con respaldo; modo avión (mes visto y no visto, y reconexión); giro, segundo plano, cambio de pestaña y proceso terminado (`adb shell am kill`); cambio de familia si hay dos; modo oscuro, texto al 200 % y TalkBack si es posible.
  - **Validación:** pasos manuales registrados con el resultado observado y capturas.
  - **Hecho cuando:** cada escenario tiene resultado o motivo de bloqueo.
  - **Evidencia:** —
- [ ] **T-17 · Documentación y revisión final**
  - **Cubre:** CA-01 a CA-18 · **Depende de:** T-16
  - **Alcance:** `AGENTS.md` (dashboard: cálculo en la app, `DashboardViewModel` y su clave por instancia de familia; color de categoría y `CategoryColors`; `MovementRoute.Create` con categoría; Inicio con el hueco del dashboard), `docs/ROADMAP.md` (estado y notas), `BACKEND_GAPS.md` (lo observado en ejecución) y coherencia entre SPEC, PLAN y TASKS.
  - **Validación:** revisión documental: cada criterio con evidencia o marcado como no verificado; rutas y nombres citados existen.
  - **Hecho cuando:** documentos coherentes con lo implementado.
  - **Evidencia:** —

## Cobertura de criterios

| Criterio | Tareas |
| --- | --- |
| CA-01 | T-09, T-11, T-16 |
| CA-02 | T-09, T-14, T-16 |
| CA-03 | T-06, T-11, T-16 |
| CA-04 | T-09, T-10, T-11, T-16 |
| CA-05 | T-07, T-10, T-11, T-16 |
| CA-06 | T-06, T-10, T-11 |
| CA-07 | T-08, T-09, T-10, T-11, T-16 |
| CA-08 | T-08 |
| CA-09 | T-07, T-10, T-12, T-16 |
| CA-10 | T-14, T-16 |
| CA-11 | T-12, T-13, T-16 |
| CA-12 | T-09, T-16 |
| CA-13 | T-01 a T-03, T-05, T-12, T-16 |
| CA-14 | T-01, T-02, T-04, T-05, T-16 |
| CA-15 | T-06, T-07, T-09, T-12, T-16 |
| CA-16 | T-09, T-12, T-16 |
| CA-17 | T-09, T-14, T-16 |
| CA-18 | T-14, T-16 |

## Estado de verificación por criterio

<!-- Se completa en T-17 con lo realmente ejecutado. -->

| Criterio | JVM | UI | Manual | Estado |
| --- | :-: | :-: | :-: | --- |
| CA-01 a CA-18 | | | | Pendiente |
