# TASKS: Medios de pago

**SPEC:** `docs/features/006-payment-methods/SPEC.md` (Aprobada)
**PLAN:** `docs/features/006-payment-methods/PLAN.md` (Aprobado)

<!-- Tareas de máx. 20-30 min, en orden de dependencia. Marcar [x] solo tras
ejecutar la validación y registrar en "Evidencia" el resultado real. Si una
tarea queda bloqueada, anotar el motivo en "Evidencia". -->

Rutas relativas a `app/src/main/java/com/rocioplata/familyfinaiapp/` salvo
indicación. Los tests JVM van en `app/src/test/…` y los de UI en
`app/src/androidTest/…`, en el mismo paquete que lo que prueban.

## Dominio

- [x] **T-01 · Modelo, fallos y contrato del repositorio**
  - **Cubre:** RF-02, RF-03, RF-09, RF-10, RF-12 · **Depende de:** —
  - **Alcance:** `domain/paymentmethods/PaymentMethod.kt` (`PaymentMethod` con id, nombre y estado; `PaymentMethodStatus`; `PaymentMethodsData` con lista + `isStale` o sin datos), `PaymentMethodFailure.kt` (`Offline`, `Network`, `InsufficientRole`, `Unauthorized`, `DuplicateName`, `InvalidName`, `IsSomeonesDefault`, `HasAssociatedItems`, `NotFound`, `InvalidData`, `Unknown` y el resultado de operación) y `PaymentMethodsRepository.kt` (flujo por familia, `refresh`, crear, renombrar, archivar, eliminar y `clear()`). Kotlin puro.
  - **Validación:** `./gradlew :app:assembleDebug` y `grep` de imports `android`/`androidx`/`io.ktor`/`kotlinx.serialization` en `domain/paymentmethods`.
  - **Hecho cuando:** compila y el dominio no depende de Android, Compose, DTOs ni datos.
  - **Evidencia:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (leído de esta ejecución). `grep -rnE "import (android|androidx|io\.ktor|kotlinx\.serialization)" domain/paymentmethods` → sin resultados (solo importa `kotlinx.coroutines.flow.Flow`, como `domain/categories`). Archivos: `PaymentMethod.kt` (`PaymentMethod`, `PaymentMethodStatus` `ACTIVE`/`DEPRECATED`, `PaymentMethodsData` con `paymentMethods` nulo = sin datos e `isStale`), `PaymentMethodFailure.kt` (los 11 fallos y `PaymentMethodResult.Success/Failure`) y `PaymentMethodsRepository.kt` (flujo por familia, `refresh`, crear, renombrar, archivar, eliminar y `clear()`). Decisión de firma: renombrar, archivar y eliminar devuelven `Unit` (el repositorio aplica el cambio conocido, T-06); crear devuelve el `PaymentMethod` con su id. Sin tests: tarea de tipos sin lógica.

- [x] **T-02 · Validación del nombre**
  - **Cubre:** RF-06, RF-07, RF-13 (CA-08) · **Depende de:** —
  - **Alcance:** `domain/paymentmethods/PaymentMethodValidation.kt`: recorte de espacios, vacío y longitud máxima 40. Devuelve el nombre recortado o el error del campo; expone la constante del límite para el campo y el contador.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.domain.paymentmethods.*"` con `PaymentMethodValidationTest` (válido, vacío, solo espacios, recorte, límites 40/41, espacios extremos que no cuentan).
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.domain.paymentmethods.*"` → BUILD SUCCESSFUL; `PaymentMethodValidationTest`: 6 tests, 0 fallos, 0 errores, 0 omitidos (leído de `app/build/test-results/testDebugUnitTest`, de esta ejecución). `PaymentMethodValidation` (objeto) con `validateName`, que devuelve `PaymentMethodNameValidation.Valid(nombre recortado)` o `Invalid(PaymentMethodNameError.REQUIRED | TOO_LONG)`; constante `MAX_NAME_LENGTH = 40` para limitar el campo y el contador. Casos: válido, vacío, solo espacios (incluye tab y salto de línea), recorte, límites 40/41, espacios extremos que no cuentan para el límite y espacios internos conservados. Los tipos de resultado son propios (no se reutilizan `NameValidation`/`NameError` de categorías) para que `domain/paymentmethods` no dependa de otra funcionalidad. Longitud en unidades UTF-16 (supuesto como en 005: no verificado en el código del backend).

## Datos

- [ ] **T-03 · Data source remoto**
  - **Cubre:** RF-02, RF-03, RF-06 a RF-10 (CA-03, CA-09, CA-10, CA-14) · **Depende de:** T-01
  - **Alcance:** `data/paymentmethods/PaymentMethodsRemoteDataSource.kt` con sus DTOs, sobre `apiClient`: `GET …/payment-methods?includeDeprecated=true`, `POST` (`name`, `201`), `PATCH …/:id` (`name`, `200`), `POST …/:id/deprecate` (`204`) y `DELETE …/:id` (`204`). Mapeo a dominio (`status` desconocido → `InvalidData`) y traducción de `error` según la tabla del plan, aceptando `FINANCIAL_TRACKING.*` y `FINANCIAL.*`. No lanza por red/servidor (la espera excesiva es `Network`); relanza `CancellationException`; no lee `message`; segmentos de ruta codificados (`encodeSlash = true`).
  - **Validación:** `PaymentMethodsRemoteDataSourceTest` (JVM, `ktor-client-mock`): método, ruta, parámetro y cuerpo de cada llamada; listado con activos y archivados en el orden recibido; estado desconocido y JSON ilegible; cada fila de la tabla de errores con ambos prefijos; `IS_SOMEONES_DEFAULT` al archivar y al eliminar; red; cancelación relanzada.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-04 · Caché local por familia**
  - **Cubre:** RF-11 (CA-15) · **Depende de:** T-01
  - **Alcance:** `data/paymentmethods/PaymentMethodsLocalDataSource.kt`: un JSON por familia en DataStore Preferences con `schemaVersion = 1`, `userId` y `savedAt`; leer, guardar, quitar una familia y vaciar todo. Versión desconocida o JSON ilegible = sin datos.
  - **Validación:** `PaymentMethodsLocalDataSourceTest` (JVM): ida y vuelta, lista vacía distinta de sin datos, dos familias independientes, versión desconocida, JSON corrupto, estado desconocido, vaciado.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-05 · Repositorio: lectura, refresco y reglas de caché**
  - **Cubre:** RF-11 (CA-02, CA-15) · **Depende de:** T-03, T-04
  - **Alcance:** `data/paymentmethods/PaymentMethodsRepositoryImpl.kt`: `paymentMethods(familyId)` (memoria por familia sembrada desde la caché), `refresh(familyId)` (uno a la vez por familia; guarda con `savedAt`), descarte de caché de otro usuario o caducada e `isStale` con `CachePolicy`; `clear()` con candado propio y contador de época. El candado de escritura nunca se mantiene durante la red ni se toma de forma reentrante.
  - **Validación:** `PaymentMethodsRepositoryImplTest` (JVM): sin caché, caché de otro usuario, ≥7 días, >24 h (`isStale`), refresco con éxito y con fallo conservando lo anterior, datos separados por familia, dos refrescos simultáneos → una llamada, `clear` durante un refresco.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-06 · Repositorio: operaciones de cambio**
  - **Cubre:** RF-06 a RF-10, RF-12 (CA-07, CA-10, CA-11, CA-12, CA-16) · **Depende de:** T-05
  - **Alcance:** crear, renombrar, archivar y eliminar en `PaymentMethodsRepositoryImpl`: sin conexión devuelven `Offline` sin llamar al backend; con éxito aplican el cambio a memoria y caché conservando el `savedAt` de la última descarga (el creado se añade al final de los activos; el archivado cambia de estado; el eliminado desaparece); un fallo no cambia nada.
  - **Validación:** `PaymentMethodsRepositoryImplTest`: cada operación con éxito (memoria y caché), cada una sin conexión (cero llamadas), fallo sin cambios, `savedAt` conservado, cambio descartado si hubo `clear` durante la llamada.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-07 · Cableado, backups y fin de sesión**
  - **Cubre:** RF-11 · **Depende de:** T-06
  - **Alcance:** `AppContainer.kt`: DataStore `payment_methods_cache`, `paymentMethodsRepository` y `onSessionEnded` también lo vacía. `res/xml/backup_rules.xml` y `data_extraction_rules.xml`: excluir `payment_methods_cache`.
  - **Validación:** test nuevo `session/SessionEndDuringPaymentMethodsRefreshTest` (terminar la sesión durante un refresco no se bloquea y vacía la caché); regresión `SessionEndDuringHeaderRefreshTest` y `SessionEndDuringCategoriesRefreshTest`; `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
  - **Hecho cuando:** la suite JVM pasa y las tres exclusiones de backup coinciden con el nombre del DataStore.
  - **Evidencia:** —

## Navegación

- [ ] **T-08 · Ruta común de gestión**
  - **Cubre:** RF-01 (CA-01, CA-02) · **Depende de:** —
  - **Alcance:** `ui/main/ManagementRoute.kt` (interfaz sellada y `PaymentMethodsRoute`); `CategoriesRoute` la implementa; `MainUiState.categoriesRoute` → `managementRoute`; `MainViewModel`: `openPaymentMethods()` (solo desde las pestañas y con familia activa) y vuelta atrás común, conservando la regla de ruta ligada a la familia y sin cambio de familia mientras está abierta. `MainScreen` y `TestMainHost` se adaptan al renombrado, sin cambio de comportamiento en categorías.
  - **Validación:** `MainViewModelTest` ampliado (abrir, volver, ruta ligada a la familia, sin selector con la gestión abierta, no se abre una gestión sobre otra) y los tests existentes de categorías en él; `./gradlew :app:testDebugUnitTest --tests "*.ui.main.*" --tests "*.ui.categories.*"` y `:app:assembleDebugAndroidTest`.
  - **Hecho cuando:** los tests pasan y los de UI compilan.
  - **Evidencia:** —

- [ ] **T-09 · Sección en Inicio y pantalla vacía**
  - **Cubre:** RF-01 (CA-01) · **Depende de:** T-08
  - **Alcance:** `ui/main/HomeContent.kt`: sección "Medios de pago" + "Gestionar" encima de "Categorías", oculta sin familia activa. `MainScreen`: con `PaymentMethodsRoute` muestra a pantalla completa una pantalla provisional con barra, flecha y `BackHandler` (se sustituye en T-11). Textos en `strings.xml`.
  - **Validación:** `MainScreenTest` ampliado: la sección aparece encima de "Categorías"; "Gestionar" abre la pantalla sin cabecera ni pestañas; flecha y atrás vuelven a Inicio. Regresión de los tests de UI de `ui/main` y `ui/categories` en emulador.
  - **Hecho cuando:** los tests pasan en emulador.
  - **Evidencia:** —

## Listado

- [ ] **T-10 · Estado y ViewModel del listado**
  - **Cubre:** RF-02, RF-03, RF-11 (CA-03, CA-04, CA-15) · **Depende de:** T-06
  - **Alcance:** `ui/paymentmethods/PaymentMethodsUiState.kt` (`Loading`, `Content` con activos, archivados, sección desplegada, `isStale` y refrescando; `Error`) y `PaymentMethodsViewModel.kt`: combina el flujo del repositorio con el estado local; refresca al abrir, en `onStart` y al recuperar conectividad; `retry`; alternar archivados. Sin formulario ni acciones todavía.
  - **Validación:** `PaymentMethodsViewModelTest` (JVM, repositorio falso): carga, contenido en el orden recibido, separación y contador de archivados, vacío, error sin datos con reintento, datos antiguos, refresco al recuperar red, un solo refresco a la vez.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-11 · Pantalla del listado**
  - **Cubre:** RF-01 a RF-03, RF-05, RF-11 (CA-03, CA-04, CA-06) · **Depende de:** T-09, T-10
  - **Alcance:** `ui/paymentmethods/PaymentMethodsScreen.kt` (entrada con estado, clave `sesión-familia`) y `PaymentMethodsContent.kt` (sin estado): barra con flecha y título, lista de activos, sección desplegable "Medios de pago archivados" con cantidad y etiqueta "Archivado", `StaleDataBanner`, estados de carga, vacío y error con "Reintentar". Filas de una línea con elipsis, `LazyColumn`. Sustituye la pantalla provisional de T-09 y se cablea en `MainScreen`.
  - **Validación:** `PaymentMethodsContentTest` (UI): activos en orden; archivados plegados, desplegados y ausentes; carga, vacío, error y aviso de datos antiguos; el contenido no recibe el rol. `PaymentMethodTexts` y previews compilan.
  - **Hecho cuando:** los tests pasan en emulador.
  - **Evidencia:** —

## Alta y renombrado

- [ ] **T-12 · Formulario en el ViewModel**
  - **Cubre:** RF-06, RF-07, RF-12, RF-13 (CA-07 a CA-10, CA-16, CA-17, CA-19) · **Depende de:** T-10
  - **Alcance:** en `PaymentMethodsViewModel`: abrir alta o renombrado, editar el texto, guardar (vacío → error "obligatorio" sin llamar al backend; renombrar sin cambios → cierra sin llamar; éxito → cierra; `DuplicateName`/`InvalidName` → error del campo; `NotFound` → cierra, avisa y refresca; resto → mensaje conservando el texto), una sola operación a la vez, y cierre con confirmación de descarte si el texto recortado difiere del inicial. `ui/paymentmethods/PaymentMethodTexts.kt` con la traducción de fallos.
  - **Validación:** `PaymentMethodsViewModelTest`: cada rama anterior, dos pulsaciones → una llamada, sin conexión con el texto intacto, descarte confirmado y cancelado, cierre directo sin cambios, no se cierra durante el guardado.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-13 · Diálogo del formulario**
  - **Cubre:** RF-06, RF-07, RF-13 (CA-08, CA-09, CA-18, CA-19) · **Depende de:** T-11, T-12
  - **Alcance:** `ui/paymentmethods/PaymentMethodFormDialog.kt`: `AlertDialog` con campo de nombre (límite 40, foco al abrir), contador "n / 40", error junto al campo, Guardar con progreso y Cancelar; atrás y tocar fuera pasan por el mismo cierre; diálogo de confirmación de descarte. Acción "Crear medio de pago" en el listado.
  - **Validación:** `PaymentMethodsContentTest`: abrir el alta, contador, no admite más de 40, aviso de nombre obligatorio, error de duplicado, descarte con Cancelar, atrás y tocar fuera; `StateRestorationTester` con el formulario abierto y texto escrito.
  - **Hecho cuando:** los tests pasan en emulador.
  - **Evidencia:** —

## Archivar y eliminar

- [ ] **T-14 · Acciones en el ViewModel**
  - **Cubre:** RF-08, RF-09, RF-10, RF-12 (CA-11 a CA-14, CA-16, CA-17) · **Depende de:** T-12
  - **Alcance:** en `PaymentMethodsViewModel`: pedir y confirmar archivar y eliminar; `HasAssociatedItems` en un activo → diálogo que ofrece archivar, en un archivado → aviso; `IsSomeonesDefault` → aviso sin cambios; `NotFound` → aviso y refresco; `InsufficientRole` → aviso y `onInsufficientRole`; sin conexión → aviso; una sola operación a la vez.
  - **Validación:** `PaymentMethodsViewModelTest`: cada rama, cancelar no llama al backend, dos confirmaciones → una llamada, aceptar la oferta de archivar.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-15 · Menú ⋮ y diálogos**
  - **Cubre:** RF-04, RF-08, RF-09, RF-10 (CA-05, CA-11, CA-13, CA-14) · **Depende de:** T-13, T-14
  - **Alcance:** en `PaymentMethodsContent`: menú ⋮ por fila con `contentDescription` que incluye el nombre ("Renombrar", "Archivar", "Eliminar" en activos; "Eliminar" en archivados), diálogos de confirmación (archivar advierte que es irreversible), diálogo de oferta de archivar, mensajes de aviso y acciones deshabilitadas con progreso durante una operación.
  - **Validación:** `PaymentMethodsContentTest`: opciones del menú por estado, confirmar y cancelar cada diálogo, texto de irreversibilidad, oferta de archivar, aviso de predeterminado, acciones deshabilitadas en curso.
  - **Hecho cuando:** los tests pasan en emulador.
  - **Evidencia:** —

## Validación

- [ ] **T-16 · Comprobaciones automáticas completas**
  - **Cubre:** todos · **Depende de:** T-07, T-15
  - **Alcance:** suite completa, incluida la regresión de categorías, cabecera, sesión, `MainScreenTest`, `MainScreenBehaviorTest` y `AppRootTest`. Lint sin avisos nuevos: cada cadena `payment_methods_*` se usa o se elimina, sin `tools:ignore`; la línea base heredada (24 avisos al cerrar la 005) no se toca.
  - **Validación:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` y `./gradlew :app:connectedDebugAndroidTest`; lectura de `app/build/reports/lint-results-debug.xml`.
  - **Hecho cuando:** todo pasa o cada fallo queda registrado con su causa, y lint no supera la línea base.
  - **Evidencia:** —

- [ ] **T-17 · Pruebas manuales contra el backend**
  - **Cubre:** CA-01, CA-02, CA-06, CA-07, CA-09 a CA-16, CA-18 · **Depende de:** T-16
  - **Alcance:** emulador con backend local: listado de una familia (nombres del backend), alta, renombrado, duplicado con otras mayúsculas y con el nombre de un archivado, archivar y eliminar un medio creado, archivar y eliminar "Efectivo" (error de predeterminado y comprobación del prefijo real del código, BG-04), dos familias, modo avión en la consulta (con y sin copia) y en cada acción, segundo plano con el formulario abierto, TalkBack en el listado y el formulario, tamaño de fuente máximo con teclado. CA-06 (Integrante) y el caso "con movimientos" de CA-13 solo si se pueden preparar por API en el backend; si no, se anotan como no verificados de extremo a extremo.
  - **Validación:** pasos manuales registrados con el resultado observado y capturas.
  - **Hecho cuando:** cada escenario tiene resultado o motivo de bloqueo.
  - **Evidencia:** —

- [ ] **T-18 · Documentación y revisión final**
  - **Cubre:** CA-01 a CA-19 · **Depende de:** T-17
  - **Alcance:** `AGENTS.md` (capa de medios de pago, ruta común de gestión, caché nueva), `docs/ROADMAP.md` (estado y notas), `BACKEND_GAPS.md` (resultado de BG-04 y lo observado en ejecución) y revisión de coherencia entre SPEC, PLAN y TASKS.
  - **Validación:** revisión documental: cada criterio con evidencia o marcado como no verificado; rutas y nombres citados existen.
  - **Hecho cuando:** documentos coherentes con lo implementado.
  - **Evidencia:** —

## Cobertura de criterios

| Criterio | Tareas |
| --- | --- |
| CA-01 | T-08, T-09, T-17 |
| CA-02 | T-05, T-08, T-17 |
| CA-03 | T-03, T-10, T-11 |
| CA-04 | T-10, T-11 |
| CA-05 | T-15 |
| CA-06 | T-11, T-17 |
| CA-07 | T-06, T-12, T-17 |
| CA-08 | T-02, T-12, T-13 |
| CA-09 | T-03, T-12, T-13, T-17 |
| CA-10 | T-03, T-06, T-12, T-17 |
| CA-11 | T-06, T-14, T-15, T-17 |
| CA-12 | T-06, T-14, T-17 |
| CA-13 | T-14, T-15, T-17 |
| CA-14 | T-03, T-14, T-15, T-17 |
| CA-15 | T-04, T-05, T-10, T-17 |
| CA-16 | T-06, T-12, T-14, T-17 |
| CA-17 | T-12, T-14 |
| CA-18 | T-13, T-17 |
| CA-19 | T-12, T-13 |
