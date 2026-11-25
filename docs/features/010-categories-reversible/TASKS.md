# TASKS: Categorías reversibles y ordenables

**SPEC:** `docs/features/010-categories-reversible/SPEC.md` (Aprobada)
**PLAN:** `docs/features/010-categories-reversible/PLAN.md` (Aprobado)

<!-- Tareas de máx. 20-30 min, en orden de dependencia. Marcar [x] solo tras
ejecutar la validación y registrar en "Evidencia" el resultado real. Si una
tarea queda bloqueada, anotar el motivo en "Evidencia". -->

Rutas relativas a `app/src/main/java/com/rocioplata/familyfinaiapp/` salvo
indicación. Los tests JVM van en `app/src/test/…` y los de UI en
`app/src/androidTest/…`, en el mismo paquete que lo que prueban.

**Dependencia externa:** el backend con el PR #163 (BG-01, BG-03 y BG-04 de
`005-categories/BACKEND_GAPS.md`) para las pruebas manuales (T-15). Lo que no
coincida con lo documentado se anota en un `BACKEND_GAPS.md` de esta
funcionalidad y se consulta antes de adaptar la app.

## Datos

- [x] **T-01 · Posición y fecha de archivado en el modelo y el remoto**
  - **Cubre:** RF-09, RF-10 (CA-13) · **Depende de:** —
  - **Alcance:** `domain/categories/Category.kt`: `displayOrder: Int = 0` y `deprecatedAt: Instant? = null`; corregir el comentario de `CategoryStatus` (archivar ya tiene inversa). `CategoriesRemoteDataSource`: `CategoryDto` lee `displayOrder` (por defecto 0) y `deprecatedAt`; una fecha ilegible se trata como ausente.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.CategoriesRemoteDataSourceTest"`: campos presentes, ausentes, `deprecatedAt` nulo e ilegible (la lista se lee igual). `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan y la app compila sin cambios visibles.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "…data.categories.*" …` → BUILD SUCCESSFUL; `CategoriesRemoteDataSourceTest` 52 (3 nuevos: lee posición y fecha, con `Z` y con desfase; sin los campos quedan en 0 y sin fecha; fecha ilegible se trata como ausente y la lista se lee igual), 0 fallos (leído de `app/build/test-results/testDebugUnitTest`, de esta ejecución). `Category` gana `displayOrder: Int = 0` y `deprecatedAt: Instant? = null` al final, así que las construcciones existentes no cambian; la fecha se lee con `OffsetDateTime.parse` (acepta `Z` y desfases). Comentario de `CategoryStatus` corregido. `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. Sin cambios visibles.
- [x] **T-02 · Campos nuevos en la caché**
  - **Cubre:** RF-08 (CA-12) · **Depende de:** T-01
  - **Alcance:** `CategoriesLocalDataSource`: `displayOrder` y `deprecatedAt` (epoch ms) opcionales en `CategoryJson`, sin subir `schemaVersion`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.CategoriesLocalDataSourceTest"`: ida y vuelta con los campos y copia anterior sin ellos (posición 0, sin fecha).
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** Misma ejecución: `CategoriesLocalDataSourceTest` 20 (2 nuevos: ida y vuelta con posición y fecha con milisegundos; copia anterior sin los campos se lee con posición 0, sin fecha y en el orden guardado). La copia anterior sin color (test existente) también se sigue leyendo. `CategoryJson` gana `displayOrder` y `deprecatedAtMillis` opcionales; `schemaVersion` sigue en 1.
- [x] **T-03 · Orden estable en el repositorio**
  - **Cubre:** RF-10, RF-11 · **Depende de:** T-02
  - **Alcance:** `List<Category>.inDisplayOrder()` en el dominio (orden estable por `displayOrder`, sin mover las archivadas al final). `CategoriesRepositoryImpl` lo aplica tras cada descarga, lectura de caché y cambio aplicado. `deprecateCategory` fija `deprecatedAt` con el reloj del repositorio.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.*"`: lista desordenada sale ordenada; empates (todas en 0) conservan el orden del backend; archivar fija la fecha y no mueve la categoría. Regresión: `ui.categories.CategoryColorsTest` (archivar no cambia el color de las demás), `domain.dashboard.*`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** Misma ejecución: `CategoriesRepositoryImplTest` 58 (4 nuevos: la descarga se ordena por posición y los empates conservan el orden del backend, sin posiciones se conserva el orden del backend, una caché desordenada se muestra ordenada, archivar fija la fecha del dispositivo en memoria y caché y no mueve la categoría). Regresión: `CategoryColorsTest` 14, `DashboardCalculatorTest` 31, `CategoryBreakdownTest` 20, `MonthComparisonTest` 21 y `SessionEndDuringCategoriesRefreshTest` 1, 0 fallos. Después, `./gradlew :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL; suite JVM completa: 1557 tests, 0 fallos, 0 errores, 0 omitidos (suma de `app/build/test-results/testDebugUnitTest`). `inDisplayOrder()` está en `domain/categories/Category.kt` y el repositorio lo aplica al descargar, al leer la caché y tras cada cambio. No se ejecutó `lintDebug` ni la suite instrumentada en esta sección.

## Operaciones

- [x] **T-04 · Llamadas remotas de reactivar y reordenar**
  - **Cubre:** RF-01, RF-04, RF-10 · **Depende de:** T-01
  - **Alcance:** `CategoriesRemoteDataSource`: `reactivateCategory`, `reactivateTag` (`POST …/reactivate`, `204`) y `reorderCategories` (`PUT …/categories/order` con `orderedCategoryIds`, `204`).
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.CategoriesRemoteDataSourceTest"`: ruta, método y cuerpo; `204`; `404` → `NotFound`; `FAMILY.INSUFFICIENT_ROLE` → `InsufficientRole`; `400 INVALID_CATEGORY_ORDER` → `Unknown`; error de red → `Network`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL; `CategoriesRemoteDataSourceTest` 57 (5 nuevos: `POST …/categories/:id/reactivate` y `…/tags/:id/reactivate` con `204`; errores de reactivar (`404` de categoría y de tag → `NotFound`, `FAMILY.INSUFFICIENT_ROLE`, red, un `200` en vez de `204` → `Unknown`); `PUT /families/:id/categories/order` con `orderedCategoryIds`; errores de ordenar (`400 INVALID_CATEGORY_ORDER` → `Unknown`, `404`, rol, red, cancelación relanzada)), 0 fallos (leído de `app/build/test-results/testDebugUnitTest`, de esta ejecución).
- [x] **T-05 · Reordenar categorías en el repositorio**
  - **Cubre:** RF-10, RF-12 (CA-14, CA-16, CA-18) · **Depende de:** T-03, T-04
  - **Alcance:** `CategoriesRepository.reorderCategories(familyId, type, activeIdsInOrder)` por `change()`: envía solo las activas del tipo y, al confirmarse, aplica la regla del backend (las enviadas en 0..n-1, el resto del tipo detrás en su orden previo, el otro tipo sin cambios). Los fakes de `CategoriesRepository` (`ui/movements/Fakes.kt`, `CategoriesViewModelTest`, `CategoryEditViewModelTest`) implementan el método.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.CategoriesRepositoryImplTest"`: cuerpo enviado; memoria y caché con el nuevo orden y `savedAt` conservado; archivadas detrás; otro tipo intacto; sin conexión no hay petición; fallo sin cambios. `./gradlew :app:testDebugUnitTest` (compilan los fakes).
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** Misma ejecución: `CategoriesRepositoryImplTest` 70 (3 nuevos para ordenar: envía solo las dadas y aplica la regla del backend (enviadas primero, archivada después en su orden previo, el otro tipo intacto, memoria = caché, `savedAt` conservado); un refresco con lo que devolvería el backend no mueve nada; rechazado o sin conexión no cambia nada y sin conexión no hay petición). `CategoriesRepository.reorderCategories(familyId, type, activeCategoryIdsInOrder)`; la regla local es `withOrder` en `CategoriesRepositoryImpl`. Los fakes (`ui/movements/Fakes.kt`, `CategoriesViewModelTest`, `CategoryEditViewModelTest`) implementan los métodos nuevos sin uso (los de los ViewModels se completan en T-08 y T-12).
- [x] **T-06 · Reactivar en el repositorio**
  - **Cubre:** RF-01, RF-04, RF-06 a RF-08, RF-11 (CA-08, CA-10, CA-12, CA-17) · **Depende de:** T-05
  - **Alcance:** `ReactivationResult` (`Success`, `Partial(orderFailure)`, `Failure`) en el dominio. `reactivateCategory`: reactivar por `change()` (estado activo, sin fecha), y si no queda ya como la última activa del tipo, `reorderCategories` con ella al final. `reactivateTag`: lo mismo con `reorderTags`. Fakes actualizados.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.CategoriesRepositoryImplTest"`: 3 activas + 1 archivada → la cuarta (cuerpo del `PUT`); única activa o ya última → sin segunda llamada; orden falla → `Partial` con la reactivación ya en memoria y caché; reactivar falla (404, rol) → `Failure` sin cambios y sin segunda llamada; sin conexión → `Offline` sin peticiones; ídem para tags. Regresión: `SessionEndDuringCategoriesRefreshTest`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** Misma ejecución: 9 tests nuevos en `CategoriesRepositoryImplTest`: categoría con 3 activas + 1 archivada → `POST reactivate` y `PUT` con `["a","c","d","b"]`, queda la cuarta, sin fecha, en caché; ya última o única activa → sin segunda llamada; orden falla → `Partial(Unknown)` y activa en memoria y caché en la posición del backend; reactivar falla (`404`, rol) → `Failure`, una sola petición y nada cambia; sin conexión → `Offline` sin peticiones (categoría y tag); tag → `POST` y `PUT …/tags/order` con `["t-a","t-b","t-x"]`; tag ya último → sin segunda llamada; tag con orden rechazado → `Partial`, y con `404` → `Failure` sin cambios. Comprobación de que los tests detectan el fallo: sin llevarla al final y sin la regla local de orden fallaron 3 tests; ya restaurado. Suite JVM completa: 1574 tests, 0 fallos, 0 errores, 0 omitidos. `ReactivationResult` está en `domain/categories/CategoryFailure.kt`. Sin datos cargados de la familia la reactivación devuelve `Success` sin calcular orden (no hay lista que mostrar). Regresión `SessionEndDuringCategoriesRefreshTest` incluida en la suite. No se ejecutó `lintDebug` ni la suite instrumentada en esta sección.

## Textos de archivar

- [x] **T-07 · Archivar ya no es irreversible**
  - **Cubre:** RF-02, RF-05 (CA-04, CA-07) · **Depende de:** —
  - **Alcance:** `res/values/strings.xml`: `categories_dialog_archive_message`, `categories_dialog_has_items_offer_archive_message` y `categories_dialog_tag_has_items_offer_archive_message` indican que se puede reactivar desde «Categorías archivadas» (la de tag, desde la categoría), sin «no podrás reactivarla» ni «irreversible». Los de medios de pago no cambian.
  - **Validación:** `./gradlew :app:assembleDebug`; `CategoryEditContentTest` comprueba los tres textos (`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rocioplata.familyfinaiapp.ui.categories.CategoryEditContentTest`).
  - **Hecho cuando:** compila y el test pasa.
  - **Evidencia:** `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -P…class=…CategoriesContentTest,…CategoryEditContentTest` → BUILD SUCCESSFUL; `CategoryEditContentTest` 70 tests, 0 fallos (leído de `app/build/outputs/androidTest-results/connected/debug`, emulador «Medium_Phone_API_37.0», de esta ejecución). Los tres textos ya no dicen «no podrás reactivarla»: archivar indica «Podrás reactivarla desde «Categorías archivadas»», la oferta de archivar categoría «…y reactivarla más tarde desde «Categorías archivadas»» y la de tag «…y reactivarla más tarde desde esta categoría». Los tres tests que los comprueban se actualizaron (uno renombrado a `archivingAsksForConfirmationSayingItCanBeReactivatedAndCancelChangesNothing`). Los de medios de pago no cambian. Después de la ejecución se reinstaló la app en el emulador (`installDebug`).

## Listado: reactivar y fecha

- [x] **T-08 · Reactivar y «Archivada en …» en el ViewModel**
  - **Cubre:** RF-01, RF-06, RF-07, RF-09, RF-11 (CA-01, CA-08 a CA-11, CA-13) · **Depende de:** T-06
  - **Alcance:** `CategoriesViewModel.reactivate(categoryId)` (solo si sigue archivada; `busy` con `getAndUpdate`); eventos `Reactivated` y `ReactivatedNotMoved`; `NotFound` → aviso y refresco; `InsufficientRole` → aviso y `onInsufficientRole`. `CategoryItem.archivedAt: YearMonth?` en la zona del dispositivo. `formatShortMonth(YearMonth, locale)` en `ui/movements/MoneyFormat.kt`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.categories.CategoriesViewModelTest"` y el test de `formatShortMonth`: contadores tras reactivar; cada evento; doble llamada → una petición; otra operación ignorada en curso; sin conexión; `archivedAt` con y sin fecha, nulo en activas.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL; `CategoriesViewModelTest` 45 (8 nuevos: reactivar pasa a las activas al final, actualiza contadores y avisa; parcial avisa y refresca; parcial por rol notifica al header; solo archivadas; sin conexión o con error avisa sin cambios; 404 avisa y refresca y rol notifica; dos pulsaciones u otra operación → una sola llamada; mes de archivado solo en archivadas con fecha y en la zona del dispositivo) y `MoneyFormatTest` 14 (1 nuevo: `formatShortMonth` con `Locale.US` → «Sep 2026» y en español empieza por «sep»), 0 fallos. Suite JVM completa: 1583 tests, 0 fallos, 0 errores, 0 omitidos. `CategoriesViewModel` recibe `zone: ZoneId = ZoneId.systemDefault()` antes de `onInsufficientRole` (`CategoriesScreen` lo pasa por nombre). `formatShortMonth` usa la abreviatura de la configuración regional tal cual (en español del JDK puede ser «sept.»); la spec la pide «con el formato del dispositivo».
- [x] **T-09 · Reactivar y fecha en el listado**
  - **Cubre:** RF-01, RF-03, RF-09 (CA-01 a CA-03, CA-13, CA-19) · **Depende de:** T-07, T-08
  - **Alcance:** `CategoriesContent`: «Reactivar» antes de «Eliminar» en el ⋮ de `ArchivedRow` (solo `canManage`, desactivado en curso), diálogo de confirmación con el id en `rememberSaveable`, avisos de los eventos nuevos y «Archivada en <mes año>» o solo «Archivada». Descripción accesible con el nombre. `CategoriesScreen` conecta la acción.
  - **Validación:** `CategoriesContentTest` (instrumentado): confirmar llama a la acción; cancelar no; Integrante sin «Reactivar»; textos de fecha con y sin ella; avisos. `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** Misma ejecución instrumentada de T-07: `CategoriesContentTest` 43 tests (8 nuevos: el Propietario reactiva tras confirmar; cancelar no hace nada; la opción «Reactivar» mide al menos 48 dp y se anuncia «Reactivar Gimnasio»; un Integrante no la ve y abre la archivada en solo lectura; la confirmación sobrevive a recrear la pantalla; «Archivada en <mes>» o solo «Archivada»; avisos «Categoría reactivada» y «…pero no se pudo mover al final», consumidos una vez), 0 fallos. Lint: 0 errores y 25 avisos, ninguno de estos cambios (versiones de dependencias y AGP, `OldTargetApi`, `RedundantLabel`, `LockedOrientationActivity`, `DiscouragedApi`, `Typos` en «momento» y un recurso de auth sin usar). `CategoriesContent` gana `onReactivateArchived` (con valor por defecto, para no tocar las vistas previas). No se ejecutó la suite instrumentada completa ni se probó contra el backend (T-14, T-15).

## Listado: orden

- [x] **T-10 · Modo de orden en el ViewModel**
  - **Cubre:** RF-06, RF-10, RF-12 (CA-14 a CA-16, CA-18) · **Depende de:** T-05
  - **Alcance:** `CategoriesViewModel`: `reorderMode`, `startReorder` (ignorado con búsqueda o menos de dos activas), `finishReorder`, `moveCategory(id, toIndex)`, `moveCategoryUp/Down`; `pendingOrder` optimista; si falla se restaura y, salvo sin conexión, `ReorderFailed` y refresco; escribir en el buscador sale del modo. `Content` gana `reorderMode` y `canReorder`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.categories.CategoriesViewModelTest"`: mover, subir y bajar en los límites; orden mostrado al instante; fallo → restaurado con aviso y refresco; sin conexión → mensaje y orden anterior; búsqueda desactiva y cierra; una operación a la vez.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL; `CategoriesViewModelTest` 54 (9 nuevos: abre y cierra el modo con al menos dos activas; no se abre con una sola activa ni con búsqueda; escribir en el buscador cierra el modo y borrarlo vuelve a permitirlo; mover muestra el orden al instante y lo guarda (envía solo las activas del tipo); subir y bajar mueven una posición y en los extremos no hacen nada; mover fuera del modo o con otra operación en curso se ignora (tampoco se puede reactivar entonces); rechazado → orden restaurado, `ReorderFailed` y refresco; sin conexión → `Failed(Offline)`, orden anterior y sin refresco; `404` avisa y refresca, rol notifica al header), 0 fallos. Suite JVM completa: 1592 tests, 0 fallos, 0 errores, 0 omitidos. El modo y el orden pendiente viven en `ReorderState` del ViewModel (sobreviven a recrear); cambiar de pestaña no cierra el modo, que pasa a ordenar el tipo visible.
- [x] **T-11 · Modo de orden en el listado**
  - **Cubre:** RF-10 (CA-14, CA-15, CA-19) · **Depende de:** T-09, T-10
  - **Alcance:** `CategoriesContent`: fila de sección «Categorías de gasto/ingreso» con su cantidad y «Reordenar» (solo `canManage`, desactivada sin `canReorder`). En el modo: activas en un único ítem con el arrastre de `TagList` (`ui/common/DragReorder.kt`), subir/bajar como botones y `CustomAccessibilityAction`, tocar una fila no la abre, sin «Crear», archivadas ni buscador, «Listo» y atrás cierran el modo.
  - **Validación:** `CategoriesContentTest` (instrumentado): «Reordenar» visible solo al Propietario y desactivada con búsqueda; el modo oculta lo indicado; subir/bajar llaman a la acción; «Listo» y atrás salen. `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -P…class=…CategoriesContentTest,…CategoryEditContentTest` → BUILD SUCCESSFUL; `CategoriesContentTest` 52 (9 nuevos: el Propietario ve el título de la sección con su cantidad y «Reordenar» de al menos 48 dp; el Integrante no ve «Reordenar»; desactivada con búsqueda y con otra operación en curso; el modo muestra solo las activas con su asa y oculta «Crear», archivadas y buscador, y tocar una fila no la abre; subir y bajar piden el movimiento y están desactivados en los extremos; cada fila tiene acciones de accesibilidad y el asa mide 48 dp; «Listo» y atrás cierran el modo sin salir; un Integrante nunca ve el modo) y `CategoryEditContentTest` 70, 0 fallos (emulador «Medium_Phone_API_37.0»). Dos tests anteriores (`tabsShowTheirLabelsAndActiveCounts` y `aSingleActiveCategoryUsesTheSingularForm`) fallaron porque la cantidad del tipo visible ahora aparece también en el título de la sección; se acotaron a la pestaña, que es lo que comprueban. Lint: 0 errores y los mismos 25 avisos previos. La fila del modo y su asa están en `ui/categories/CategoryReorder.kt` (tercera copia privada del asa, como en tags y en el selector de familias, para no tocar código de otras funcionalidades). El arrastre real no lo cubre ningún test automático: queda para T-15. Se reinstaló la app en el emulador tras la ejecución.

## Tags

- [x] **T-12 · Reactivar tag en el ViewModel de edición**
  - **Cubre:** RF-04, RF-06, RF-07, RF-11 (CA-05, CA-08 a CA-11, CA-17) · **Depende de:** T-06
  - **Alcance:** `CategoryEditViewModel.reactivateTag(tagId)` por `run()`, sin confirmación, ignorado en solo lectura; eventos `TagReactivated` y `TagReactivatedNotMoved` en `CategoryEditUiState`; 404 con `handleTagNotFound`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.categories.CategoryEditViewModelTest"`: queda último de los activos; cada evento; solo lectura y categoría archivada no llaman; doble llamada → una; sin conexión; rol.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL; `CategoryEditViewModelTest` 113 (7 nuevos: reactivar sin confirmación deja el tag el último de los activos y avisa «Tag reactivado»; en solo lectura, en una categoría archivada o sobre un tag activo no hace nada; dos pulsaciones u otra operación → una sola llamada; parcial avisa y actualiza, y parcial por rol notifica al header; sin conexión avisa y no cambia nada; `404` avisa del tag y actualiza; rol insuficiente avisa y notifica), 0 fallos. Suite JVM completa: 1599 tests, 0 fallos, 0 errores, 0 omitidos. `reactivateTag` no usa `run()` porque el repositorio devuelve `ReactivationResult`; replica su control de una operación a la vez (`busy.compareAndSet`). Se corrigió el comentario de `archiveTag` que decía «irreversible».
- [x] **T-13 · Botón «Reactivar» en los tags archivados**
  - **Cubre:** RF-04 (CA-05, CA-06, CA-19) · **Depende de:** T-12
  - **Alcance:** `TagList`/`TagRow`: botón «Reactivar» (48 dp, descripción con el nombre del tag) en los archivados cuando `!readOnly`, desactivado en curso; acción en `TagActions`; avisos en `CategoryEditContent`.
  - **Validación:** `CategoryEditContentTest` (instrumentado): visible y funcional para el Propietario en una categoría activa; ausente en solo lectura y en una archivada; sin diálogo. `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -P…class=…CategoriesContentTest,…CategoryEditContentTest` → BUILD SUCCESSFUL; `CategoryEditContentTest` 76 (6 nuevos: el Propietario reactiva un tag archivado sin diálogo; el botón se anuncia «Reactivar Viejo», mide al menos 48 dp y solo está en los archivados; en solo lectura o categoría archivada no aparece; desactivado durante otra operación; avisos «Tag reactivado» y «…pero no se pudo mover al final») y `CategoriesContentTest` 52, 0 fallos (emulador «Medium_Phone_API_37.0»). Lint: 0 errores y los mismos 25 avisos previos. El botón es un `TextButton` «Reactivar» en `TagRow`, antes del de quitar; `TagActions` gana `onReactivate`. Se reinstaló la app en el emulador tras la ejecución.

## Cierre

- [ ] **T-14 · Comprobaciones automáticas completas**
  - **Cubre:** Regresión · **Depende de:** T-11, T-13
  - **Alcance:** Suite JVM e instrumentada completas y lint; corregir lo que falle. No dejar la app desinstalada del emulador de la persona.
  - **Validación:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` y `./gradlew :app:connectedDebugAndroidTest`.
  - **Hecho cuando:** todo pasa sin avisos nuevos de lint, o cada fallo previo está identificado.
  - **Evidencia:** —
- [ ] **T-15 · Pruebas manuales contra el backend local**
  - **Cubre:** CA-01 a CA-18 · **Depende de:** T-14
  - **Alcance:** Emulador y backend local con el PR #163: comprobar primero lo documentado (rutas, `deprecatedAt`, `GET` ordenado, regla del `PUT`) y anotar diferencias en `BACKEND_GAPS.md` de la 010. Reactivar categoría y tag (éxito, cancelar, modo avión), reordenar con arrastre y subir/bajar, refrescar sin que nada se mueva, ver el orden en el formulario de movimiento y el dashboard, «Archivada en …», reabrir sin conexión; con una segunda sesión o `curl`, CA-10, CA-11 y CA-18. Borrar los datos de prueba al terminar.
  - **Validación:** tabla por criterio con resultado y captura.
  - **Hecho cuando:** cada criterio está verificado o marcado como no verificado con su motivo.
  - **Evidencia:** —
- [ ] **T-16 · Accesibilidad manual**
  - **Cubre:** CA-19 · **Depende de:** T-14
  - **Alcance:** TalkBack, fuente al 200 % y modo oscuro en «Reactivar» (categoría y tag), «Archivada en …» y el modo de orden.
  - **Validación:** lista de pantallas con resultado.
  - **Hecho cuando:** el resultado está registrado, incluido lo que el entorno no permita.
  - **Evidencia:** —
- [ ] **T-17 · Documentación final**
  - **Cubre:** — · **Depende de:** T-15, T-16
  - **Alcance:** `AGENTS.md` (categorías: orden, reactivar en dos pasos, campos de caché), `docs/ROADMAP.md`, `005-categories/BACKEND_GAPS.md` (BG-01, BG-03 y BG-04 incorporadas en la app), nota en `005-categories/SPEC.md` de los requisitos que cambia la 010 y la copia del contrato (`docs/backend/api-contract.md` §3.4.2 y `openapi.yaml`). `CLAUDE.md` no se toca.
  - **Validación:** revisar referencias y que cada CA apunte a su evidencia.
  - **Hecho cuando:** los documentos coinciden con lo implementado.
  - **Evidencia:** —

## Criterios y evidencia

| Criterio | Tareas | Estado |
| --- | --- | --- |
| CA-01 | T-06, T-08, T-09, T-15 | Pendiente |
| CA-02 | T-09, T-15 | Pendiente |
| CA-03 | T-09, T-15 | Pendiente |
| CA-04 | T-07, T-15 | Pendiente |
| CA-05 | T-06, T-12, T-13, T-15 | Pendiente |
| CA-06 | T-13, T-15 | Pendiente |
| CA-07 | T-07, T-15 | Pendiente |
| CA-08 | T-06, T-08, T-12, T-15 | Pendiente |
| CA-09 | T-08, T-12 | Pendiente |
| CA-10 | T-06, T-08, T-12, T-15 | Pendiente |
| CA-11 | T-08, T-12, T-15 | Pendiente |
| CA-12 | T-02, T-06, T-15 | Pendiente |
| CA-13 | T-01, T-08, T-09, T-15 | Pendiente |
| CA-14 | T-05, T-10, T-11, T-15 | Pendiente |
| CA-15 | T-10, T-11 | Pendiente |
| CA-16 | T-05, T-10, T-15 | Pendiente |
| CA-17 | T-06, T-12, T-15 | Pendiente |
| CA-18 | T-05, T-10, T-15 | Pendiente |
| CA-19 | T-09, T-11, T-13, T-16 | Pendiente |
