# AGENTS.md - FamilyFinAIApp

## Alcance y prioridad

Estas instrucciones aplican a todo el repositorio. Las instrucciones más
específicas dentro de subdirectorios tienen prioridad para sus archivos. Las
indicaciones explícitas del usuario tienen prioridad sobre este documento.

Antes de planificar, revisar o cambiar código, leer íntegramente
`docs/GENERIC_RULES.md` y las reglas móviles relevantes en
`docs/MOBILE_GUIDELINES.md`, además de la especificación o plan asociado si
existe. No inventar requisitos; consultar cuando una decisión afecte el
comportamiento observable, el alcance o la persistencia.

## Proyecto

- Es una aplicación Android llamada `FamilyFinAIApp`.
- El módulo principal es `app/`.
- El código de producción está escrito en Kotlin y usa Jetpack Compose con
  Material 3.
- La configuración de dependencias se centraliza en
  `gradle/libs.versions.toml`; usar el Gradle Wrapper (`./gradlew`) en lugar de
  una instalación global de Gradle.
- Mantener el `namespace` y el `applicationId`
  `com.rocioplata.familyfinaiapp`, salvo que exista un requisito explícito para
  cambiarlos.
- Respetar los SDK y versiones configurados en los archivos Gradle. No
  actualizar dependencias o herramientas sin una necesidad concreta.
- No asumir que una librería o una arquitectura descrita en documentación
  externa ya está configurada: verificar primero el código y
  `gradle/libs.versions.toml`.

## Entorno de desarrollo

Los builds debug dependen de la tarea `checkDevProperties` (enganchada a
`preDebugBuild`), que falla si `local.properties` (no versionado) no define
`DEV_EMAIL` y `DEV_PASSWORD`, o si `DEV_TARGET` no es `emulator`
(`http://10.0.2.2:3000`) o `device` (`http://localhost:3000`, requiere
`adb reverse tcp:3000 tcp:3000`). `adb reverse` también sirve para el emulador:
con `DEV_TARGET=device` fijo, `scripts/dev-reverse.sh` lo aplica a todos los
dispositivos y emuladores conectados (repetir tras reiniciarlos).
`BACKEND_BASE_URL` lo sobrescribe. Estos valores llegan como campos
`BuildConfig`; en release quedan vacíos. Nunca escribir sus valores en código,
logs ni documentación.

minSdk 34, target/compileSdk 36, Java 11.

## Convenciones de implementación

- Seguir la estructura existente de paquetes bajo
  `app/src/main/java/com/rocioplata/familyfinaiapp/`.
- Preferir APIs de Compose, Material 3 y AndroidX ya configuradas antes de
  introducir nuevas dependencias.
- Mantener funciones pequeñas, estado explícito y tipos seguros. No ocultar
  errores con `catch` vacíos, valores ficticios o fallbacks silenciosos.
- Para nuevas pantallas o flujos, definir los estados de carga, contenido,
  vacío, error y éxito cuando correspondan.
- Considerar ciclo de vida, conservación de estado, conectividad, accesibilidad,
  tamaños de pantalla, permisos, privacidad y rendimiento según
  `docs/MOBILE_GUIDELINES.md`.
- No incluir secretos, credenciales ni datos personales innecesarios en código,
  logs, fixtures o documentación.
- Usar corrutinas estructuradas y `viewModelScope` cuando se incorporen
  ViewModels. No capturar ni ocultar `CancellationException`; si se captura
  una excepción amplia, relanzar la cancelación antes de manejar otros
  errores.

## Arquitectura y UI

- Mantener separadas las capas de presentación, dominio y datos cuando el
  tamaño de la funcionalidad lo justifique. El dominio no debe depender de
  Android, Compose, DTOs ni implementaciones de datos.
- Mantener el acceso a datos y la lógica de negocio fuera de los composables.
  Preferir una entrada de pantalla con estado y una función de contenido
  stateless que reciba estado y callbacks.
- Para pantallas con estado observable, usar `StateFlow` y
  `collectAsStateWithLifecycle()` cuando las dependencias configuradas lo
  permitan.
- Definir explícitamente los estados válidos de cada pantalla (por ejemplo,
  carga, contenido, vacío y error) y conservar el patrón establecido por la
  funcionalidad existente.
- Colocar colores y tipografías reutilizables en `app/src/main/.../ui/theme/`;
  evitar valores hexadecimales hardcodeados en las pantallas.
- Antes de agregar navegación, revisar la solución existente y no introducir
  dos stacks de navegación diferentes sin una decisión documentada.

## Arquitectura actual del código

Paquetes bajo `app/src/main/java/com/rocioplata/familyfinaiapp/`, organizados por capa y
luego por funcionalidad (`domain/header`, `data/header`, `ui/main`), no según la estructura
`core/` + `features/` propuesta en el README.

- **DI manual**: `FamilyFinApp` (Application) crea un `AppContainer` con el `HttpClient` de
  Ktor (OkHttp, kotlinx.serialization con `ignoreUnknownKeys`, timeouts), DataStore,
  `ConnectivityObserver`, `SessionManager` y los repositorios. Hay dos `HttpClient`: uno público
  (login, registro, renovación) y otro de API con el plugin `SessionAuth`. Los ViewModels se crean en la
  entrada de pantalla con `viewModelFactory { initializer { ... } }` leyendo el container
  desde `LocalContext.current.applicationContext`.
- **Sesión**: `SessionManager` (ámbito de aplicación, `session/`) es la única fuente de verdad:
  expone `StateFlow<SessionState>` (`Loading`, `SignedOut(motivo?)`, `SignedIn(userId, generación)`)
  y es el `SessionProvider` de los repositorios (`current()` lanza
  `SessionUnavailableException(SessionFailure)`). Lee la sesión guardada una vez por proceso, la
  guarda cifrada con AES-GCM y clave del Keystore (`EncryptedSessionStore`, excluida de las copias
  de seguridad) y, bajo un `Mutex`, hace una sola renovación ante varios 401 (el backend rota el
  refresh token y revoca todas las sesiones si se reutiliza uno). El plugin `SessionAuth`
  (`data/network`) añade el Bearer y, ante un 401, renueva una vez y reintenta. Si el backend
  rechaza la renovación, la sesión termina con motivo `Expired` o `Security` y se borran sesión y
  caché de cabecera (`onSessionEnded`); sin red la sesión se conserva. `endSession` no debe
  esperar al refresco de cabecera en curso: `HeaderRepositoryImpl.clear()` usa su propio candado y
  un contador de época (ver `SessionEndDuringHeaderRefreshTest`). Al terminar la sesión también se vacían las cachés de categorías, medios de pago,
  movimientos y miembros (`AppContainer.onSessionEnded`), cada una con el mismo esquema de época
  (`SessionEndDuring…RefreshTest`). `Session`, los DTOs de auth y
  `RefreshOutcome` sobrescriben `toString()` para no filtrar tokens.
- **Datos**: los data sources remotos devuelven `RemoteResult.Success/Failure` con fallos de
  dominio tipados (`HeaderFailure`) y nunca lanzan por red/servidor; sí relanzan
  `CancellationException`. Los repositorios exponen un `Flow` de datos y un `suspend refresh()`
  que devuelve un resultado por recurso (cada recurso se refresca de forma independiente).
- **Caché offline** (`HeaderLocalDataSource`): un JSON por recurso en DataStore Preferences con
  `schemaVersion`, `userId` y `savedAt`. El repositorio descarta caché de otro usuario o de
  ≥7 días y marca `isStale` a partir de 24 h. Versión desconocida o JSON ilegible = sin datos.
- **Categorías** (`domain/categories`, `data/categories`, `ui/categories`): misma separación por
  capas que la cabecera. `CategoriesRepositoryImpl` expone un `Flow` por familia y `refresh`,
  y las mutaciones (crear, editar, archivar, eliminar categorías; añadir, renombrar, quitar,
  archivar y reordenar tags) devuelven un resultado tipado (`CategoryFailure`). La caché es
  `categories_cache` (DataStore, excluida de las copias de seguridad en `backup_rules.xml` y
  `data_extraction_rules.xml`) y comparte con la cabecera la regla de `data/cache/CachePolicy`
  (descarta a los 7 días, `isStale` a partir de 24 h). El backend emite los errores de dominio
  como `FINANCIAL_TRACKING.*` aunque el contrato documente `FINANCIAL.*`: se aceptan ambos
  prefijos (`BACKEND_GAPS.md` de la 005, BG-06). Los cambios requieren conexión y no hay cola
  offline; lo escrito se conserva ante un fallo.
- **Medios de pago** (`domain/paymentmethods`, `data/paymentmethods`, `ui/paymentmethods`): misma
  separación por capas que categorías, sin código compartido con ella (los fallos son tipos
  distintos por funcionalidad). `PaymentMethodsRepositoryImpl` expone un `Flow` por familia
  (`paymentMethods`) y `refresh`, y las mutaciones (crear, renombrar, archivar y eliminar) devuelven
  `PaymentMethodResult` con un `PaymentMethodFailure` tipado; sin conexión devuelven `Offline` sin
  llamar al backend (los cambios no tienen cola offline). Un solo `GET …?includeDeprecated=true`
  trae activos y archivados y el orden es el del backend. La caché es `payment_methods_cache`
  (DataStore, una clave por familia, `schemaVersion = 1`, excluida de las copias de seguridad en
  `backup_rules.xml` y `data_extraction_rules.xml`) y sigue `data/cache/CachePolicy`. Los errores
  de dominio se aceptan con el prefijo `FINANCIAL_TRACKING.*` que emite el backend y con
  `FINANCIAL.*` del contrato (BG-04 de la 006, confirmado en ejecución). `PaymentMethodsViewModel`
  (clave `sesión-familia`) combina el repositorio con la sección de archivados, el refresco, el
  formulario de alta y renombrado (`PaymentMethodFormState`; nombre recortado, tope de 40, descarte
  con confirmación), la acción pendiente (`PaymentMethodPendingAction`: confirmar archivar o
  eliminar, y la oferta de archivar si eliminar falla por movimientos asociados) y un evento de
  aviso (`PaymentMethodsEvent`) en un `StateFlow<PaymentMethodsUiState>`. Una sola operación de
  cambio a la vez (`isBusy`); recibe `onInsufficientRole`. `PaymentMethodsContent` es stateless
  (`PaymentMethodFormDialog` y el menú ⋮ por fila); `PaymentMethodTexts` traduce los fallos y
  distingue el aviso sin conexión de una acción del menú del del formulario.
- **Dinero y moneda de la familia** (`domain/money`, `ui/movements/MoneyFormat.kt`): `Money(amountMinor: Long,
  currency)` es siempre un entero en la unidad menor (nunca `Double`); sumar o restar exige la misma moneda y
  usa aritmética exacta. `MoneyInput` decide qué se puede escribir en el campo de monto según los decimales de
  la moneda (`java.util.Currency`: CLP 0, USD 2) y convierte texto ↔ unidad menor; la parte entera admite 12
  dígitos. `MoneyFormat` (`formatted`, `formattedFor` con `-` en gasto y `+` en ingreso, `formattedSigned`) da
  formato con la configuración regional del dispositivo. La moneda de la familia llega en `Family.currency`
  (`defaultCurrency` de `GET /me/families`), guardada como campo opcional de `header_cache` sin cambiar su
  `schemaVersion`: una copia antigua se lee sin moneda. Sin moneda válida no se puede registrar un movimiento;
  al editar se usa la del propio movimiento.
- **Miembros de la familia** (`domain/members`, `data/members`): `FamilyMembersRepositoryImpl` descarga
  `GET /families/:id/members` y solo lee `userId`, `displayName` y `role`; el correo no se lee ni se guarda. Caché
  `members_cache` (una clave `members_<familia>`, `schemaVersion = 1`, excluida de las copias de seguridad,
  `CachePolicy`). Solo lee: sin operaciones de cambio ni conectividad. `ui/movements/MemberLabel.kt` resuelve
  quién registró un movimiento contra la lista y el `userId` de la sesión: `You` (se reconoce aunque no haya
  lista), `Named`, `NoName`, `FormerMember` (no está en la lista) o `Unavailable` (sin lista; el listado lo omite
  y el detalle lo indica).
- **Movimientos** (`domain/movements`, `data/movements`, `ui/movements`): misma separación por capas que
  categorías y medios de pago. `MovementsRepositoryImpl` expone `movements(familia, mes)` (Flow ya ordenado por
  día y `createdAt` descendentes), `movement(familia, id)` (busca solo en los meses cargados: el backend no tiene
  consulta individual, BG-06 de la 007), `refresh(familia, mes)` y las mutaciones `createMovement`, `editMovement` (primero
  los datos y después la categoría; `MovementEditResult` distingue `Success`, `Failure` y `Partial`) y
  `deleteMovement`. Sin conexión las mutaciones devuelven `Offline` sin llamar al backend (una edición sin
  cambios no llama). Cada cambio correcto se aplica a memoria y caché de los meses cargados conservando su
  `savedAt` (un movimiento que cambia de mes sale del mes actual); lo aplicado es lo que la app envió, no lo que
  responde el backend. La caché es `movements_cache` (clave `movements_<familia>_<yyyy-MM>` y
  `last_payment_method_<usuario>_<familia>`, `schemaVersion = 1`, excluida de las copias de seguridad,
  `CachePolicy`). El día se guarda como `LocalDate`: `MovementDates` (reloj y zona del dispositivo) envía el
  instante actual si es hoy y las 12:00 de ese día si es pasado, nunca futuro (BG-09 de la 007). Los errores de dominio se
  aceptan con los prefijos `FINANCIAL_TRACKING.*` y `FINANCIAL.*` (BG-07 de la 007). Presentación:
  `MovementsViewModel` (clave `sesión-familia`) combina el mes, el filtro de tipo y los repositorios de
  categorías, medios de pago y miembros en filas ya resueltas, con subtotal por día de lo visible y resumen del
  mes con todos; `MovementFormViewModel` es el formulario de alta y, con `movementId`, de edición (envía solo lo
  modificado, conserva lo archivado que el movimiento ya tenía y un resultado parcial deja el formulario abierto
  con lo pendiente); `MovementDetailViewModel` resuelve el detalle y elimina con confirmación (si el movimiento
  desaparece pasa al estado `Gone`, con "Volver al listado"). Los dos últimos tienen una clave de ViewModel por
  apertura de la ruta (`MainUiState.managementRouteInstance`), así que cada "+" empieza con un formulario nuevo.
  Todas las operaciones de cambio permiten una sola a la vez, con el indicador fijado antes de lanzar la corrutina.
- **Presentación**: `MainViewModel` combina el flujo del repositorio con estado local
  (familia activa solo en memoria, selector abierto, refresco en curso) en un
  `StateFlow<MainUiState>`; `FamiliesState` es un sealed interface
  (Loading/NoData/Content/Error). Se refresca al iniciar, en `ON_START` y al recuperar
  conectividad. La pestaña inferior seleccionada también vive en `MainViewModel` (`MainUiState.selectedTab`,
  solo en memoria: sobrevive a recrear la pantalla y tras terminar el proceso se vuelve a Inicio). `MainScreen` tiene una sobrecarga con estado (ViewModel) y otra stateless.
  `AuthViewModel` (`ui/auth`) gestiona login y registro con las reglas de `AuthValidation` y los
  mensajes de `AuthTexts`; `AuthContent` es la parte stateless.
- **Navegación**: aún no hay librería. `AppRoot`/`RootViewModel` eligen la pantalla según
  `SessionState`: nada en `Loading`, `AuthScreen` en `SignedOut` y `MainScreen` en `SignedIn`; la
  generación de la sesión reinicia el estado de los ViewModels al cambiar de usuario. Las pestañas
  inferiores son el enum `MainDestination` (su orden es el de la barra); la pestaña Movimientos muestra
  `MovementsScreen` mediante el hueco `movementsContent` de `MainScreen` (nulo en los tests de UI, que usan
  `TabPlaceholder`). La gestión de categorías
  (`CategoriesRoute`: listado, alta o edición) y la de medios de pago (`PaymentMethodsRoute`) son
  rutas **en memoria** de `MainViewModel` (`managementRoute`, ligada a la familia con la que se
  abrió), no pestañas ni una librería de navegación: se abren desde «Gestionar» en Inicio, ocultan
  la cabecera y la barra inferior, impiden cambiar de familia mientras están abiertas, se cierran
  si esa familia desaparece de la cabecera y se pierden si el proceso muere (se vuelve a Inicio).
  Las pantallas de un movimiento son también rutas en memoria (`MovementRoute.Create`, `Detail(id)` y
  `Edit(id)`): el "+" de Inicio y de Movimientos abre `Create` y tocar una fila abre `Detail`; `Edit` solo se abre
  desde su `Detail` y atrás desde ella vuelve al detalle, mientras que `Create` y `Detail` vuelven a la pestaña de
  origen. Una edición que mueve el movimiento a otro mes cierra todo de una vez (`closeManagement()`), porque el
  detalle ya no lo encontraría. `MainViewModel.onInsufficientRole()` vuelve a consultar la cabecera cuando el
  backend rechaza una acción por rol: si el usuario ya no es miembro, la familia desaparece y la ruta se cierra sola.
- **Tests**: unitarios JVM con `kotlinx-coroutines-test` y `ktor-client-mock`; los de UI
  (`androidTest`) usan `TestMainHost`, que reproduce lo mínimo del ViewModel sobre la
  sobrecarga stateless de `MainScreen`, sin red ni caché; los de acceso (`AuthScreenTest`,
  `AppRootTest`) usan `AuthContent` y `AppRootContent` con repositorios falsos. Los repositorios falsos de los tests de movimientos (movimientos, categorías, medios de pago y
  miembros) están compartidos en `app/src/test/…/ui/movements/Fakes.kt`.

## Documentación y especificaciones

- Leer las instrucciones incluidas en cada plantilla y preservar su estructura
  y comentarios al crear documentos derivados.
- Usar `docs/SPEC_TEMPLATE.md` para especificaciones funcionales y
  `docs/PLAN_TEMPLATE.md` para planes de trabajo.
- Mantener los documentos de cada funcionalidad juntos en
  `docs/features/<feature-name>/`: `SPEC.md`, `PLAN.md` y `TASKS.md`.
- Seguir esta secuencia: completar la especificación, esperar la aprobación
  explícita del usuario, crear el plan, esperar su aprobación, derivar las
  tareas y recién entonces implementar. El agente no cambia por sí solo el
  estado de aprobación.
- `TASKS.md` debe contener tareas pequeñas, ordenadas y verificables, con
  identificador, objetivo, alcance, dependencias, criterios de aceptación que
  resuelve y método de validación. Actualizar su estado durante el trabajo y
  registrar allí la evidencia de validación.
- Mantener requisitos, decisiones, criterios de aceptación y estado del trabajo
  consistentes con la implementación.
- Actualizar la documentación directamente relacionada cuando cambie el
  comportamiento o una decisión técnica.

## Referencias

- `docs/ROADMAP.md` — orden de implementación y estado de cada funcionalidad; actualizarlo
  cuando una funcionalidad cambie de estado.
- `docs/features/<NNN-nombre>/` — SPEC/PLAN/TASKS por funcionalidad. Los requisitos se citan en
  el código como `RF-xx`.
- `docs/backend/api-contract.md` y `docs/backend/openapi.yaml` — contrato con el backend
  (`familyFinAI-backend`). Dinero como entero en la unidad menor (`Money {amount, currency}`),
  nunca `Double`; los errores llegan con un `code` de dominio.
- `docs/DESIGN.md` — sistema visual (colores, tipografía, componentes).
- La app nunca contiene secretos de BD ni API keys de IA; lo que propone la IA nunca se guarda
  sin confirmación del usuario.

## Validación

Ejecutar desde la raíz el conjunto más pequeño de comprobaciones que cubra el
cambio:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Para una prueba local concreta, usar:

```bash
./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.main.MainViewModelTest"
```

Para cambios que requieran pruebas instrumentadas, ejecutar
`./gradlew :app:connectedDebugAndroidTest` con un dispositivo o emulador
disponible.

No hay configuración separada de ktlint o detekt en el repositorio base; no
introducir una herramienta de calidad como cambio no relacionado. Para cambios
solo documentales, verificar el contenido y sus referencias sin exigir una
compilación Android.

Si cambia la configuración de variantes, inspeccionar las tareas Gradle
disponibles y usar la variante correspondiente.

## Informe de finalización

Resumir el comportamiento implementado, los documentos de funcionalidad
afectados y los resultados de validación. Vincular los criterios de aceptación
con la evidencia registrada en `TASKS.md` e identificar claramente cualquier
parte incompleta o no verificada.
Informar claramente qué comprobaciones se ejecutaron, cuáles fallaron y cuáles
no pudieron ejecutarse por limitaciones del entorno.

## Cambios y control de versiones

- Este archivo es la única fuente de instrucciones del proyecto. `CLAUDE.md` solo enlaza a él y
  no debe contener información propia: cualquier regla, comando, decisión o contexto nuevo se
  añade aquí, nunca en `CLAUDE.md`.
- Revisar el estado del repositorio antes de editar y preservar cambios ajenos.
- Mantener los cambios enfocados; no hacer refactors o actualizaciones
  no relacionados.
- No modificar ni eliminar archivos generados o cambios existentes sin
  autorización explícita.
- Usar formato Kotlin/Gradle consistente con los archivos existentes y evitar
  comentarios que solo repitan el código.
