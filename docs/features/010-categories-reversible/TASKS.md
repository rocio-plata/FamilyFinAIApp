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

- [ ] **T-01 · Posición y fecha de archivado en el modelo y el remoto**
  - **Cubre:** RF-09, RF-10 (CA-13) · **Depende de:** —
  - **Alcance:** `domain/categories/Category.kt`: `displayOrder: Int = 0` y `deprecatedAt: Instant? = null`; corregir el comentario de `CategoryStatus` (archivar ya tiene inversa). `CategoriesRemoteDataSource`: `CategoryDto` lee `displayOrder` (por defecto 0) y `deprecatedAt`; una fecha ilegible se trata como ausente.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.CategoriesRemoteDataSourceTest"`: campos presentes, ausentes, `deprecatedAt` nulo e ilegible (la lista se lee igual). `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan y la app compila sin cambios visibles.
  - **Evidencia:** —
- [ ] **T-02 · Campos nuevos en la caché**
  - **Cubre:** RF-08 (CA-12) · **Depende de:** T-01
  - **Alcance:** `CategoriesLocalDataSource`: `displayOrder` y `deprecatedAt` (epoch ms) opcionales en `CategoryJson`, sin subir `schemaVersion`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.CategoriesLocalDataSourceTest"`: ida y vuelta con los campos y copia anterior sin ellos (posición 0, sin fecha).
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-03 · Orden estable en el repositorio**
  - **Cubre:** RF-10, RF-11 · **Depende de:** T-02
  - **Alcance:** `List<Category>.inDisplayOrder()` en el dominio (orden estable por `displayOrder`, sin mover las archivadas al final). `CategoriesRepositoryImpl` lo aplica tras cada descarga, lectura de caché y cambio aplicado. `deprecateCategory` fija `deprecatedAt` con el reloj del repositorio.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.*"`: lista desordenada sale ordenada; empates (todas en 0) conservan el orden del backend; archivar fija la fecha y no mueve la categoría. Regresión: `ui.categories.CategoryColorsTest` (archivar no cambia el color de las demás), `domain.dashboard.*`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Operaciones

- [ ] **T-04 · Llamadas remotas de reactivar y reordenar**
  - **Cubre:** RF-01, RF-04, RF-10 · **Depende de:** T-01
  - **Alcance:** `CategoriesRemoteDataSource`: `reactivateCategory`, `reactivateTag` (`POST …/reactivate`, `204`) y `reorderCategories` (`PUT …/categories/order` con `orderedCategoryIds`, `204`).
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.CategoriesRemoteDataSourceTest"`: ruta, método y cuerpo; `204`; `404` → `NotFound`; `FAMILY.INSUFFICIENT_ROLE` → `InsufficientRole`; `400 INVALID_CATEGORY_ORDER` → `Unknown`; error de red → `Network`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-05 · Reordenar categorías en el repositorio**
  - **Cubre:** RF-10, RF-12 (CA-14, CA-16, CA-18) · **Depende de:** T-03, T-04
  - **Alcance:** `CategoriesRepository.reorderCategories(familyId, type, activeIdsInOrder)` por `change()`: envía solo las activas del tipo y, al confirmarse, aplica la regla del backend (las enviadas en 0..n-1, el resto del tipo detrás en su orden previo, el otro tipo sin cambios). Los fakes de `CategoriesRepository` (`ui/movements/Fakes.kt`, `CategoriesViewModelTest`, `CategoryEditViewModelTest`) implementan el método.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.CategoriesRepositoryImplTest"`: cuerpo enviado; memoria y caché con el nuevo orden y `savedAt` conservado; archivadas detrás; otro tipo intacto; sin conexión no hay petición; fallo sin cambios. `./gradlew :app:testDebugUnitTest` (compilan los fakes).
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-06 · Reactivar en el repositorio**
  - **Cubre:** RF-01, RF-04, RF-06 a RF-08, RF-11 (CA-08, CA-10, CA-12, CA-17) · **Depende de:** T-05
  - **Alcance:** `ReactivationResult` (`Success`, `Partial(orderFailure)`, `Failure`) en el dominio. `reactivateCategory`: reactivar por `change()` (estado activo, sin fecha), y si no queda ya como la última activa del tipo, `reorderCategories` con ella al final. `reactivateTag`: lo mismo con `reorderTags`. Fakes actualizados.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.CategoriesRepositoryImplTest"`: 3 activas + 1 archivada → la cuarta (cuerpo del `PUT`); única activa o ya última → sin segunda llamada; orden falla → `Partial` con la reactivación ya en memoria y caché; reactivar falla (404, rol) → `Failure` sin cambios y sin segunda llamada; sin conexión → `Offline` sin peticiones; ídem para tags. Regresión: `SessionEndDuringCategoriesRefreshTest`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Textos de archivar

- [ ] **T-07 · Archivar ya no es irreversible**
  - **Cubre:** RF-02, RF-05 (CA-04, CA-07) · **Depende de:** —
  - **Alcance:** `res/values/strings.xml`: `categories_dialog_archive_message`, `categories_dialog_has_items_offer_archive_message` y `categories_dialog_tag_has_items_offer_archive_message` indican que se puede reactivar desde «Categorías archivadas» (la de tag, desde la categoría), sin «no podrás reactivarla» ni «irreversible». Los de medios de pago no cambian.
  - **Validación:** `./gradlew :app:assembleDebug`; `CategoryEditContentTest` comprueba los tres textos (`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rocioplata.familyfinaiapp.ui.categories.CategoryEditContentTest`).
  - **Hecho cuando:** compila y el test pasa.
  - **Evidencia:** —

## Listado: reactivar y fecha

- [ ] **T-08 · Reactivar y «Archivada en …» en el ViewModel**
  - **Cubre:** RF-01, RF-06, RF-07, RF-09, RF-11 (CA-01, CA-08 a CA-11, CA-13) · **Depende de:** T-06
  - **Alcance:** `CategoriesViewModel.reactivate(categoryId)` (solo si sigue archivada; `busy` con `getAndUpdate`); eventos `Reactivated` y `ReactivatedNotMoved`; `NotFound` → aviso y refresco; `InsufficientRole` → aviso y `onInsufficientRole`. `CategoryItem.archivedAt: YearMonth?` en la zona del dispositivo. `formatShortMonth(YearMonth, locale)` en `ui/movements/MoneyFormat.kt`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.categories.CategoriesViewModelTest"` y el test de `formatShortMonth`: contadores tras reactivar; cada evento; doble llamada → una petición; otra operación ignorada en curso; sin conexión; `archivedAt` con y sin fecha, nulo en activas.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-09 · Reactivar y fecha en el listado**
  - **Cubre:** RF-01, RF-03, RF-09 (CA-01 a CA-03, CA-13, CA-19) · **Depende de:** T-07, T-08
  - **Alcance:** `CategoriesContent`: «Reactivar» antes de «Eliminar» en el ⋮ de `ArchivedRow` (solo `canManage`, desactivado en curso), diálogo de confirmación con el id en `rememberSaveable`, avisos de los eventos nuevos y «Archivada en <mes año>» o solo «Archivada». Descripción accesible con el nombre. `CategoriesScreen` conecta la acción.
  - **Validación:** `CategoriesContentTest` (instrumentado): confirmar llama a la acción; cancelar no; Integrante sin «Reactivar»; textos de fecha con y sin ella; avisos. `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Listado: orden

- [ ] **T-10 · Modo de orden en el ViewModel**
  - **Cubre:** RF-06, RF-10, RF-12 (CA-14 a CA-16, CA-18) · **Depende de:** T-05
  - **Alcance:** `CategoriesViewModel`: `reorderMode`, `startReorder` (ignorado con búsqueda o menos de dos activas), `finishReorder`, `moveCategory(id, toIndex)`, `moveCategoryUp/Down`; `pendingOrder` optimista; si falla se restaura y, salvo sin conexión, `ReorderFailed` y refresco; escribir en el buscador sale del modo. `Content` gana `reorderMode` y `canReorder`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.categories.CategoriesViewModelTest"`: mover, subir y bajar en los límites; orden mostrado al instante; fallo → restaurado con aviso y refresco; sin conexión → mensaje y orden anterior; búsqueda desactiva y cierra; una operación a la vez.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-11 · Modo de orden en el listado**
  - **Cubre:** RF-10 (CA-14, CA-15, CA-19) · **Depende de:** T-09, T-10
  - **Alcance:** `CategoriesContent`: fila de sección «Categorías de gasto/ingreso» con su cantidad y «Reordenar» (solo `canManage`, desactivada sin `canReorder`). En el modo: activas en un único ítem con el arrastre de `TagList` (`ui/common/DragReorder.kt`), subir/bajar como botones y `CustomAccessibilityAction`, tocar una fila no la abre, sin «Crear», archivadas ni buscador, «Listo» y atrás cierran el modo.
  - **Validación:** `CategoriesContentTest` (instrumentado): «Reordenar» visible solo al Propietario y desactivada con búsqueda; el modo oculta lo indicado; subir/bajar llaman a la acción; «Listo» y atrás salen. `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Tags

- [ ] **T-12 · Reactivar tag en el ViewModel de edición**
  - **Cubre:** RF-04, RF-06, RF-07, RF-11 (CA-05, CA-08 a CA-11, CA-17) · **Depende de:** T-06
  - **Alcance:** `CategoryEditViewModel.reactivateTag(tagId)` por `run()`, sin confirmación, ignorado en solo lectura; eventos `TagReactivated` y `TagReactivatedNotMoved` en `CategoryEditUiState`; 404 con `handleTagNotFound`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.categories.CategoryEditViewModelTest"`: queda último de los activos; cada evento; solo lectura y categoría archivada no llaman; doble llamada → una; sin conexión; rol.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-13 · Botón «Reactivar» en los tags archivados**
  - **Cubre:** RF-04 (CA-05, CA-06, CA-19) · **Depende de:** T-12
  - **Alcance:** `TagList`/`TagRow`: botón «Reactivar» (48 dp, descripción con el nombre del tag) en los archivados cuando `!readOnly`, desactivado en curso; acción en `TagActions`; avisos en `CategoryEditContent`.
  - **Validación:** `CategoryEditContentTest` (instrumentado): visible y funcional para el Propietario en una categoría activa; ausente en solo lectura y en una archivada; sin diálogo. `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

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
