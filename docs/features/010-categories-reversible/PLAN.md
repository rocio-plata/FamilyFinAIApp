# PLAN: Categorías reversibles y ordenables (reactivar, orden y fecha de archivado)

**SPEC de referencia:** `docs/features/010-categories-reversible/SPEC.md`
**Versión de la spec revisada:** aprobada el 2026-11-24 (rama `feature/010-categories-reversible`, sobre `5b5aef8`, con las decisiones de planificación del mismo día)
**Estado:** Aprobado (2026-11-24) <!-- Borrador | En revisión | Aprobado -->

<!-- PARA LA PERSONA
Copia esta plantilla como PLAN.md junto a la SPEC.md aprobada.
Este documento define la solución técnica. Una vez revisado, el agente puede
derivar TASKS.md con tareas, dependencias y comprobaciones.
-->

<!-- PARA EL AGENTE
- Lee la SPEC.md aprobada, las instrucciones del proyecto y MOBILE_GUIDELINES.md.
  Si falta un documento necesario o la spec no está aprobada, indícalo antes de avanzar.
- Inspecciona el repositorio. Referencia rutas verificadas y distingue las nuevas propuestas.
- Propón una solución proporcional al alcance y coherente con el proyecto.
  Reutiliza lo existente y justifica nuevas dependencias o cambios de arquitectura.
- Distingue hechos, decisiones confirmadas y propuestas. Consulta las decisiones
  no resueltas; haz pocas preguntas por vez y actualiza el plan con las respuestas.
- Referencia los requisitos y criterios por su ID, sin copiar toda la spec.
- Si una decisión cambia el comportamiento o alcance, vuelve a la spec y solicita
  confirmación. No resuelvas una duda de producto mediante una suposición técnica.
- Conserva estos comentarios. No implementes durante la planificación.
- Solicita aprobación antes de marcar el plan como Aprobado. La autorización
  para implementar debe ser explícita; no se deduce del estado de los documentos.
-->

## Contexto técnico verificado

<!-- Qué existe hoy y cómo participa en la funcionalidad. -->

Rutas relativas a `app/src/main/java/com/rocioplata/familyfinaiapp/` salvo que se indique otra cosa.

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| Modelo de dominio | `domain/categories/Category.kt` | `Category` (sin posición ni fecha) y `Tag` (con `displayOrder`). Se amplía `Category`. El comentario de `CategoryStatus` dice que archivar no tiene inversa: se corrige. |
| Contrato del repositorio | `domain/categories/CategoriesRepository.kt` | Flujo por familia, `refresh` y mutaciones con `CategoryResult`. Se añaden reactivar y reordenar categorías. |
| Fallos | `domain/categories/CategoryFailure.kt` | `Offline`, `NotFound`, `InsufficientRole`, etc. Se reutilizan sin tipos nuevos. |
| Fuente remota | `data/categories/CategoriesRemoteDataSource.kt` | Ktor; `CategoryDto` sin `displayOrder` ni `deprecatedAt`; `reorderTags` ya existe; 404 → `NotFound`, `FAMILY.INSUFFICIENT_ROLE` → `InsufficientRole`, código desconocido → `Unknown`. |
| Repositorio | `data/categories/CategoriesRepositoryImpl.kt` | `change()` comprueba conexión, llama y aplica a memoria y caché con candado y época. `reorderTags` ya envía activos + archivados. Guarda la lista en el orden del backend. |
| Caché | `data/categories/CategoriesLocalDataSource.kt` | `categories_cache`, `schemaVersion = 1`; `color` se añadió como campo opcional sin subir la versión (mismo patrón para los campos nuevos). |
| Listado | `ui/categories/CategoriesViewModel.kt`, `CategoriesUiState.kt`, `CategoriesContent.kt`, `CategoriesScreen.kt` | Tipo, búsqueda, plegado de archivadas, `deleteArchived` con `OpState(busy, event)`; menú ⋮ de `ArchivedRow` solo con «Eliminar»; no hay título de sección ni «Reordenar». |
| Edición y tags | `ui/categories/CategoryEditViewModel.kt`, `TagList.kt` | `run()` de una operación a la vez, `moveTag` optimista con `pendingOrder`, `handleTagNotFound`; `TagRow` muestra «Archivado» y el arrastre propio. |
| Cálculo de arrastre | `ui/common/DragReorder.kt` | `dragTargetIndex` y `dragShift`, ya compartidos por tags y familias. |
| Textos de fallo | `ui/categories/CategoryTexts.kt` | `CategoryFailure.messageRes(forTag)`. |
| Color de respaldo | `ui/categories/CategoryColors.kt` | `positionOf` usa el orden del repositorio entre las de su tipo, archivadas incluidas. No cambia (decisión: el color sigue a la posición). |
| Dashboard y movimientos | `domain/dashboard/DashboardCalculator.kt`, `ui/movements/MovementFormViewModel.kt` | Usan el orden de la lista del repositorio («el de la gestión»): heredan el orden nuevo sin cambios. |
| Formato de mes | `ui/movements/MoneyFormat.kt` | `formatMonth` («Noviembre 2026», `LLLL yyyy`). Se añade una variante abreviada. |
| Textos | `app/src/main/res/values/strings.xml` | `categories_dialog_archive_message`, `categories_dialog_has_items_offer_archive_message` y `categories_dialog_tag_has_items_offer_archive_message` dicen «no podrás reactivarla». |
| Tests | `app/src/test/…/data/categories/*Test.kt`, `ui/categories/*Test.kt`, `ui/movements/Fakes.kt`; `app/src/androidTest/…/ui/categories/CategoriesContentTest.kt`, `CategoryEditContentTest.kt` | Fakes de `CategoriesRepository` en `Fakes.kt`, `CategoriesViewModelTest` y `CategoryEditViewModelTest`: deben implementar los métodos nuevos. |

Hechos del backend (de `005-categories/BACKEND_GAPS.md`, BG-01, BG-03 y BG-04; verificados en su `main`, no en ejecución): rutas `…/reactivate` (`204`), `deprecatedAt` ISO-8601 o `null`, `displayOrder` por tipo (todas en 0 antes de la migración), `GET` ordenado por `displayOrder`, `PUT …/categories/order` con `orderedCategoryIds` (`204`; las no enviadas del tipo quedan después en su orden previo; `400 INVALID_CATEGORY_ORDER`, `404 CATEGORY_NOT_FOUND`). Reactivar conserva la posición anterior. La copia local `docs/backend/api-contract.md` y `openapi.yaml` todavía no recoge estos cambios.

**Convenciones y patrón de referencia:** capas dominio/datos/UI de la 005; mutaciones por `change()` del repositorio (conexión, época, aplicar a memoria y caché conservando `savedAt`); una operación a la vez con el indicador fijado antes de lanzar la corrutina (`getAndUpdate`); reordenado optimista con restauración de `moveTag`; resultado parcial tipado como `MovementEditResult.Partial` (007); campos de caché opcionales sin subir `schemaVersion` (`color`, 008).

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de datos y eventos hasta la interfaz. Usa un diagrama si aporta claridad. -->

**1. El orden vive en el repositorio.** `Category` gana `displayOrder` y `deprecatedAt`. El repositorio guarda y emite
la lista ordenada de forma estable por `displayOrder` (función de dominio `inDisplayOrder()`), tras cada descarga y cada
cambio aplicado. Así todas las pantallas (gestión, formulario de movimiento, dashboard, colores) ven el mismo orden sin
tocarlas (RF-10). Al ser estable, las anteriores a la migración (todas en 0) conservan el orden del backend. Las archivadas
**no** se mueven al final en la lista del repositorio: la UI ya las separa (listado, dashboard, formulario) y así archivar no
cambia la posición ni el color de las demás (decisión de la spec).

**2. Reactivar = dos llamadas en el repositorio.** `reactivateCategory(familyId, categoryId)`:
1. `change()` con `POST …/reactivate`; al confirmarse aplica `status = ACTIVE` y `deprecatedAt = null` a memoria y caché (RF-08).
2. Si la categoría no queda ya como la última activa de su tipo, llama a `reorderCategories` con las activas del tipo en su
   orden actual y ella al final (RF-11). Si es la única activa o ya es la última, no hay segunda llamada.
3. Devuelve `ReactivationResult`: `Success`, `Partial(orderFailure)` (reactivada, pero el orden falló: decisión de la spec) o
   `Failure(failure)` (no se reactivó; nada cambia).

`reactivateTag(familyId, categoryId, tagId)` hace lo mismo con `POST …/tags/:tagId/reactivate` y, después, el
`reorderTags` existente con los activos + el reactivado al final (el repositorio añade los archivados como hoy).

Un 404 en el segundo paso (otro dispositivo la borró entre medias) es `Partial(NotFound)`: la UI avisa y refresca.

**3. Reordenar categorías.** `reorderCategories(familyId, type, activeIdsInOrder)` envía solo las activas del tipo
(el backend deja las no enviadas, las archivadas, después en su orden previo). Al confirmarse aplica las mismas reglas
localmente: las enviadas toman las posiciones 0..n-1 y el resto del tipo les sigue en su orden previo; el otro tipo no
cambia. Esto reproduce lo que devolverá el siguiente `GET`, así que un refresco no mueve nada (ni colores).

**4. Listado (`CategoriesViewModel`).** Gana:
- `reactivate(categoryId)`: ya confirmada por la persona; comprueba que siga archivada, toma `busy` con `getAndUpdate` y
  emite `Reactivated`, `ReactivatedNotMoved` o el fallo (RF-01, RF-06, RF-07, RF-11).
- Modo de orden (`reorderMode`, en memoria del ViewModel; sobrevive a recrear): `startReorder()` (ignorado con búsqueda o
  con menos de dos activas), `finishReorder()`, `moveCategory(id, toIndex)`, `moveCategoryUp/Down(id)`. Igual que
  `moveTag`: `pendingOrder` optimista, llamada a `reorderCategories`, restauración si falla y, salvo sin conexión,
  `ReorderFailed` + refresco (RF-10, RF-12). Sin conexión: mensaje de conexión y orden anterior (CA-16).
- `Content` gana `reorderMode`, `canReorder` (sin búsqueda, al menos dos activas, sin operación en curso) y
  `active` ya en el orden pendiente si lo hay. Escribir en el buscador sale del modo.
- `CategoryItem` gana `archivedAt: YearMonth?` (mes en la zona del dispositivo, solo en archivadas).

**5. `CategoriesContent`.** Encima de las activas, una fila de sección «Categorías de gasto/ingreso» con su cantidad y la
acción «Reordenar» (solo con `canManage`, desactivada si `!canReorder`), como el mockup. En el modo: las activas se pintan en
un único ítem del `LazyColumn` con el arrastre propio de `TagList` (`dragTargetIndex`/`dragShift`) más subir/bajar como
acciones de accesibilidad y botones; se ocultan «Crear nueva categoría», las archivadas y el buscador; la cabecera muestra
«Listo» y atrás (`BackHandler`) cierra el modo. `ArchivedRow` muestra «Archivada en sep 2026» (`formatShortMonth`, nuevo en
`MoneyFormat.kt`, `LLL yyyy` con la configuración regional) o solo «Archivada», y su menú ⋮ añade «Reactivar» antes de
«Eliminar». Reactivar pide confirmación con un diálogo cuyo id pendiente es `rememberSaveable` (se conserva al recrear).
Los avisos «Categoría reactivada» / «…pero no se pudo mover al final» van al `SnackbarHost` existente.

**6. Tags (`CategoryEditViewModel`, `TagList`).** `reactivateTag(tagId)` por `run()` (una operación a la vez), sin
confirmación (RF-04), con eventos `TagReactivated` / `TagReactivatedNotMoved` y `handleTagNotFound` para el 404.
`TagRow` muestra un botón «Reactivar» (48 dp, descripción con el nombre del tag) en los archivados cuando la categoría es
editable (`!readOnly`: activa y Propietario, RF-03).

**7. Textos.** Se cambian los tres mensajes de archivar para indicar que se puede reactivar desde «Categorías archivadas»
(RF-02, RF-05); se añaden los de reactivar, reordenar, «Listo», «Archivada en %s» y las descripciones accesibles.

```
CategoriesContent ──reactivate/move──▶ CategoriesViewModel ──▶ CategoriesRepositoryImpl ──▶ RemoteDataSource
       ▲                                     │                       │  change(): conexión, época,
       └──── StateFlow<CategoriesUiState> ◀──┘◀── Flow<CategoriesData> ◀┘  memoria + caché, inDisplayOrder()
```

## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

| Módulo | Existe / nuevo | Responsabilidad y cambios | Dependencias afectadas |
| --- | --- | --- | --- |
| No aplica: módulo único `app/` | Existente | Cambios en `domain/categories`, `data/categories`, `ui/categories`, `ui/movements/MoneyFormat.kt` y recursos | Ninguna nueva |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son propuestas.
Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `domain/categories/Category.kt` | Modificar | `Category.displayOrder: Int = 0`, `Category.deprecatedAt: Instant? = null`; `List<Category>.inDisplayOrder()`; corregir el comentario de `CategoryStatus` | RF-09, RF-10, RF-11 |
| `domain/categories/CategoriesRepository.kt` | Modificar | `reactivateCategory`, `reactivateTag` → `ReactivationResult`; `reorderCategories(familyId, type, activeIdsInOrder)` → `CategoryResult<Unit>` | RF-01, RF-04, RF-10, RF-11 |
| `domain/categories/CategoryFailure.kt` | Modificar | `ReactivationResult` (`Success`, `Partial(orderFailure)`, `Failure`) | RF-11 |
| `data/categories/CategoriesRemoteDataSource.kt` | Modificar | DTO con `displayOrder` (por defecto 0) y `deprecatedAt`; `reactivateCategory`, `reactivateTag`, `reorderCategories` | RF-01, RF-04, RF-09, RF-10 |
| `data/categories/CategoriesRepositoryImpl.kt` | Modificar | Orden estable al guardar; reactivar en dos pasos; reordenar con la regla del backend; archivar fija `deprecatedAt` | RF-06 a RF-12 |
| `data/categories/CategoriesLocalDataSource.kt` | Modificar | `displayOrder` y `deprecatedAt` opcionales en `CategoryJson`, sin subir `schemaVersion` | RF-08 |
| `ui/categories/CategoriesViewModel.kt`, `CategoriesUiState.kt` | Modificar | Reactivar, modo de orden, eventos nuevos, `archivedAt` en `CategoryItem` | RF-01, RF-06, RF-07, RF-09 a RF-12 |
| `ui/categories/CategoriesContent.kt` | Modificar | Fila de sección con «Reordenar», modo de orden, «Reactivar» en ⋮ con confirmación, «Archivada en …» | RF-01, RF-09, RF-10, CA-19 |
| `ui/categories/CategoryEditViewModel.kt`, `CategoryEditUiState.kt` | Modificar | `reactivateTag` y sus eventos | RF-04, RF-06, RF-07, RF-11 |
| `ui/categories/TagList.kt`, `CategoryEditScreen.kt`/`CategoryEditContent.kt` | Modificar | Botón «Reactivar» en tags archivados y su acción en `TagActions`; avisos | RF-04, CA-19 |
| `ui/categories/CategoryTexts.kt` | Reutilizar | Sin cambios salvo que un evento nuevo lo requiera | RF-06, RF-07 |
| `ui/common/DragReorder.kt` | Reutilizar | Cálculo del arrastre de categorías | RF-10 |
| `ui/movements/MoneyFormat.kt` | Modificar | `formatShortMonth(YearMonth, locale)` | RF-09 |
| `app/src/main/res/values/strings.xml` | Modificar | Textos de archivar sin «no podrás reactivarla»; textos nuevos | RF-02, RF-05 |
| Fakes de `CategoriesRepository` en tests | Modificar | Implementar los métodos nuevos | — |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Modelos y contratos de entrada y salida:** `POST /families/:familyId/categories/:categoryId/reactivate` y
  `POST …/tags/:tagId/reactivate` sin cuerpo, `204`. `PUT /families/:familyId/categories/order` con
  `{ "orderedCategoryIds": [...] }`, `204`. `CategoryDto` gana `displayOrder: Int = 0` y `deprecatedAt: String? = null`.
  Errores: los existentes (`404` → `NotFound`, rol → `InsufficientRole`); `INVALID_CATEGORY_ORDER` queda como `Unknown`,
  igual que hoy `INVALID_TAG_ORDER` (la UI trata ambos como «no se pudo guardar el orden»).
- **Identificadores, relaciones y restricciones:** el orden se envía por tipo y solo con activas, sin repetidos (el
  ViewModel solo mueve ids de la lista activa). `displayOrder` es por tipo.
- **Origen de los datos mostrados y transformaciones:** «Archivada en …» sale de `deprecatedAt` convertido a `YearMonth` en
  la zona del dispositivo. Una fecha que no se puede leer se trata como ausente (solo «Archivada»): es un dato informativo y
  rechazar toda la lista por él dejaría sin categorías a la app; se cubre con un test. Al archivar desde la app,
  `deprecatedAt` se fija con el reloj del dispositivo (lo aplicado es lo que hizo la app, como en movimientos); el siguiente
  refresco trae la del backend.
- **Persistencia, consultas y actualizaciones:** `categories_cache` guarda `displayOrder` y `deprecatedAt` (epoch ms). Cada
  cambio correcto se aplica a memoria y caché por `change()`, conservando `savedAt` (RF-08, CA-12).
- **Convivencia entre datos locales y remotos:** el backend manda; la regla local de reordenado imita la del backend para que
  el refresco no cambie nada. Tras `Partial` o un rechazo del orden se refresca.
- **Compatibilidad y migraciones de datos existentes:** una caché sin los campos nuevos se lee con `displayOrder = 0` y sin
  fecha, y el orden estable conserva el guardado. Sin migración ni cambio de `schemaVersion`.

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de MOBILE_GUIDELINES.md. -->

- **Gestión del estado de interfaz y navegación:** `StateFlow<CategoriesUiState>` y el estado de la edición existentes;
  eventos de una vez (`CategoriesEvent.Reactivated`, `ReactivatedNotMoved`, `ReorderFailed`; `CategoryEditEvent.TagReactivated`,
  `TagReactivatedNotMoved`). El modo de orden no es una ruta: atrás lo cierra antes de salir del listado. Sin navegación nueva.
- **Conservación y restauración del estado:** modo de orden en el ViewModel (sobrevive a recrear; se pierde con el proceso,
  aceptado en la spec); diálogo de confirmación de reactivar con `rememberSaveable`.
- **Ejecución, concurrencia y cancelación de operaciones:** `viewModelScope`; una operación de cambio a la vez con `busy`
  fijado antes de lanzar (CA-09); reactivar y reordenar comparten `busy` con eliminar. Salir de la pantalla no cancela la
  operación del repositorio de forma distinta a hoy: si el ViewModel se destruye, la corrutina se cancela; lo ya confirmado
  por el backend se refleja en el próximo refresco (spec: «el resultado se refleja al volver»).
- **Errores, reintentos y prevención de duplicados:** `Offline` sin llamar (CA-08, CA-16); `NotFound` → aviso y refresco
  (CA-10, CA-18); `InsufficientRole` → aviso y `onInsufficientRole` (CA-11); orden rechazado → restaurar, aviso y refresco
  (RF-12); fallo de red incierto → la lista se vuelve a pedir al recuperar la conexión (ya lo hace el listado). Reactivar
  algo ya activo no da error en el backend, así que un reintento tras un fallo incierto es seguro.
- **Otras consideraciones mobile aplicables y su solución:** accesibilidad (CA-19): «Reactivar» con descripción que nombra
  la categoría o el tag y 48 dp; «Archivada en …» como texto, no solo color; subir/bajar como `CustomAccessibilityAction` y
  botones en el modo de orden; fuente al 200 % sin cortar «Reordenar»/«Listo» (fila con `weight` y salto de línea); modo
  oscuro con los colores del tema.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

- Ninguna dependencia, permiso ni configuración nueva. `java.time` ya se usa (minSdk 34).

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, UI o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Unit `CategoriesRepositoryImplTest` (reactivar aplica estado y orden), `CategoriesViewModelTest` (contadores, evento); UI `CategoriesContentTest` (⋮ → Reactivar → confirmar) | JVM con `ktor-client-mock`; emulador | Tests en verde; manual contra backend local |
| CA-02 | UI `CategoriesContentTest`: cancelar no llama a `onReactivate` | Emulador | Test en verde |
| CA-03 | UI `CategoriesContentTest` con `canManage = false`: sin «Reactivar»; regresión de solo lectura en `CategoryEditViewModelTest` | Emulador | Tests en verde |
| CA-04 | Unit/UI: texto del diálogo de archivar sin «no podrás reactivarla» (`CategoryEditContentTest`) | Emulador | Test en verde |
| CA-05 | Unit `CategoryEditViewModelTest` (reactivar tag sin confirmación, al final); UI `CategoryEditContentTest` | JVM; emulador | Tests en verde |
| CA-06 | UI `CategoryEditContentTest` en solo lectura y con categoría archivada: sin «Reactivar» | Emulador | Test en verde |
| CA-07 | UI `CategoryEditContentTest`: oferta de archivar tag sin «irreversible» ni «no podrás reactivarla» | Emulador | Test en verde |
| CA-08 | Unit repositorio (`Offline` sin peticiones al `MockEngine`) y ViewModels (mensaje) | JVM | Tests en verde |
| CA-09 | Unit ViewModels: dos llamadas seguidas → una petición; otra operación ignorada mientras `isBusy` | JVM | Tests en verde |
| CA-10 | Unit: 404 → evento y refresco (listado y edición) | JVM | Tests en verde; manual opcional con dos dispositivos |
| CA-11 | Unit: `InsufficientRole` → aviso y `onInsufficientRole` | JVM | Tests en verde |
| CA-12 | Unit repositorio: tras reactivar, la caché guardada (`CategoriesLocalDataSource`) tiene el estado nuevo; `CategoriesLocalDataSourceTest` con los campos nuevos y con caché antigua | JVM | Tests en verde; manual: reactivar, modo avión, reabrir |
| CA-13 | Unit `CategoriesRemoteDataSourceTest` (`deprecatedAt` válido, nulo, ilegible), `CategoriesViewModelTest` (`archivedAt`); test de `formatShortMonth`; UI `CategoriesContentTest` | JVM; emulador | Tests en verde; captura |
| CA-14 | Unit repositorio (regla de orden local = backend, cuerpo del `PUT`), ViewModel (mover, subir/bajar); UI (modo, subir/bajar); manual: arrastrar, salir y volver, ver el orden en el formulario de movimiento y el dashboard | JVM; emulador con backend local | Tests en verde; manual registrado |
| CA-15 | UI `CategoriesContentTest`: Integrante sin «Reordenar», desactivada con búsqueda; archivadas sin asa | Emulador | Test en verde |
| CA-16 | Unit ViewModel: `Offline` → mensaje y orden restaurado | JVM | Test en verde |
| CA-17 | Unit repositorio: 3 activas + 1 archivada → reactivada en 4.ª posición (cuerpo del `PUT`); ídem tag; `Partial` cuando el orden falla | JVM | Tests en verde |
| CA-18 | Unit: `PUT` con 404/400 → restaurar, aviso, refresco | JVM | Tests en verde |
| CA-19 | Manual con TalkBack, fuente 200 % y modo oscuro; UI: descripción de «Reactivar» con el nombre | Emulador | Registro manual y capturas |

**Comprobaciones de regresión:** suites existentes de categorías (`CategoriesRepositoryImplTest`, `CategoriesRemoteDataSourceTest`,
`CategoriesLocalDataSourceTest`, `CategoriesViewModelTest`, `CategoryEditViewModelTest`, `CategoryColorsTest`), dashboard
(`DashboardCalculatorTest`, `DashboardViewModelTest`) y formulario de movimiento; UI `CategoriesContentTest` y `CategoryEditContentTest`.

**Comandos verificados para compilar y ejecutar tests:** `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`,
`./gradlew :app:lintDebug`, `./gradlew :app:connectedDebugAndroidTest` (con emulador). Requieren `local.properties` con
`DEV_EMAIL`, `DEV_PASSWORD` y `DEV_TARGET` (AGENTS.md).

**Pruebas en dispositivo, emulador o simulador:** emulador con el backend local en una versión que incluya el PR #163:
reactivar categoría y tag (con éxito, sin conexión, cancelando), reordenar arrastrando y con TalkBack, comprobar el orden en
el formulario de movimiento y el dashboard, «Archivada en …», reabrir sin conexión. No hace falta hardware real.

**Limitaciones del entorno:** CA-10, CA-11 y CA-18 de extremo a extremo necesitan una segunda sesión (otro dispositivo o
`curl`) para borrar o bajar el rol; si no se hacen, quedan cubiertos por tests unitarios y se registra como no verificado
en ejecución. El caso `Partial` no es reproducible a mano sin forzar un fallo del backend: solo test unitario.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. **Datos:** modelo y DTO (`displayOrder`, `deprecatedAt`), caché compatible, orden estable en el repositorio. Comprobación:
   tests de remoto, caché y repositorio; regresión de dashboard y colores.
2. **Operaciones:** remoto y repositorio de reactivar (dos pasos, `ReactivationResult`) y `reorderCategories`. Depende de 1.
   Comprobación: tests de repositorio (CA-08, CA-12, CA-17, CA-18).
3. **Textos de archivar** (RF-02, RF-05). Independiente. Comprobación: tests de UI existentes ajustados.
4. **Listado:** reactivar y «Archivada en …» en ViewModel y contenido. Depende de 2. Comprobación: tests unitarios y de UI.
5. **Listado:** modo de orden. Depende de 2. Comprobación: tests unitarios y de UI.
6. **Tags:** reactivar en la edición. Depende de 2. Comprobación: tests unitarios y de UI.
7. **Cierre:** validación manual en emulador, instrumentados, lint; actualizar `AGENTS.md`, `ROADMAP.md`, el estado de BG-01,
   BG-03 y BG-04 en `005-categories/BACKEND_GAPS.md` y la copia del contrato en `docs/backend/` si se confirma (ver decisiones).

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - El backend no se ha probado en ejecución (BG-01, BG-03, BG-04): la primera tarea de cierre lo prueba contra el backend
    local; si difiere (por ejemplo, `GET` sin ordenar o `deprecatedAt` con otro formato), se registra en un
    `BACKEND_GAPS.md` de la 010 y se consulta antes de adaptar.
  - Reactivar en dos llamadas puede quedar a medias: cubierto con `Partial`, aviso y refresco (decisión de la spec).
  - Si la regla local de reordenado no coincide con la del backend, un refresco movería categorías o colores: test del
    cuerpo enviado y comprobación manual de que el refresco tras reordenar no cambia nada.
  - Arrastrar dentro de un `LazyColumn`: se evita pintando las activas del modo de orden en un único ítem (lista corta),
    con el mismo arrastre de `TagList`.
  - El color de respaldo de categorías sin color cambia al reordenar o reactivar: aceptado en la spec; regresión en
    `CategoryColorsTest` de que archivar no cambia el de las demás.
- **Decisiones pendientes:** Ninguna. Resuelta al aprobar el plan (2026-11-24): la copia del contrato
  (`docs/backend/api-contract.md` §3.4.2 y `openapi.yaml`) se actualiza en esta rama, en la etapa 7, copiando del backend
  solo esa sección.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
