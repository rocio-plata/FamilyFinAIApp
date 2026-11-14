# PLAN: Categorías y tags

**SPEC de referencia:** `docs/features/005-categories/SPEC.md`
**Versión de la spec revisada:** SPEC aprobada el 2026-11-11, con RF-01 a RF-21 y CA-01 a CA-24 (sin commit propio al redactar este plan; rama `feature/005-categories` sobre `e7d4ac7`).
**Estado:** Aprobado <!-- Borrador | En revisión | Aprobado -->

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

Rutas relativas a `app/src/main/java/com/rocioplata/familyfinaiapp/` salvo indicación.

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| `AppContainer` | `AppContainer.kt` | DI manual: `apiClient` (Bearer + renovación), `connectivityObserver`, `sessionManager` (con `onSessionEnded = { headerRepository.clear() }`), `headerRepository`, DataStore `header_cache`. Se amplía con el repositorio de categorías, su DataStore y su borrado al terminar la sesión. |
| `MainScreen` (con estado, sin estado y `MainScreenContent`) | `ui/main/MainScreen.kt` | Dibuja cabecera, pestañas y cuerpo; la pestaña vive en `remember`. `BackHandler` solo fuera de Inicio. Aquí se decide entre las pestañas y la gestión de categorías. |
| `MainViewModel` / `MainUiState` / `FamiliesState` | `ui/main/` | Familia activa en memoria (`FamiliesState.Content.activeFamily`, con `role`), refresco en `ON_START` y al recuperar red. Ya expone `onInsufficientRole()`, que vuelve a consultar las familias sin cambiar la activa (RF-08). |
| `HomeContent` | `ui/main/HomeContent.kt` | Placeholder "Inicio · Familia". Recibe la sección "Categorías" con "Gestionar" (RF-01). |
| `StaleDataBanner` | `ui/main/StaleDataBanner.kt` | Aviso de datos antiguos; no tiene estado propio, se reutiliza en el listado (RF-20). |
| `HeaderRemoteDataSource` | `data/header/HeaderRemoteDataSource.kt` | Patrón de data source remoto: nunca lanza por red/servidor, relanza cancelación, lee solo `error` del cuerpo. Su `RemoteResult` está ligado a `HeaderFailure`, por lo que no se reutiliza tal cual. |
| `HeaderLocalDataSource` / `HeaderRepositoryImpl` | `data/header/` | Patrón de caché: sobre JSON con `schemaVersion`, `userId`, `savedAt`; descarte por otro usuario o ≥7 días, `isStale` >24 h; `clear()` con candado propio y contador de época para no interbloquear el fin de sesión. Las constantes de 24 h / 7 días son privadas del repositorio. |
| `SessionProvider` / `SessionManager` | `session/` | `current()` da el `userId` para validar la caché; lanza `SessionUnavailableException`. |
| `ConnectivityObserver` | `data/network/ConnectivityObserver.kt` | `isOnline: Flow<Boolean>`; se usa para refrescar al recuperar red y para RF-21. |
| `AuthValidation` / `AuthTexts` | `domain/auth/`, `ui/auth/` | Patrón de validación pura en dominio y de traducción de fallos a `strings.xml`. |
| Backups | `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Excluyen `header_cache` y `session`. Hay que excluir la caché nueva. |
| Dependencias | `gradle/libs.versions.toml` | Compose BOM 2026.02.01 (Material 3 y `material-icons-extended`), lifecycle 2.10.0, Ktor 3.3.3, kotlinx-serialization 1.9.0, DataStore 1.3.0. **No hay** librería de navegación, de arrastre ni Room. |
| Tests de referencia | `app/src/test/.../data/header/*Test.kt`, `ui/main/MainViewModelTest.kt`; `app/src/androidTest/.../ui/main/TestMainHost.kt`, `MainScreenTest.kt` | Unitarios JVM con `ktor-client-mock` y `kotlinx-coroutines-test`; UI sobre las funciones sin estado. |
| Contrato backend | `docs/backend/api-contract.md` §3.4.2, §4, §9; `docs/backend/openapi.yaml` (`Category`, `Tag`) | 10 operaciones de categorías y tags. `Category.status` es un `string` sin enumerar en OpenAPI; `Tag.displayOrder` es entero. Errores `{error, message}`. |
| Comportamiento del backend | Informado por la persona el 2026-11-11 a partir del código del backend (no verificado por el agente) | `status` es `ACTIVE` o `DEPRECATED`. El `GET` devuelve siempre todos los tags de cada categoría, también los archivados; `includeDeprecated` solo filtra categorías. `PUT …/tags/order` exige exactamente el conjunto completo de tags de la categoría, archivados incluidos, sin duplicados (`InvalidTagOrderError` si no). Las 9 categorías por defecto son todas `EXPENSE` y nacen con `icon: null`. |

**Convenciones y patrón de referencia:** paquetes por capa y funcionalidad;
entrada con estado + contenido sin estado; `StateFlow` +
`collectAsStateWithLifecycle`; ViewModels con `viewModelFactory` leyendo
`AppContainer`; data sources que no lanzan por red; textos en `strings.xml`;
colores en `ui/theme`. Patrón de referencia: la 002
(`docs/features/002-main-header/PLAN.md`) para datos y caché, y la 003 para
formularios.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de datos y eventos hasta la interfaz. Usa un diagrama si aporta claridad. -->

**Navegación (decisión que fuerza el roadmap).** No se añade librería de
navegación. `MainViewModel` guarda en memoria una ruta de gestión de
categorías: `null` (pestañas), `List`, `Create(tipo)` o `Edit(categoryId)`. La
entrada con estado de `MainScreen` muestra `MainScreenContent` cuando es `null`
y, en otro caso, la gestión de categorías a pantalla completa, sin cabecera ni
barra de pestañas (RF-01). Motivos: son solo dos niveles, la ruta sobrevive a
la recreación porque vive en el ViewModel (tabla mobile de la spec) y se pierde
al terminar el proceso, que es lo pedido ("la app abre en Inicio"). Una
librería de navegación se decidirá cuando haya más pantallas (Movimientos).

**Familia activa compartida (decisión que fuerza el roadmap).** No se crea un
contenedor global: la gestión de categorías se dibuja dentro de `MainScreen` y
recibe la `Family` activa (id y rol) como parámetro desde `MainUiState`. El rol
llega siempre del repositorio de cabecera, de modo que al actualizarse (RF-08)
la pantalla pasa sola a solo lectura. Si `FamiliesState` deja de ser `Content`,
la ruta vuelve a `null`.

**Capas.**

```
ui/categories  CategoriesScreen ── CategoriesViewModel ─┐
               CategoryEditScreen ─ CategoryEditViewModel┤
domain/categories   CategoriesRepository (interfaz), modelos, CategoryFailure, CategoryValidation
data/categories     CategoriesRepositoryImpl ─ CategoriesRemoteDataSource (apiClient)
                                             └ CategoriesLocalDataSource (DataStore categories_cache)
```

- El repositorio expone `categories(familyId): Flow<CategoriesData>` (lista +
  `isStale`, o sin datos) y `refresh(familyId)`. Cada operación de cambio es
  una función `suspend` que devuelve éxito o un `CategoryFailure` tipado y, si
  tiene éxito, aplica el cambio al estado en memoria y a la caché, de modo que
  listado y detalle se actualizan sin recargar.
- Un solo `GET …/categories?includeDeprecated=true` trae ambos tipos, las
  archivadas y los tags; pestañas, contadores, sección de archivadas y
  buscador (RF-02 a RF-05) se calculan en el ViewModel sobre esa lista.
- `CategoriesViewModel` (listado): combina el flujo del repositorio con el
  tipo visible, el texto de búsqueda, la sección plegada y el refresco en
  curso. Refresca al abrir, en `ON_START` y al recuperar conectividad.
- `CategoryEditViewModel` (alta/edición): borrador de nombre, icono y tipo;
  la categoría y sus tags los lee del mismo flujo del repositorio. Ejecuta las
  operaciones de una en una y publica un único "evento" pendiente (mensaje o
  diálogo) que la pantalla consume.
- Ambos ViewModels se crean con clave `sesión-familia` (y `categoryId` o
  "nueva" en la edición), por lo que cambiar de familia o de usuario nunca
  reutiliza estado. Al cerrar la edición se descarta su borrador.

**Regla de caché común.** Se extraen las constantes de 24 h y 7 días a
`data/cache/CachePolicy.kt` y `HeaderRepositoryImpl` pasa a usarlas, para que
la regla sea una sola en toda la app (decisión confirmada en la spec). No se
cambia nada más de la cabecera.

**Catálogo de iconos.** `ui/categories/CategoryIcons.kt` asocia claves
estables (las que guarda el backend) a iconos de `material-icons-extended`.
Clave ausente o desconocida → icono genérico (RF-03). Las 15 claves
(confirmadas; nombres de Material Icons), en el orden del mockup 006: `shopping_cart`, `restaurant`,
`local_cafe`, `medical_services`, `home`, `directions_car`, `local_shipping`,
`bolt`, `flight`, `fitness_center`, `sports_soccer`, `beach_access`, `school`,
`pets`, `card_giftcard`. El backend no define claves propias: solo guarda las
que envía la app, y las 9 categorías por defecto nacen con `icon: null`
(confirmado), así que muestran el genérico hasta que el Propietario elija uno. El catálogo
de visualización y el del selector son el mismo.

**Reordenar tags (RF-17).** Implementación propia con el asa de arrastre
(`pointerInput` + desplazamiento del elemento) sobre una columna, sin librería:
la lista es corta y no hay ninguna configurada. Cada tag expone además las
acciones de accesibilidad "Subir" y "Bajar". Solo se mueven los tags activos,
pero al soltar se envía la lista completa de ids de la categoría: los activos
en su nuevo orden seguidos de los archivados en su orden actual (el backend
rechaza una lista incompleta). Si falla se restaura el orden anterior, se
informa y se refresca la lista, por si otro miembro cambió los tags.

## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

| Módulo | Existe / nuevo | Responsabilidad y cambios | Dependencias afectadas |
| --- | --- | --- | --- |
| No aplica: módulo único `app/` | Existente | Paquetes nuevos por capa dentro de `app/` | Ninguna |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son propuestas.
Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `domain/categories/Category.kt` | Crear | `Category`, `Tag`, `CategoryType`, `CategoryStatus`/`TagStatus`, `CategoriesData` | RF-02 a RF-04, RF-19 |
| `domain/categories/CategoryFailure.kt` | Crear | Fallos tipados: `Offline`, `Network`, `InsufficientRole`, `Unauthorized`, `DuplicateName`, `NotFound`, `HasAssociatedItems`, `CategoryNotActive`, `InvalidData`, `Unknown`; y el resultado de operación | RF-08, RF-13, RF-18, RF-21 |
| `domain/categories/CategoryValidation.kt` | Crear | Recorte de espacios, vacío y longitud (50 / 30) | RF-09, RF-10, RF-15, RF-16 |
| `domain/categories/CategoriesRepository.kt` | Crear | Interfaz: flujo por familia, `refresh`, 9 operaciones de cambio y `clear()` | RF-09 a RF-21 |
| `data/cache/CachePolicy.kt` | Crear | 24 h / 7 días y sus dos comprobaciones | RF-20 |
| `data/header/HeaderRepositoryImpl.kt` | Modificar | Usar `CachePolicy` en lugar de sus constantes privadas; sin cambio de comportamiento | RF-20 |
| `data/categories/CategoriesRemoteDataSource.kt` | Crear | Las 10 llamadas del contrato y la traducción de `error` a `CategoryFailure` | RF-09 a RF-19 |
| `data/categories/CategoriesLocalDataSource.kt` | Crear | Un JSON por familia con `schemaVersion`, `userId`, `savedAt` | RF-20 |
| `data/categories/CategoriesRepositoryImpl.kt` | Crear | Reglas de caché, estado en memoria por familia, aplicación de cambios, `clear()` sin esperar a la red | RF-20, RF-21 |
| `AppContainer.kt` | Modificar | DataStore `categories_cache`, `categoriesRepository`, y `onSessionEnded` también lo vacía | RF-20 |
| `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | Modificar | Excluir `categories_cache` de las copias de seguridad | RF-20 |
| `ui/main/MainViewModel.kt`, `MainUiState.kt` | Modificar | Ruta de categorías en memoria y sus eventos (abrir, editar, crear, volver) | RF-01, RF-06 |
| `ui/main/MainScreen.kt` | Modificar | Elegir entre pestañas y gestión de categorías; pasar familia activa y `onInsufficientRole` | RF-01, RF-08 |
| `ui/main/HomeContent.kt` | Modificar | Título de sección "Categorías" + enlace "Gestionar"; se conserva el texto actual | RF-01 |
| `ui/categories/CategoriesViewModel.kt`, `CategoriesUiState.kt` | Crear | Estado del listado (carga, contenido, error; vacío y sin resultados dentro de contenido) | RF-02 a RF-07, RF-13, RF-20 |
| `ui/categories/CategoriesScreen.kt`, `CategoriesContent.kt` | Crear | Entrada con estado y contenido sin estado: barra con flecha y lupa, pestañas, crear, lista, archivadas con ⋮ | RF-01 a RF-07, RF-13 |
| `ui/categories/CategoryEditViewModel.kt`, `CategoryEditUiState.kt` | Crear | Borrador, operaciones, evento pendiente, modo alta/edición/solo lectura | RF-07 a RF-19, RF-21 |
| `ui/categories/CategoryEditScreen.kt`, `CategoryEditContent.kt`, `TagList.kt` | Crear | Formulario, selector de icono, lista de tags con arrastre, diálogos de confirmación y de descarte | RF-09 a RF-19 |
| `ui/categories/CategoryIcons.kt`, `CategoryTexts.kt` | Crear | Catálogo de iconos y traducción de fallos a textos | RF-03, RF-11 |
| `res/values/strings.xml` | Modificar | Textos nuevos (títulos, acciones, errores, descripciones de accesibilidad) | Todos |
| `AGENTS.md`, `docs/ROADMAP.md` | Modificar | Describir la capa nueva, la ruta en memoria y la regla de caché común; estado de la funcionalidad | — |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Modelos y contratos de entrada y salida:** los de §3.4.2. `GET
  …/categories?includeDeprecated=true`; `POST` (`type`, `name`, `icon?`);
  `PATCH` (`newName?`, `newIcon` con `null` explícito para quitarlo: solo se
  envían los campos modificados); `deprecate`; `DELETE`; tags: `POST`
  (`name`), `PATCH` (`name`), `PUT …/tags/order` (`orderedTagIds` con todos los tags de
  la categoría, archivados incluidos), `deprecate`, `DELETE`. Las respuestas `204` no traen cuerpo.
- **Identificadores, relaciones y restricciones:** categoría y tag se
  identifican por su `id`; un tag pertenece a una categoría; todo cuelga del
  `familyId` activo. `status` es `ACTIVE` o `DEPRECATED` (archivado); otro valor invalida la
  respuesta, igual que
  un `type` desconocido (`InvalidData`).
- **Origen de los datos mostrados y transformaciones:** una sola lista por
  familia; en el ViewModel se separa por tipo y estado, se cuentan las
  activas, se ordenan los tags por `displayOrder` y se filtra por texto sin
  distinguir mayúsculas ni acentos.
- **Persistencia, consultas y actualizaciones:** DataStore Preferences
  `categories_cache`, una clave por familia. Tras cada cambio con éxito se
  actualizan memoria y caché con la respuesta (o con el cambio conocido en los
  `204`), conservando el `savedAt` de la última descarga completa.
- **Convivencia entre datos locales y remotos:** el backend manda; la caché
  solo sirve para leer (RF-20). No se guarda ningún cambio pendiente (RF-21),
  por lo que no hay conflictos que resolver.
- **Compatibilidad y migraciones de datos existentes:** caché nueva con
  `schemaVersion = 1`; versión desconocida o JSON ilegible = sin datos. La
  caché de cabecera no cambia de formato.

Traducción de errores (`error` del cuerpo; `message` no se lee):

| Respuesta | `CategoryFailure` | Efecto en la interfaz |
| --- | --- | --- |
| `FINANCIAL.INSUFFICIENT_ROLE` o `FAMILY.INSUFFICIENT_ROLE` | `InsufficientRole` | Aviso + `MainViewModel.onInsufficientRole()` (RF-08) |
| `FINANCIAL.DUPLICATE_CATEGORY_NAME`, `FINANCIAL.DUPLICATE_TAG_NAME` | `DuplicateName` | Mensaje junto al campo |
| `FINANCIAL.CATEGORY_HAS_ASSOCIATED_ITEMS`, `FINANCIAL.TAG_HAS_ASSOCIATED_ITEMS` | `HasAssociatedItems` | Oferta de archivar o solo aviso (RF-13, RF-18) |
| `FINANCIAL.CATEGORY_NOT_ACTIVE` | `CategoryNotActive` | Aviso y refresco de la lista |
| `404` (cualquier código) | `NotFound` | Aviso, refresco y vuelta al listado |
| `401` tras la renovación | `Unauthorized` | Lo resuelve la sesión (003) |
| `IOException` | `Network` | Error con reintento |
| Sin conexión antes de enviar | `Offline` | "Requiere conexión" (RF-21) |
| Resto, incluida la espera excesiva | `Unknown` | Error genérico con reintento |

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de MOBILE_GUIDELINES.md. -->

- **Gestión del estado de interfaz y navegación:** ruta en `MainViewModel`
  (ver solución). `BackHandler` en cada nivel: edición → listado → pestañas;
  la flecha de la barra hace lo mismo (CA-01). En alta/edición, si el borrador
  difiere de lo guardado, atrás y la flecha abren el diálogo de descarte
  (CA-24). Listado: `CategoriesUiState` con `Loading`, `Content` y `Error`.
  Solo lectura = rol `MEMBER` o categoría archivada (RF-07, RF-14).
- **Conservación y restauración del estado:** ruta, pestaña, búsqueda y
  borrador viven en ViewModels, que sobreviven a la recreación y al segundo
  plano (CA-22). No se usa `SavedStateHandle`: tras terminar el proceso la app
  abre en Inicio sin borrador, como pide la spec.
- **Ejecución, concurrencia y cancelación de operaciones:** todo en
  `viewModelScope`. Refresco: uno a la vez por familia (se ignora si ya hay
  uno). Cambios: mientras hay una operación en curso el ViewModel rechaza
  otra y la interfaz deshabilita las acciones y muestra progreso (CA-21). El
  repositorio serializa las escrituras de caché con un candado que nunca se
  mantiene durante la red; `clear()` usa el mismo esquema de época que la
  cabecera para no esperar a un refresco en curso.
- **Errores, reintentos y prevención de duplicados:** tabla anterior. Antes de
  enviar un cambio se consulta `ConnectivityObserver`; sin red se devuelve
  `Offline` sin llamar al backend. Ningún fallo borra lo escrito. Un alta que
  falla por red no se reintenta sola (evita duplicados). El reordenado es
  optimista y se revierte si falla.
- **Otras consideraciones mobile aplicables y su solución:** validación en
  `CategoryValidation` y límite de longitud en el campo; `imePadding` y
  contenido desplazable para que el teclado no tape el campo ni "Guardar";
  filas con una línea y elipsis; listas en `LazyColumn`; áreas táctiles ≥48
  dp; `contentDescription` por icono del catálogo, `selected` en el elegido,
  texto "Archivada"/"Archivado" además de la atenuación; acciones de
  accesibilidad "Subir"/"Bajar" (RF-17). Sin nombres de categorías ni tags en
  logs. Colores solo del tema.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

- Sin dependencias nuevas ni cambios en `libs.versions.toml`. Los iconos salen
  de `material-icons-extended`, ya configurada.
- Sin permisos nuevos.
- Nuevo archivo DataStore `categories_cache`, excluido de las copias de
  seguridad igual que `header_cache`.
- No se usa Room (el contrato lo sugiere en §5, pero no es obligatorio y el
  proyecto ya resuelve la caché con DataStore).

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, UI o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

Todos los tests son nuevos (propuestos). Unitarios en `app/src/test/…`, de UI
en `app/src/androidTest/…/ui/categories/` sobre las funciones sin estado con
un repositorio falso.

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | UI: `MainScreenTest` (ampliado) — "Gestionar" abre el listado sin cabecera ni pestañas; flecha y atrás vuelven. Manual en emulador | Emulador | Test + captura |
| CA-02 | Unit: `CategoriesViewModelTest`. UI: `CategoriesContentTest` | JVM / emulador | Tests |
| CA-03 | Unit: `CategoryIconsTest` (clave conocida, nula, desconocida). UI: `CategoriesContentTest` | JVM / emulador | Tests + captura |
| CA-04 | Unit: `CategoriesViewModelTest`. UI: `CategoriesContentTest` | JVM / emulador | Tests |
| CA-05 | Unit: `CategoriesViewModelTest` (nombre, tag, sin resultados, acentos) | JVM | Test |
| CA-06 | UI: `CategoriesContentTest` y `CategoryEditContentTest` con rol `MEMBER` | Emulador | Tests |
| CA-07 | Unit: `CategoriesRemoteDataSourceTest` (403), `CategoryEditViewModelTest` (aviso, sin cambio, notifica rol). Manual: degradar el rol desde otra cuenta | JVM; backend con dos cuentas | Tests + pasos manuales |
| CA-08 | Unit: `CategoryEditViewModelTest`, `CategoriesRepositoryImplTest`. Manual contra backend | JVM; backend | Tests + captura |
| CA-09 | Unit: `CategoryValidationTest`, `CategoryEditViewModelTest` (409). UI: mensaje junto al campo | JVM / emulador | Tests |
| CA-10 | Unit: `CategoriesRemoteDataSourceTest` (cuerpo de `PATCH`, `newIcon: null`), `CategoryEditViewModelTest`. Manual | JVM; backend | Tests + captura |
| CA-11 | Unit: `CategoryEditViewModelTest`, `CategoriesRepositoryImplTest`. UI: diálogo confirmar/cancelar | JVM / emulador | Tests |
| CA-12 | Unit: `CategoryEditViewModelTest` y `CategoriesViewModelTest` (sin movimientos, activa con movimientos, archivada con movimientos) | JVM | Tests |
| CA-13 | UI: `CategoryEditContentTest` con categoría archivada, `OWNER` y `MEMBER` | Emulador | Test |
| CA-14 | Unit: `CategoryEditViewModelTest` (éxito, 409) | JVM | Test |
| CA-15 | Unit: `CategoryEditViewModelTest`, `CategoriesRepositoryImplTest` | JVM | Tests |
| CA-16 | Unit: `CategoryEditViewModelTest` (orden enviado, reversión). UI: acciones "Subir"/"Bajar". Manual: arrastre, salir y volver | JVM / emulador; backend | Tests + pasos manuales |
| CA-17 | Unit: `CategoryEditViewModelTest` (eliminar; con movimientos → archivar) | JVM | Test |
| CA-18 | UI: `CategoryEditContentTest` | Emulador | Test |
| CA-19 | Unit: `CategoriesLocalDataSourceTest`, `CategoriesRepositoryImplTest` (otro usuario, 7 días, 24 h, sin caché). Manual: modo avión | JVM; emulador | Tests + pasos manuales |
| CA-20 | Unit: `CategoryEditViewModelTest` (sin red no se llama al backend, borrador intacto). Manual: modo avión | JVM; emulador | Tests + pasos manuales |
| CA-21 | Unit: `CategoryEditViewModelTest` (dos pulsaciones, una llamada) | JVM | Test |
| CA-22 | UI: `StateRestorationTester` sobre el contenido; manual: cambiar tamaño de fuente y segundo plano | Emulador | Test + pasos manuales |
| CA-23 | Unit: `CategoriesRepositoryImplTest` (datos por familia). Manual con dos familias | JVM; backend | Test + pasos manuales |
| CA-24 | Unit: `CategoryEditViewModelTest` (borrador modificado o no). UI: diálogo de descarte | JVM / emulador | Tests |

**Comprobaciones de regresión:** `HeaderRepositoryImplTest` y
`SessionEndDuringHeaderRefreshTest` (por `CachePolicy` y el nuevo
`onSessionEnded`), `MainViewModelTest`, `MainScreenTest`,
`MainScreenBehaviorTest`, `AppRootTest`. Test nuevo: terminar la sesión vacía
la caché de categorías.

**Comandos verificados para compilar y ejecutar tests:** los de `AGENTS.md`
(`./gradlew :app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`,
`:app:connectedDebugAndroidTest`). No se ejecutaron al redactar este plan.

**Pruebas en dispositivo, emulador o simulador:** emulador con el backend
local (`DEV_TARGET`, `scripts/dev-reverse.sh`): alta, edición, tags, archivar,
eliminar, modo avión, TalkBack sobre el listado y el reordenado, y tamaño de
fuente máximo. No hace falta hardware real.

**Limitaciones del entorno:** CA-07 y CA-12/CA-17 "con movimientos" dependen
de datos que la app aún no puede crear (cambio de rol, movimientos): se
cubrirán con tests y, si se pueden preparar en el backend por API, también a
mano; si no, quedarán anotados como no verificados de extremo a extremo.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. Dominio: modelos, fallos y `CategoryValidation` con sus tests.
2. Datos: `CachePolicy` (+ ajuste de cabecera), data sources remoto y local,
   repositorio, `AppContainer` y exclusión de backups. Tests unitarios y
   regresión de cabecera y sesión.
3. Navegación mínima: ruta en `MainViewModel`, "Gestionar" en Inicio y
   pantalla vacía con flecha. Comprobación: CA-01.
4. Listado en solo lectura: pestañas, contadores, archivadas, buscador,
   estados y aviso de datos antiguos. Comprobación: CA-02 a CA-06, CA-19.
5. Alta y edición de nombre e icono, descarte y roles. Comprobación: CA-08 a
   CA-10, CA-21, CA-22, CA-24, CA-07.
6. Archivar y eliminar categorías (edición y menú ⋮). Comprobación: CA-11 a
   CA-13.
7. Tags: añadir, renombrar, quitar/archivar, archivados y reordenar.
   Comprobación: CA-14 a CA-18.
8. Sin conexión en las acciones, accesibilidad y texto grande. Comprobación:
   CA-20 y revisión con TalkBack.
9. Validación completa en emulador, `lintDebug`, y actualización de
    `AGENTS.md`, `ROADMAP.md` y `TASKS.md` con la evidencia.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - *Orden de tags con cambios ajenos.* Si otro Propietario añade o elimina
    un tag mientras se reordena, la lista enviada ya no coincide y el backend
    la rechaza. Medida: revertir, informar y refrescar (ver "Reordenar tags").
    El código de error de ese rechazo no está en el contrato: se trata como
    error genérico.
  - *Arrastre propio.* Sin librería, el gesto puede ser frágil con texto
    grande o listas largas. Medida: las acciones "Subir"/"Bajar" comparten la
    misma lógica y sirven de alternativa; prueba manual específica.
  - *Fin de sesión.* Un segundo repositorio en `onSessionEnded` no debe
    reintroducir el interbloqueo corregido en la 003. Medida: mismo esquema
    de época y test equivalente a `SessionEndDuringHeaderRefreshTest`.
  - *Ruta en `MainViewModel`.* `TestMainHost` reproduce a mano el ViewModel:
    habrá que ampliarlo para la ruta.
- **Decisiones pendientes:** Ninguna.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
