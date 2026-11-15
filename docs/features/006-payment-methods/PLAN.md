# PLAN: Medios de pago

**SPEC de referencia:** `docs/features/006-payment-methods/SPEC.md`
**Versión de la spec revisada:** aprobada el 2026-11-14 (aún sin commit)
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
| `AppContainer` | `AppContainer.kt` | DI manual: `apiClient`, `connectivityObserver`, `sessionManager` (su `onSessionEnded` vacía cabecera y categorías), DataStores `header_cache` y `categories_cache`. Se amplía con el repositorio de medios de pago, su DataStore y su borrado al terminar la sesión. |
| `MainViewModel` / `MainUiState` / `CategoriesRoute` | `ui/main/` | La gestión de categorías es una ruta en memoria (`categoriesRoute`) ligada a la familia con la que se abrió: se descarta si esa familia deja de ser la activa, y mientras está abierta no se puede cambiar de familia. Expone `onInsufficientRole()`. Se generaliza para admitir la gestión de medios de pago. |
| `MainScreen` (con estado y sin estado) | `ui/main/MainScreen.kt` | Con ruta abierta dibuja la gestión a pantalla completa (sin cabecera ni pestañas) con `BackHandler`; la sobrecarga sin estado recibe el contenido como parámetro (`categoriesContent`). Aquí se elige también la pantalla de medios de pago. |
| `HomeContent` | `ui/main/HomeContent.kt` | Sección "Categorías" con "Gestionar" (título `titleMedium` con semántica de encabezado + `TextButton`). Se añade encima la sección "Medios de pago" con el mismo patrón (RF-01). |
| `StaleDataBanner` | `ui/main/StaleDataBanner.kt` | Aviso de datos antiguos, sin estado; se reutiliza en el listado (RF-11). |
| `CachePolicy` | `data/cache/CachePolicy.kt` | Regla común: `isStale` (>24 h) e `isExpired` (≥7 días). Se reutiliza tal cual. |
| Capa de categorías | `domain/categories/`, `data/categories/`, `ui/categories/` | Patrón de referencia: repositorio con `Flow` por familia, `refresh` y cambios con resultado tipado; data source remoto que nunca lanza por red y acepta los prefijos `FINANCIAL_TRACKING.*` y `FINANCIAL.*`; caché JSON por familia con `schemaVersion`, `userId` y `savedAt`; `clear()` con candado propio y contador de época; ViewModel con evento pendiente; entrada con estado + contenido sin estado. Sus tipos (`CategoryFailure`, `CategoryResult`) y sus funciones de envío son propios de categorías y no se reutilizan. |
| `SessionProvider` / `ConnectivityObserver` | `session/`, `data/network/` | `userId` para validar la caché; `isOnline` para refrescar al recuperar red y para RF-12. |
| Backups | `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Excluyen `header_cache`, `categories_cache` y `session`. Hay que excluir la caché nueva. |
| Dependencias | `gradle/libs.versions.toml` | Compose + Material 3, lifecycle, Ktor, kotlinx-serialization, DataStore. Sin librería de navegación ni Room. |
| Tests de referencia | `app/src/test/…/data/categories/*Test.kt`, `ui/categories/CategoriesViewModelTest.kt`, `session/SessionEndDuringCategoriesRefreshTest.kt`, `ui/main/MainViewModelTest.kt`; `app/src/androidTest/…/ui/main/TestMainHost.kt`, `MainScreenTest.kt`, `ui/categories/CategoriesContentTest.kt` | Unitarios JVM con `ktor-client-mock` y `kotlinx-coroutines-test`; UI sobre las funciones sin estado. |
| Contrato backend | `docs/backend/api-contract.md` §3.4.1, §4, §9; `docs/backend/openapi.yaml` | 5 operaciones usadas: listar, crear, renombrar, archivar y eliminar. Errores `{error, message}`. |
| Comportamiento del backend | Leído el 2026-11-14 en `familyFinAI-backend/src/contexts/financial-tracking` (no probado en ejecución); ver `BACKEND_GAPS.md` | Errores con prefijo `FINANCIAL_TRACKING.*` (BG-04). Archivar y eliminar responden `409 …PAYMENT_METHOD_IS_SOMEONES_DEFAULT`, comprobado antes que los movimientos asociados (BG-03). Duplicado sin distinguir mayúsculas y contando archivados (BG-06). El nombre se recorta y se limita a 40 (`INVALID_PAYMENT_METHOD_NAME`). Renombrar no comprueba el estado del medio. Quien no es miembro recibe un error de rol insuficiente. |

**Convenciones y patrón de referencia:** paquetes por capa y funcionalidad;
entrada con estado + contenido sin estado; `StateFlow` +
`collectAsStateWithLifecycle`; ViewModels con `viewModelFactory` leyendo
`AppContainer`; data sources que no lanzan por red; textos en `strings.xml`;
colores en `ui/theme`. Patrón de referencia: la 005
(`docs/features/005-categories/PLAN.md`).

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de datos y eventos hasta la interfaz. Usa un diagrama si aporta claridad. -->

**Navegación.** No se añade librería. La ruta en memoria de `MainViewModel`
pasa a cubrir las dos gestiones: se crea la interfaz sellada
`ManagementRoute` (`ui/main/`), que implementan la `CategoriesRoute` existente
y un nuevo `PaymentMethodsRoute` (un único destino, el listado). El campo
`MainUiState.categoriesRoute` se renombra a `managementRoute`. Motivo: con un
solo campo las dos gestiones son excluyentes por construcción y comparten sin
duplicarla la regla ya probada (ruta ligada a la familia, sin cambio de
familia mientras está abierta, vuelta a las pestañas si la familia
desaparece). El alta y el renombrado no son rutas: son un diálogo sobre el
listado (RF-06, RF-07). El renombrado del campo toca `MainScreen`,
`TestMainHost` y sus tests, sin cambiar el comportamiento de categorías.

**Capas** (paquetes nuevos, mismo esquema que categorías):

```
ui/paymentmethods      PaymentMethodsScreen ── PaymentMethodsViewModel
domain/paymentmethods  PaymentMethodsRepository (interfaz), PaymentMethod,
                       PaymentMethodFailure, PaymentMethodValidation
data/paymentmethods    PaymentMethodsRepositoryImpl ─ PaymentMethodsRemoteDataSource (apiClient)
                                                    └ PaymentMethodsLocalDataSource (DataStore payment_methods_cache)
```

- El repositorio expone `paymentMethods(familyId): Flow<PaymentMethodsData>`
  (lista + `isStale`, o sin datos) y `refresh(familyId)`. Crear, renombrar,
  archivar y eliminar son funciones `suspend` que devuelven éxito o un
  `PaymentMethodFailure` y, si tienen éxito, aplican el cambio a memoria y a
  la caché, de modo que el listado se actualiza sin recargar.
- Un solo `GET …/payment-methods?includeDeprecated=true` trae activos y
  archivados; la separación y el contador de archivados (RF-02, RF-03) se
  calculan en el ViewModel, conservando el orden recibido.
- `PaymentMethodsViewModel` combina el flujo del repositorio con el estado
  local: sección de archivados desplegada, refresco en curso, formulario
  (alta o renombrado, texto, error del campo, guardando), diálogo pendiente
  (confirmar archivar, confirmar eliminar, ofrecer archivar, confirmar
  descarte) y un mensaje pendiente. Refresca al abrir, en `ON_START` y al
  recuperar conectividad. Se crea con clave `sesión-familia`.
- Formulario: `AlertDialog` de Material 3 con un campo de texto, contador
  "n / 40" y Guardar/Cancelar. Se elige diálogo y no bottom sheet porque con
  un solo campo el teclado no desplaza el contenido y el cierre (atrás, tocar
  fuera, Cancelar) se intercepta en un único punto para RF-13.

**Sin extracción de código común.** El envío HTTP y la lectura de `error` se
repiten en el data source nuevo en lugar de extraerlos de categorías: los
fallos son tipos distintos por funcionalidad y extraerlos sería un refactor de
la 005 ajeno a este alcance.

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
| `domain/paymentmethods/PaymentMethod.kt` | Crear | `PaymentMethod` (id, nombre, estado), `PaymentMethodStatus`, `PaymentMethodsData` | RF-02, RF-03 |
| `domain/paymentmethods/PaymentMethodFailure.kt` | Crear | Fallos tipados y resultado de operación (ver tabla de errores) | RF-09, RF-10, RF-12 |
| `domain/paymentmethods/PaymentMethodValidation.kt` | Crear | Recorte de espacios, vacío y longitud máxima 40 | RF-06, RF-07, RF-13 |
| `domain/paymentmethods/PaymentMethodsRepository.kt` | Crear | Interfaz: flujo por familia, `refresh`, 4 operaciones de cambio y `clear()` | RF-02 a RF-12 |
| `data/paymentmethods/PaymentMethodsRemoteDataSource.kt` | Crear | Las 5 llamadas del contrato y la traducción de `error` a `PaymentMethodFailure` (ambos prefijos) | RF-02, RF-06 a RF-10 |
| `data/paymentmethods/PaymentMethodsLocalDataSource.kt` | Crear | Un JSON por familia con `schemaVersion`, `userId`, `savedAt` | RF-11 |
| `data/paymentmethods/PaymentMethodsRepositoryImpl.kt` | Crear | Reglas de caché (`CachePolicy`), estado en memoria por familia, aplicación de cambios, comprobación de conexión, `clear()` sin esperar a la red | RF-11, RF-12 |
| `data/cache/CachePolicy.kt` | Reutilizar | Sin cambios | RF-11 |
| `AppContainer.kt` | Modificar | DataStore `payment_methods_cache`, `paymentMethodsRepository`, y `onSessionEnded` también lo vacía | RF-11 |
| `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | Modificar | Excluir `payment_methods_cache` | RF-11 |
| `ui/main/ManagementRoute.kt` | Crear | Interfaz sellada común y `PaymentMethodsRoute` | RF-01 |
| `ui/main/CategoriesRoute.kt` | Modificar | Implementa `ManagementRoute`; sin otros cambios | RF-01 |
| `ui/main/MainViewModel.kt`, `MainUiState.kt` | Modificar | `managementRoute` (antes `categoriesRoute`), `openPaymentMethods()` y vuelta atrás común | RF-01 |
| `ui/main/MainScreen.kt` | Modificar | Elegir la pantalla según la ruta; pasar `onManagePaymentMethods`; el contenido inyectable de la sobrecarga sin estado recibe una `ManagementRoute` | RF-01 |
| `ui/main/HomeContent.kt` | Modificar | Sección "Medios de pago" + "Gestionar" encima de "Categorías" | RF-01 |
| `ui/paymentmethods/PaymentMethodsViewModel.kt`, `PaymentMethodsUiState.kt` | Crear | Estado del listado (`Loading`, `Content`, `Error`), formulario, diálogos y mensaje pendiente | RF-02 a RF-13 |
| `ui/paymentmethods/PaymentMethodsScreen.kt`, `PaymentMethodsContent.kt` | Crear | Entrada con estado y contenido sin estado: barra con flecha, "Crear medio de pago", lista con menú ⋮, sección de archivados, aviso de datos antiguos, estados de carga, vacío y error | RF-01 a RF-05, RF-08 a RF-12 |
| `ui/paymentmethods/PaymentMethodFormDialog.kt` | Crear | Diálogo de alta y renombrado con contador y error junto al campo | RF-06, RF-07, RF-13 |
| `ui/paymentmethods/PaymentMethodTexts.kt` | Crear | Traducción de fallos a textos | RF-09, RF-10, RF-12 |
| `res/values/strings.xml` | Modificar | Textos nuevos (títulos, acciones, errores, descripciones de accesibilidad) | Todos |
| `app/src/androidTest/…/ui/main/TestMainHost.kt`, `MainScreenTest.kt`; `app/src/test/…/ui/main/MainViewModelTest.kt` | Modificar | Adaptar al renombrado de la ruta y cubrir la nueva sección | RF-01 |
| `AGENTS.md`, `docs/ROADMAP.md` | Modificar | Describir la capa nueva y la ruta común; estado de la funcionalidad | — |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Modelos y contratos de entrada y salida:** los de §3.4.1. `GET
  …/payment-methods?includeDeprecated=true` → lista de `{id, familyId, name,
  status}`; `POST` (`name`) → `201` con el medio; `PATCH …/:id` (`name`) →
  `200` con el medio; `POST …/:id/deprecate` → `204`; `DELETE …/:id` → `204`.
  No se usa `PUT …/me/default-payment-method` (fuera de alcance).
- **Identificadores, relaciones y restricciones:** el medio se identifica por
  su `id` y cuelga del `familyId` activo. `status` es `ACTIVE` o `DEPRECATED`
  (archivado); otro valor invalida la respuesta (`InvalidData`).
- **Origen de los datos mostrados y transformaciones:** una sola lista por
  familia; el ViewModel la separa por estado sin reordenarla. Los nombres se
  muestran como llegan (RF-02).
- **Persistencia, consultas y actualizaciones:** DataStore Preferences
  `payment_methods_cache`, una clave por familia. Tras cada cambio con éxito
  se actualizan memoria y caché con la respuesta (o con el cambio conocido en
  los `204`), conservando el `savedAt` de la última descarga completa. Un
  medio creado se añade al final de los activos.
- **Convivencia entre datos locales y remotos:** el backend manda; la caché
  solo sirve para leer (RF-11). No se guarda ningún cambio pendiente (RF-12).
- **Compatibilidad y migraciones de datos existentes:** caché nueva con
  `schemaVersion = 1`; versión desconocida o JSON ilegible = sin datos. Las
  cachés existentes no cambian.

Traducción de errores (`error` del cuerpo; `message` no se lee). `*.X`
significa `FINANCIAL_TRACKING.X` o `FINANCIAL.X` (BG-04):

| Respuesta | `PaymentMethodFailure` | Efecto en la interfaz |
| --- | --- | --- |
| `*.DUPLICATE_PAYMENT_METHOD_NAME` | `DuplicateName` | Mensaje junto al campo; el formulario sigue abierto (CA-09) |
| `*.INVALID_PAYMENT_METHOD_NAME` | `InvalidName` | Mensaje junto al campo (no debería ocurrir: la app valida antes) |
| `*.PAYMENT_METHOD_IS_SOMEONES_DEFAULT` | `IsSomeonesDefault` | Aviso; el medio no cambia (RF-10) |
| `*.PAYMENT_METHOD_HAS_ASSOCIATED_ITEMS` | `HasAssociatedItems` | Activo: diálogo que ofrece archivar; archivado: solo aviso (RF-09) |
| `404` (cualquier código) | `NotFound` | Aviso, cierre del formulario y refresco de la lista |
| `*.INSUFFICIENT_ROLE` o `FAMILY.INSUFFICIENT_ROLE` | `InsufficientRole` | Aviso + `MainViewModel.onInsufficientRole()`: si el usuario ya no es miembro, la familia desaparece y la ruta se cierra sola |
| `401` tras la renovación | `Unauthorized` | Lo resuelve la sesión (003) |
| `IOException`, incluida la espera excesiva | `Network` | Error con reintento |
| Sin conexión antes de enviar | `Offline` | "Requiere conexión" (RF-12) |
| Resto | `Unknown` | Error genérico |

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de MOBILE_GUIDELINES.md. -->

- **Gestión del estado de interfaz y navegación:** `managementRoute` en
  `MainViewModel`; `BackHandler` y flecha vuelven a las pestañas (CA-01).
  `PaymentMethodsUiState`: `Loading`, `Content` (activos, archivados, sección
  desplegada, `isStale`, refrescando) y `Error`; el vacío es `Content` sin
  activos. El formulario y los diálogos son estado del ViewModel, no de la
  composición. Menú ⋮: tres acciones en activos, "Eliminar" en archivados
  (RF-04), iguales para ambos roles (RF-05).
- **Conservación y restauración del estado:** ruta, sección desplegada,
  formulario abierto y su texto viven en ViewModels, que sobreviven a la
  recreación y al segundo plano (CA-18). No se usa `SavedStateHandle`: tras
  terminar el proceso la app abre en Inicio, como pide la spec.
- **Ejecución, concurrencia y cancelación de operaciones:** todo en
  `viewModelScope`. Refresco: uno a la vez por familia. Cambios: mientras hay
  una operación en curso el ViewModel rechaza otra y la interfaz deshabilita
  las acciones y muestra progreso (CA-17). El repositorio serializa las
  escrituras de caché con un candado que no se mantiene durante la red;
  `clear()` usa el esquema de época de categorías para no esperar a un
  refresco en curso.
- **Errores, reintentos y prevención de duplicados:** tabla anterior. Antes de
  enviar un cambio se consulta `ConnectivityObserver`; sin red se devuelve
  `Offline` sin llamar al backend. Ningún fallo cierra el formulario ni borra
  lo escrito, salvo `NotFound`. Un alta que falla por red no se reintenta
  sola.
- **Formulario (RF-13):** "Guardar" está siempre habilitado salvo durante el
  guardado; con nombre vacío o solo espacios no se llama al backend y se
  muestra "El nombre es obligatorio" junto al campo. Renombrar sin cambiar el
  nombre cierra el formulario sin llamar al backend. Cancelar, atrás y tocar
  fuera pasan por el mismo punto: si el texto recortado difiere del inicial
  (vacío en el alta, el nombre actual al renombrar) se abre la confirmación de
  descarte; si no, se cierra (CA-19). Durante el guardado no se puede cerrar.
- **Otras consideraciones mobile aplicables y su solución:** límite de 40 en
  el campo; filas de una línea con elipsis; lista en `LazyColumn`; áreas
  táctiles ≥48 dp; el menú ⋮ lleva `contentDescription` con el nombre del
  medio; texto "Archivado" además de la atenuación; foco en el campo al abrir
  el formulario. Sin nombres de medios de pago en logs. Colores solo del tema.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

- Sin dependencias nuevas ni cambios en `libs.versions.toml`.
- Sin permisos nuevos.
- Nuevo archivo DataStore `payment_methods_cache`, excluido de las copias de
  seguridad igual que los existentes.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, UI o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

Salvo `MainViewModelTest`, `MainScreenTest` y `TestMainHost` (existentes, se
amplían), todos los tests son nuevos (propuestos). Unitarios en
`app/src/test/…`, de UI en `app/src/androidTest/…/ui/paymentmethods/` sobre
las funciones sin estado.

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Unit: `MainViewModelTest` (abrir y volver). UI: `MainScreenTest` (sección encima de "Categorías", pantalla sin cabecera ni pestañas, flecha y atrás). Manual | JVM / emulador | Tests + captura |
| CA-02 | Unit: `MainViewModelTest` (ruta ligada a la familia), `PaymentMethodsRepositoryImplTest` (datos por familia). Manual con dos familias | JVM; backend | Tests + pasos manuales |
| CA-03 | Unit: `PaymentMethodsRemoteDataSourceTest`, `PaymentMethodsViewModelTest` (orden conservado) | JVM | Tests |
| CA-04 | Unit: `PaymentMethodsViewModelTest`. UI: `PaymentMethodsContentTest` | JVM / emulador | Tests |
| CA-05 | UI: `PaymentMethodsContentTest` (menú de un activo y de un archivado) | Emulador | Test |
| CA-06 | UI: `PaymentMethodsContentTest` (el contenido no depende del rol). Manual con una cuenta Integrante | Emulador; backend con dos cuentas | Test + pasos manuales |
| CA-07 | Unit: `PaymentMethodsViewModelTest`, `PaymentMethodsRepositoryImplTest`. Manual | JVM; backend | Tests + captura |
| CA-08 | Unit: `PaymentMethodValidationTest`, `PaymentMethodsViewModelTest` (vacío no llama al backend). UI: aviso y contador | JVM / emulador | Tests |
| CA-09 | Unit: `PaymentMethodsRemoteDataSourceTest` (409 con ambos prefijos), `PaymentMethodsViewModelTest`. Manual: otras mayúsculas y nombre de un archivado | JVM; backend | Tests + pasos manuales |
| CA-10 | Unit: `PaymentMethodsRemoteDataSourceTest` (cuerpo `name`), `PaymentMethodsViewModelTest`. Manual | JVM; backend | Tests + captura |
| CA-11 | Unit: `PaymentMethodsViewModelTest`, `PaymentMethodsRepositoryImplTest`. UI: diálogo confirmar/cancelar. Manual | JVM / emulador; backend | Tests + captura |
| CA-12 | Unit: `PaymentMethodsViewModelTest`, `PaymentMethodsRepositoryImplTest`. Manual | JVM; backend | Tests + captura |
| CA-13 | Unit: `PaymentMethodsViewModelTest` (activo → oferta de archivar; archivado → aviso) | JVM | Test |
| CA-14 | Unit: `PaymentMethodsRemoteDataSourceTest`, `PaymentMethodsViewModelTest` (archivar y eliminar). Manual con "Efectivo" de una familia nueva | JVM; backend | Tests + captura |
| CA-15 | Unit: `PaymentMethodsLocalDataSourceTest`, `PaymentMethodsRepositoryImplTest` (otro usuario, 7 días, 24 h, sin caché). Manual: modo avión | JVM; emulador | Tests + pasos manuales |
| CA-16 | Unit: `PaymentMethodsRepositoryImplTest` (sin red no se llama al backend), `PaymentMethodsViewModelTest` (texto intacto). Manual: modo avión | JVM; emulador | Tests + pasos manuales |
| CA-17 | Unit: `PaymentMethodsViewModelTest` (dos pulsaciones, una llamada) | JVM | Test |
| CA-18 | UI: `StateRestorationTester` sobre el contenido con el formulario abierto. Manual: rotación desactivada, cambiar tamaño de fuente y segundo plano | Emulador | Test + pasos manuales |
| CA-19 | Unit: `PaymentMethodsViewModelTest` (con y sin cambios). UI: Cancelar, atrás y tocar fuera | JVM / emulador | Tests |

**Comprobaciones de regresión:** `MainViewModelTest`, `MainScreenTest`,
`MainScreenBehaviorTest`, `CategoriesContentTest`, `CategoryEditContentTest` y
`AppRootTest` (por el renombrado de la ruta); `SessionEndDuringHeaderRefreshTest`
y `SessionEndDuringCategoriesRefreshTest` (por el nuevo `onSessionEnded`). Test
nuevo equivalente: terminar la sesión durante un refresco de medios de pago no
se bloquea y vacía su caché.

**Comandos verificados para compilar y ejecutar tests:** los de `AGENTS.md`
(`./gradlew :app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`,
`:app:connectedDebugAndroidTest`). No se ejecutaron al redactar este plan.

**Pruebas en dispositivo, emulador o simulador:** emulador con el backend
local (`DEV_TARGET`, `scripts/dev-reverse.sh`): listado de una familia nueva,
alta, renombrado, duplicado, archivar, eliminar, error de predeterminado con
"Efectivo", modo avión, TalkBack sobre el listado y el formulario, y tamaño de
fuente máximo. No hace falta hardware real.

**Limitaciones del entorno:** el caso "con movimientos" de CA-13 depende de
datos que la app aún no puede crear; CA-06 necesita un Integrante, y la app
aún no gestiona invitaciones. Se cubrirán con tests y, si se pueden preparar
en el backend por API, también a mano; si no, quedarán anotados como no
verificados de extremo a extremo.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. Dominio: modelo, fallos y `PaymentMethodValidation` con sus tests.
2. Datos: data sources remoto y local, repositorio, `AppContainer` y exclusión
   de backups. Tests unitarios y regresión de fin de sesión.
3. Navegación: `ManagementRoute`, renombrado en `MainViewModel`/`MainScreen`,
   sección en Inicio y pantalla vacía con flecha. Comprobación: CA-01, CA-02 y
   regresión de categorías.
4. Listado: activos, archivados, estados de carga, vacío y error, aviso de
   datos antiguos. Comprobación: CA-03, CA-04, CA-15.
5. Alta y renombrado con el formulario, validación y descarte. Comprobación:
   CA-07 a CA-10, CA-18, CA-19.
6. Menú ⋮, archivar y eliminar con sus errores. Comprobación: CA-05, CA-11 a
   CA-14, CA-17.
7. Sin conexión en las acciones, accesibilidad y texto grande. Comprobación:
   CA-16 y revisión con TalkBack.
8. Validación completa en emulador, `lintDebug`, y actualización de
   `AGENTS.md`, `ROADMAP.md` y `TASKS.md` con la evidencia.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - *Renombrado de la ruta.* Cambiar `categoriesRoute` por `managementRoute`
    toca código y tests de la 005. Medida: hacerlo en una etapa propia (3) y
    pasar antes de seguir toda la regresión de categorías y de `MainScreen`.
  - *"Efectivo" inamovible.* En una familia nueva no puede archivarse ni
    eliminarse hasta que exista la spec de Perfil (BG-01, BG-05). Medida: es
    el comportamiento aceptado en la spec (RF-10); el mensaje lo explica.
  - *Códigos de error.* El prefijo real se leyó en el código del backend, no
    se probó. Medida: aceptar ambos prefijos y comprobarlo a mano contra el
    backend local en la validación (CA-09, CA-14).
  - *Cambios de otro miembro.* Un medio puede haber sido archivado o eliminado
    por otro miembro. Medida: `NotFound` refresca la lista; renombrar un medio
    ya archivado lo acepta el backend y el refresco siguiente lo muestra en
    archivados.
  - *Fin de sesión.* Un tercer repositorio en `onSessionEnded` no debe
    reintroducir el interbloqueo corregido en la 003. Medida: mismo esquema
    de época y test equivalente.
- **Decisiones pendientes:** Ninguna.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
