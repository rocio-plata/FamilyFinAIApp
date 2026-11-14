# TASKS: Categorías y tags

**SPEC:** `docs/features/005-categories/SPEC.md` (Aprobada)
**PLAN:** `docs/features/005-categories/PLAN.md` (Aprobado)

<!-- Tareas de máx. 20-30 min, en orden de dependencia. Marcar [x] solo tras
ejecutar la validación y registrar en "Evidencia" el resultado real. Si una
tarea queda bloqueada, anotar el motivo en "Evidencia". -->

Rutas relativas a `app/src/main/java/com/rocioplata/familyfinaiapp/` salvo
indicación. Los tests JVM van en `app/src/test/…` y los de UI en
`app/src/androidTest/…`, en el mismo paquete que lo que prueban.

## Dominio

- [ ] **T-01 · Modelos, fallos y contrato del repositorio**
  - **Cubre:** RF-02, RF-03, RF-04, RF-08, RF-13, RF-18, RF-19, RF-21 · **Depende de:** —
  - **Alcance:** `domain/categories/Category.kt` (`Category`, `Tag`, `CategoryType`, `CategoryStatus`, `TagStatus`, `CategoriesData` con lista + `isStale` o sin datos), `CategoryFailure.kt` (`Offline`, `Network`, `InsufficientRole`, `Unauthorized`, `DuplicateName`, `NotFound`, `HasAssociatedItems`, `CategoryNotActive`, `InvalidData`, `Unknown` y el resultado de operación) y `CategoriesRepository.kt` (flujo por familia, `refresh`, las 9 operaciones de cambio y `clear()`). Kotlin puro.
  - **Validación:** `./gradlew :app:assembleDebug` y `grep` de imports `android`/`androidx`/`io.ktor`/`kotlinx.serialization` en `domain/categories`.
  - **Hecho cuando:** compila y el dominio no depende de Android, Compose, DTOs ni datos.
  - **Evidencia:** —

- [ ] **T-02 · Validación de nombres**
  - **Cubre:** RF-09, RF-10, RF-15, RF-16 (CA-09) · **Depende de:** —
  - **Alcance:** `domain/categories/CategoryValidation.kt`: recorte de espacios, vacío y longitud máxima (categoría 50, tag 30). Devuelve el nombre recortado o el error del campo.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.domain.categories.*"` con `CategoryValidationTest` (válido, vacío, solo espacios, recorte, límites 50/51 y 30/31).
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Datos

- [ ] **T-03 · Regla de caché común**
  - **Cubre:** RF-20 · **Depende de:** —
  - **Alcance:** `data/cache/CachePolicy.kt` con las constantes de 24 h y 7 días y sus dos comprobaciones (antigua, caducada). `data/header/HeaderRepositoryImpl.kt` pasa a usarlas en lugar de sus constantes privadas, sin cambio de comportamiento ni de tests.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "*.HeaderRepositoryImplTest" --tests "*.SessionEndDuringHeaderRefreshTest"`.
  - **Hecho cuando:** los tests de cabecera y de fin de sesión pasan sin modificarlos.
  - **Evidencia:** —

- [ ] **T-04 · Data source remoto: listado y traducción de errores**
  - **Cubre:** RF-02, RF-03, RF-04, RF-08 (CA-07) · **Depende de:** T-01
  - **Alcance:** `data/categories/CategoriesRemoteDataSource.kt` con sus DTOs: `GET …/categories?includeDeprecated=true` sobre `apiClient`; mapeo a dominio (`status` o `type` desconocido → `InvalidData`); traducción de `error` a `CategoryFailure` según la tabla del plan (403 de rol, 404, 401, `IOException`, resto). No lanza por red/servidor; relanza `CancellationException`; no lee `message`.
  - **Validación:** `CategoriesRemoteDataSourceTest` (JVM, `ktor-client-mock`): URL y parámetro, listado con tags y archivadas, `icon` nulo, valores desconocidos, cada fila de la tabla de errores, cancelación relanzada.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-05 · Data source remoto: operaciones de categoría**
  - **Cubre:** RF-09, RF-10, RF-12, RF-13 (CA-10) · **Depende de:** T-04
  - **Alcance:** `POST` (`type`, `name`, `icon?`), `PATCH` (solo los campos modificados; `newIcon: null` explícito para quitar el icono), `deprecate` y `DELETE`. Respuestas `204` sin cuerpo. Errores `DuplicateName`, `HasAssociatedItems`, `CategoryNotActive`.
  - **Validación:** `CategoriesRemoteDataSourceTest`: método, ruta y cuerpo de cada llamada (`PATCH` solo con nombre, solo con icono y con `newIcon: null`), `204`, 409 de duplicado y de movimientos asociados.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-06 · Data source remoto: operaciones de tags**
  - **Cubre:** RF-15, RF-16, RF-17, RF-18, RF-19 · **Depende de:** T-04
  - **Alcance:** tags: `POST` (`name`), `PATCH` (`name`), `PUT …/tags/order` (`orderedTagIds`), `deprecate` y `DELETE`. Errores `DuplicateName` (tag), `HasAssociatedItems` (tag), `CategoryNotActive`; el rechazo del orden se trata como `Unknown`.
  - **Validación:** `CategoriesRemoteDataSourceTest`: método, ruta y cuerpo de cada llamada, `204`, y cada error.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-07 · Caché local por familia**
  - **Cubre:** RF-20 (CA-19) · **Depende de:** T-01
  - **Alcance:** `data/categories/CategoriesLocalDataSource.kt`: un JSON por familia en DataStore Preferences con `schemaVersion = 1`, `userId` y `savedAt`; leer, guardar y vaciar todo. Versión desconocida o JSON ilegible = sin datos.
  - **Validación:** `CategoriesLocalDataSourceTest` (JVM): ida y vuelta, dos familias independientes, versión desconocida, JSON corrupto, vaciado.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-08 · Repositorio: lectura, refresco y reglas de caché**
  - **Cubre:** RF-20 (CA-19, CA-23) · **Depende de:** T-03, T-04, T-07
  - **Alcance:** `data/categories/CategoriesRepositoryImpl.kt`: `categories(familyId)` (memoria por familia sembrada desde la caché), `refresh(familyId)` (uno a la vez por familia; guarda con `savedAt`), descarte de caché de otro usuario o caducada e `isStale` con `CachePolicy`. El candado de escritura nunca se mantiene durante la red.
  - **Validación:** `CategoriesRepositoryImplTest` (JVM): sin caché, caché de otro usuario, ≥7 días, >24 h (`isStale`), refresco con éxito y con fallo conservando lo anterior, datos separados por familia, dos refrescos simultáneos → una llamada.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-09 · Repositorio: operaciones de cambio**
  - **Cubre:** RF-09 a RF-19, RF-21 (CA-08, CA-11, CA-15, CA-20) · **Depende de:** T-05, T-06, T-08
  - **Alcance:** las 9 operaciones: sin conexión (`ConnectivityObserver`) devuelven `Offline` sin llamar al backend; con éxito aplican el cambio a memoria y caché (respuesta, o cambio conocido en los `204`) conservando el `savedAt` de la última descarga completa; con fallo no cambian nada. El orden de tags envía los activos en su nuevo orden seguidos de los archivados.
  - **Validación:** `CategoriesRepositoryImplTest`: cada operación actualiza el flujo y la caché; `savedAt` conservado; fallo sin cambios; sin red → `Offline` y cero peticiones; lista de orden completa.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-10 · Cableado, fin de sesión y copias de seguridad**
  - **Cubre:** RF-20 · **Depende de:** T-09
  - **Alcance:** `clear()` del repositorio con candado propio y contador de época (no espera a un refresco en curso). `AppContainer.kt`: DataStore `categories_cache`, `categoriesRepository` y `onSessionEnded` vacía también las categorías. `res/xml/backup_rules.xml` y `data_extraction_rules.xml` excluyen `categories_cache`.
  - **Validación:** test nuevo equivalente a `SessionEndDuringHeaderRefreshTest` para categorías (terminar la sesión durante un refresco no se bloquea, vacía la caché y descarta el resultado en vuelo); `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
  - **Hecho cuando:** compila, toda la suite JVM pasa y lint no añade errores.
  - **Evidencia:** —

## Navegación

- [ ] **T-11 · Cadenas de categorías**
  - **Cubre:** todos los RF con texto · **Depende de:** —
  - **Alcance:** `res/values/strings.xml`: títulos, pestañas con contador, "Gestionar", "Crear nueva categoría", buscador, vacíos y sin resultados, "Archivada"/"Archivado", formulario y contador "n / 50", diálogos (archivar, eliminar, descarte, oferta de archivar), errores por `CategoryFailure`, "requiere conexión", descripciones de accesibilidad (iconos del catálogo, "Subir", "Bajar", asa de arrastre, volver).
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y los textos coinciden con la spec.
  - **Evidencia:** —

- [ ] **T-12 · Ruta de categorías en MainViewModel**
  - **Cubre:** RF-01, RF-06 (CA-01, CA-22) · **Depende de:** —
  - **Alcance:** `ui/main/MainUiState.kt` y `MainViewModel.kt`: ruta en memoria (`null`, `List`, `Create(tipo)`, `Edit(categoryId)`) y sus eventos (abrir, crear, editar, volver un nivel). La ruta vuelve a `null` si `FamiliesState` deja de ser `Content`. Sin `SavedStateHandle`.
  - **Validación:** `MainViewModelTest` ampliado: transiciones de la ruta, volver por niveles, reinicio al perder `Content`; el resto de la clase sigue pasando.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-13 · "Gestionar" en Inicio y pantalla a pantalla completa**
  - **Cubre:** RF-01 (CA-01) · **Depende de:** T-11, T-12
  - **Alcance:** `ui/main/HomeContent.kt`: título "Categorías" + enlace "Gestionar", conservando el texto actual. `ui/main/MainScreen.kt`: con ruta no nula dibuja la gestión de categorías sin cabecera ni barra de pestañas (de momento, una pantalla con barra, flecha y título) y `BackHandler` que vuelve un nivel. Ampliar `TestMainHost` para la ruta.
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest` con `MainScreenTest` ampliado ("Gestionar" abre el listado sin cabecera ni pestañas; flecha y atrás vuelven a Inicio) y regresión de `MainScreenBehaviorTest` y `AppRootTest`.
  - **Hecho cuando:** los tests pasan en emulador.
  - **Evidencia:** —

## Listado

- [ ] **T-14 · Catálogo de iconos**
  - **Cubre:** RF-03, RF-11 (CA-03) · **Depende de:** T-11
  - **Alcance:** `ui/categories/CategoryIcons.kt`: las 15 claves del plan, en el orden del mockup 006, con su icono de `material-icons-extended` y su descripción; clave nula o desconocida → icono genérico. Mismo catálogo para mostrar y para elegir.
  - **Validación:** `CategoryIconsTest` (JVM): clave conocida, nula y desconocida; 15 claves únicas en el orden acordado.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-15 · CategoriesViewModel**
  - **Cubre:** RF-02 a RF-05, RF-20 (CA-02, CA-04, CA-05) · **Depende de:** T-01
  - **Alcance:** `ui/categories/CategoriesUiState.kt` (`Loading`, `Content`, `Error`; vacío y sin resultados dentro de `Content`) y `CategoriesViewModel.kt`: tipo visible ("Gastos" al abrir), contadores de activas por tipo, activas y archivadas del tipo, tags activos por `displayOrder`, búsqueda por nombre o tag sin distinguir mayúsculas ni acentos, sección plegada, `isStale`, refresco al abrir, en `ON_START` y al recuperar conectividad (uno a la vez).
  - **Validación:** `CategoriesViewModelTest` (JVM, repositorio falso): cada punto anterior, incluidos sin resultados, tipo vacío, error sin datos y contenido con caché mientras refresca.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-16 · Listado: estructura, filas y estados**
  - **Cubre:** RF-01, RF-02, RF-03, RF-20 · **Depende de:** T-11, T-14, T-15
  - **Alcance:** `ui/categories/CategoriesContent.kt` (sin estado): barra con flecha, título y lupa; pestañas "Gastos"/"Ingresos" con contador; `LazyColumn` de filas (icono, nombre y tags en una línea con elipsis, área táctil ≥48 dp); carga, error con "Reintentar", vacío por tipo y `StaleDataBanner`. Colores del tema; `@Preview`.
  - **Validación:** `./gradlew :app:assembleDebug :app:lintDebug` y revisión en preview.
  - **Hecho cuando:** compila, lint sin errores nuevos y la preview muestra el listado.
  - **Evidencia:** —

- [ ] **T-17 · Listado: buscador, archivadas, crear y entrada con estado**
  - **Cubre:** RF-04, RF-05, RF-06, RF-07 · **Depende de:** T-13, T-16
  - **Alcance:** campo de búsqueda y mensaje de sin resultados; sección plegable "Categorías archivadas" con cantidad y marca "Archivada" (texto, no solo atenuación), con ⋮ solo para el Propietario; "Crear nueva categoría" e invitación del vacío solo para el Propietario. `ui/categories/CategoriesScreen.kt` (con estado, ViewModel con clave `sesión-familia`) conectada en `MainScreen` con la familia activa; tocar una fila abre `Edit`, crear abre `Create(tipo visible)`.
  - **Validación:** `./gradlew :app:assembleDebug :app:lintDebug` y comprobación en emulador con el backend local (captura del listado).
  - **Hecho cuando:** el listado real de la familia activa se ve en el emulador como Propietario.
  - **Evidencia:** —

- [ ] **T-18 · Tests de UI del listado**
  - **Cubre:** CA-02, CA-03, CA-04, CA-06 · **Depende de:** T-17
  - **Alcance:** `ui/categories/CategoriesContentTest` sobre la función sin estado: pestañas y contadores; icono propio y genérico (por descripción) y tags en orden; archivadas plegadas/desplegadas y ausentes; sin resultados; rol `MEMBER` sin crear ni ⋮; estados de carga, error y vacío.
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest`.
  - **Hecho cuando:** los tests pasan en emulador.
  - **Evidencia:** —

## Alta y edición

- [ ] **T-19 · CategoryEditViewModel: borrador, crear y guardar**
  - **Cubre:** RF-07 a RF-10, RF-14, RF-21 (CA-07, CA-08, CA-09, CA-10, CA-20, CA-21, CA-24) · **Depende de:** T-02, T-09
  - **Alcance:** `ui/categories/CategoryEditUiState.kt` y `CategoryEditViewModel.kt`: modo alta/edición/solo lectura (rol `MEMBER` o categoría archivada); borrador de nombre, icono y tipo; categoría y tags leídos del flujo del repositorio; crear (pasa a edición de la creada) y guardar (solo campos modificados); validación; duplicado junto al campo; una operación a la vez; evento pendiente único; "hay cambios sin guardar"; `InsufficientRole` → aviso y callback de rol; `Offline`, `NotFound` y errores genéricos sin perder el borrador.
  - **Validación:** `CategoryEditViewModelTest` (JVM, repositorio falso): tipo preseleccionado; crear → edición; nombre vacío o de espacios no llama; duplicado; guardar nombre, icono y quitar icono; dos pulsaciones → una llamada; borrador modificado o no; rol insuficiente notifica y no cambia; sin red conserva el borrador; `NotFound` pide volver al listado.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-20 · Pantalla de alta y edición**
  - **Cubre:** RF-08 a RF-11, RF-14 · **Depende de:** T-14, T-17, T-19
  - **Alcance:** `ui/categories/CategoryEditContent.kt` (sin estado): barra con flecha; tipo (elegible en alta, fijo en edición); nombre con límite y contador "n / 50", error en `supportingText`; selector de los 15 iconos con `selected` y opción de quitarlo; "Crear"/"Guardar" con progreso y deshabilitado; solo lectura; columna desplazable con `imePadding`; diálogo de descarte en flecha y `BackHandler`; avisos del evento pendiente. `CategoryEditScreen.kt` (con estado, clave `sesión-familia-categoryId` o "nueva") conectada en `MainScreen`, con `onInsufficientRole` de `MainViewModel`. `CategoryTexts.kt` traduce fallos a `strings.xml`.
  - **Validación:** `./gradlew :app:assembleDebug :app:lintDebug` y prueba en emulador con el backend local: crear una categoría, cambiar nombre e icono, quitar el icono (capturas).
  - **Hecho cuando:** alta y edición funcionan de extremo a extremo en el emulador.
  - **Evidencia:** —

## Archivar y eliminar

- [ ] **T-21 · Archivar y eliminar categorías: lógica**
  - **Cubre:** RF-12, RF-13 (CA-11, CA-12, CA-20) · **Depende de:** T-15, T-19
  - **Alcance:** `CategoryEditViewModel`: archivar y eliminar la categoría activa tras confirmar; con `HasAssociatedItems` publica la oferta de archivar; al terminar pide volver al listado. `CategoriesViewModel`: eliminar una archivada desde su menú; con `HasAssociatedItems` solo aviso. Una operación a la vez; `Offline` y demás fallos con mensaje.
  - **Validación:** `CategoryEditViewModelTest` y `CategoriesViewModelTest`: archivar con éxito; eliminar sin movimientos; activa con movimientos → oferta y archivado al aceptar; archivada con movimientos → solo aviso; sin red.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-22 · Archivar y eliminar categorías: interfaz**
  - **Cubre:** RF-12, RF-13 · **Depende de:** T-20, T-21
  - **Alcance:** acciones "Archivar" y "Eliminar" en la edición (solo Propietario y categoría activa); diálogos de confirmación (archivar advierte que es irreversible), diálogo de oferta de archivar y aviso simple; menú ⋮ de la archivada con "Eliminar" y su confirmación; progreso y acciones deshabilitadas mientras hay operación.
  - **Validación:** `./gradlew :app:assembleDebug :app:lintDebug` y prueba en emulador: archivar y eliminar una categoría de prueba, cancelar ambos diálogos.
  - **Hecho cuando:** los flujos funcionan en el emulador y cancelar no cambia nada.
  - **Evidencia:** —

## Tags

- [ ] **T-23 · Tags: añadir, renombrar, quitar y archivar (lógica)**
  - **Cubre:** RF-15, RF-16, RF-18, RF-19 (CA-14, CA-15, CA-17, CA-20) · **Depende de:** T-19
  - **Alcance:** `CategoryEditViewModel`: añadir (validado, al final, limpia el campo solo si tiene éxito), renombrar, quitar con confirmación → eliminar; con `HasAssociatedItems` oferta de archivar; eliminar un archivado; duplicado junto al campo; tags activos por orden seguidos de los archivados.
  - **Validación:** `CategoryEditViewModelTest`: añadir válido y duplicado; renombrar; quitar sin movimientos; con movimientos → archivar al aceptar; eliminar archivado; dos pulsaciones → una llamada; sin red conserva lo escrito.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-24 · Tags: reordenar (lógica)**
  - **Cubre:** RF-17 (CA-16) · **Depende de:** T-23
  - **Alcance:** mover un tag activo (a una posición, subir, bajar) con cambio optimista; al soltar se envía el orden; si falla se restaura el orden anterior, se informa y se refresca la lista. Los archivados no se mueven.
  - **Validación:** `CategoryEditViewModelTest`: orden enviado tras mover, subir y bajar; límites (primero/último); reversión, aviso y refresco al fallar; sin red.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-25 · Lista de tags: interfaz**
  - **Cubre:** RF-15, RF-16, RF-18, RF-19 · **Depende de:** T-20, T-23
  - **Alcance:** `ui/categories/TagList.kt` dentro de la edición (solo con la categoría creada): tags activos y, después, archivados atenuados con el texto "Archivado"; vacío; campo para añadir con límite de 30 y error; diálogo de renombrar al tocar; quitar con confirmación y oferta de archivar; eliminar un archivado. En solo lectura, sin acciones.
  - **Validación:** `./gradlew :app:assembleDebug :app:lintDebug` y prueba en emulador: añadir, renombrar y quitar tags (captura).
  - **Hecho cuando:** los flujos funcionan en el emulador.
  - **Evidencia:** —

- [ ] **T-26 · Lista de tags: arrastre y alternativa accesible**
  - **Cubre:** RF-17 (CA-16) · **Depende de:** T-24, T-25
  - **Alcance:** asa de arrastre con `pointerInput` y desplazamiento del elemento sobre los tags activos, sin librería; acciones de accesibilidad "Subir" y "Bajar" con la misma lógica; áreas táctiles ≥48 dp.
  - **Validación:** `./gradlew :app:assembleDebug :app:lintDebug` y prueba en emulador: arrastrar, salir y volver a entrar; repetir con tamaño de fuente máximo.
  - **Hecho cuando:** el orden se conserva al volver a entrar.
  - **Evidencia:** —

- [ ] **T-27 · Tests de UI de alta, edición y tags**
  - **Cubre:** CA-06, CA-09, CA-11, CA-13, CA-16, CA-18, CA-22, CA-24 · **Depende de:** T-22, T-26
  - **Alcance:** `ui/categories/CategoryEditContentTest` sobre la función sin estado: rol `MEMBER` sin acciones; categoría archivada en solo lectura como `OWNER` y `MEMBER`; mensaje de duplicado junto al campo; diálogo de archivar (confirmar/cancelar); diálogo de descarte; tags archivados al final, marcados y sin acciones; acciones "Subir"/"Bajar"; icono seleccionado anunciado; `StateRestorationTester` conserva texto e icono.
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest`.
  - **Hecho cuando:** los tests pasan en emulador.
  - **Evidencia:** —

## Validación

- [ ] **T-28 · Comprobaciones automáticas completas**
  - **Cubre:** todos · **Depende de:** T-27
  - **Alcance:** suite completa, incluida la regresión de `HeaderRepositoryImplTest`, `SessionEndDuringHeaderRefreshTest`, `MainViewModelTest`, `MainScreenTest`, `MainScreenBehaviorTest` y `AppRootTest`.
  - **Validación:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` y `./gradlew :app:connectedDebugAndroidTest`.
  - **Hecho cuando:** todo pasa o cada fallo queda registrado con su causa.
  - **Evidencia:** —

- [ ] **T-29 · Pruebas manuales contra el backend**
  - **Cubre:** CA-01, CA-07, CA-08, CA-10, CA-12, CA-16, CA-17, CA-19, CA-20, CA-22, CA-23 · **Depende de:** T-28
  - **Alcance:** emulador con backend local: recorrido completo de alta, edición, tags, archivar y eliminar; modo avión en la consulta (con y sin copia) y en cada acción de cambio; segundo plano y recreación con texto escrito; dos familias; TalkBack en el listado y el reordenado; tamaño de fuente máximo con teclado abierto. CA-07 y los casos "con movimientos" de CA-12/CA-17 solo si se pueden preparar por API en el backend; si no, se anotan como no verificados de extremo a extremo.
  - **Validación:** pasos manuales registrados con el resultado observado y capturas.
  - **Hecho cuando:** cada escenario tiene resultado o motivo de bloqueo.
  - **Evidencia:** —

- [ ] **T-30 · Documentación y revisión final**
  - **Cubre:** CA-01 a CA-24 · **Depende de:** T-29
  - **Alcance:** actualizar `AGENTS.md` (capa de categorías, ruta en memoria, regla de caché común) y `docs/ROADMAP.md` (estado y notas); tabla de cada criterio con su evidencia; confirmar que no se implementó nada fuera de alcance y que SPEC, PLAN y TASKS son coherentes.
  - **Validación:** revisión documental.
  - **Hecho cuando:** cada criterio tiene evidencia o está marcado como no verificado.
  - **Evidencia:** —

## Cobertura de criterios

| Criterio | Tareas |
| --- | --- |
| CA-01 | T-12, T-13, T-29 |
| CA-02 | T-15, T-18 |
| CA-03 | T-14, T-18 |
| CA-04 | T-15, T-18 |
| CA-05 | T-15, T-18 |
| CA-06 | T-18, T-27 |
| CA-07 | T-04, T-19, T-29 |
| CA-08 | T-09, T-19, T-29 |
| CA-09 | T-02, T-19, T-27 |
| CA-10 | T-05, T-19, T-29 |
| CA-11 | T-09, T-21, T-27 |
| CA-12 | T-21, T-29 |
| CA-13 | T-27 |
| CA-14 | T-23 |
| CA-15 | T-09, T-23 |
| CA-16 | T-24, T-27, T-29 |
| CA-17 | T-23, T-29 |
| CA-18 | T-27 |
| CA-19 | T-07, T-08, T-29 |
| CA-20 | T-09, T-19, T-21, T-23, T-24, T-29 |
| CA-21 | T-19, T-23 |
| CA-22 | T-12, T-27, T-29 |
| CA-23 | T-08, T-29 |
| CA-24 | T-19, T-27 |
