# TASKS: Cabecera principal operativa

**SPEC:** `docs/features/002-main-header/SPEC.md` (Aprobada)
**PLAN:** `docs/features/002-main-header/PLAN.md` (Aprobado)

<!-- Tareas de máx. 20-30 min, en orden de dependencia. Marcar [x] solo tras
ejecutar la validación y registrar en "Evidencia" el resultado real. Si una
tarea queda bloqueada, anotar el motivo en "Evidencia". -->

## Configuración

- [x] **T-01 · Dependencias y plugin de serialización**
  - **Cubre:** soporte (RF-01, RF-04, RF-05, RF-07) · **Depende de:** —
  - **Alcance:** en `gradle/libs.versions.toml` y `app/build.gradle.kts`: Ktor Client (core, okhttp, content-negotiation, serialization-kotlinx-json), plugin y `kotlinx-serialization-json`, DataStore Preferences, lifecycle-viewmodel-compose y lifecycle-runtime-compose, y para tests `kotlinx-coroutines-test` y `ktor-client-mock`. Versiones compatibles con Kotlin 2.2.10 y AGP 9.3.3.
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila sin errores con todas las dependencias.
  - **Evidencia:** `./gradlew :app:assembleDebug` y `:app:compileDebugUnitTestKotlin` → BUILD SUCCESSFUL. Versiones fijadas en `gradle/libs.versions.toml`: Ktor 3.3.3, kotlinx-serialization-json 1.9.0, kotlinx-coroutines-test 1.10.2, DataStore 1.3.0, lifecycle 2.10.0 (sustituye a `lifecycleRuntimeKtx` 2.6.1 para alinear runtime-ktx, runtime-compose y viewmodel-compose). Descartadas: serialization 1.12.0 (exige metadata de Kotlin 2.4, el proyecto usa 2.2.10) y Ktor 3.6.0 (arrastra OkHttp 5.5.0, que exige `compileSdk` 37; con 3.3.3 resuelve OkHttp 5.2.1). Plugin `kotlin-serialization` aplicado en `app/build.gradle.kts`.

- [x] **T-02 · Permisos, Application y configuración de entorno**
  - **Cubre:** soporte (RF-07) · **Depende de:** T-01
  - **Alcance:** permisos `INTERNET` y `ACCESS_NETWORK_STATE`; `FamilyFinApp` y `AppContainer` (HttpClient, DataStore, repositorio) y `android:name` en el manifest; `buildConfig` con `BACKEND_BASE_URL`, `DEV_EMAIL`, `DEV_PASSWORD` leídos de `local.properties` (no versionado) solo en debug (vacíos en release; el build de debug falla con mensaje claro si faltan); `network_security_config` en `src/debug` que permite HTTP solo a `10.0.2.2`.
  - **Validación:** `./gradlew :app:assembleDebug`, `./gradlew :app:assembleRelease` o revisión de que release no admite cleartext, y revisión de que `local.properties` no se versiona.
  - **Hecho cuando:** compila, el manifest declara permisos y `Application`, el cleartext existe solo en debug y no hay secretos en el repositorio.
  - **Evidencia:** `./gradlew :app:assembleDebug :app:assembleRelease` → BUILD SUCCESSFUL. Manifest de release con permisos y `FamilyFinApp` y sin `networkSecurityConfig`; solo el manifest de debug lo declara (`src/debug`, HTTP solo a `10.0.2.2`). `BuildConfig` de release con las 3 constantes vacías. Sin `DEV_EMAIL` en `local.properties`, `assembleDebug` falla con «Faltan propiedades en local.properties… DEV_EMAIL» (la tarea `checkDevProperties` solo engancha `preDebugBuild`). `git check-ignore local.properties` → ignorado. `AppContainer` incluye por ahora `HttpClient` (OkHttp, JSON, timeouts 10 s/15 s) y el DataStore `header_cache`; el repositorio se añade en T-12. Ampliación posterior (probar con móvil físico): `DEV_TARGET=emulator|device` elige `BACKEND_BASE_URL` (`http://10.0.2.2:3000` / `http://localhost:3000` con `adb reverse`); el cleartext de debug admite también `localhost`. Verificado con `assembleDebug` y `assembleRelease`: BuildConfig con cada URL, y valor inválido → error claro.

- [x] **T-03 · Exclusión de la caché de las copias de seguridad**
  - **Cubre:** privacidad (RF-07) · **Depende de:** T-01
  - **Alcance:** excluir el DataStore `header_cache` en `backup_rules.xml` y `data_extraction_rules.xml` (cloud-backup y device-transfer).
  - **Validación:** `./gradlew :app:assembleDebug` y revisión de los XML.
  - **Hecho cuando:** ambos archivos excluyen la caché y compila.
  - **Evidencia:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. `backup_rules.xml` (full-backup-content) y `data_extraction_rules.xml` (cloud-backup y device-transfer) excluyen `domain="file" path="datastore/header_cache.preferences_pb"`, el archivo que crea el DataStore `header_cache` (`AppContainer`). Revisión de XML; no se probó un backup real.

- [x] **T-04 · Cadenas y plurales**
  - **Cubre:** RF-04, RF-06, RF-07, RF-08, RF-09 · **Depende de:** —
  - **Alcance:** en `strings.xml`: roles ("Propietario", "Integrante"), plural del contador ("N invitaciones pendientes"), estados de carga/error/reintento/sin datos, aviso de datos desactualizados, "Cambios solo en este dispositivo", formato "Título · Familia"; retirar `header_family_name` y `header_family_role`.
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y no quedan textos de ejemplo ni literales previstos en código.
  - **Evidencia:** `./gradlew :app:assembleDebug :app:lintDebug` → BUILD SUCCESSFUL. Añadidos en `strings.xml`: roles (`role_owner`, `role_member`), plural `header_notifications_count_description`, estados (`header_family_loading`, `header_family_error`, `header_retry`, `header_no_data`), aviso `header_stale_data_banner`, `header_local_changes_description`, etiquetas del avatar y del selector, y `tab_content_title_with_family` («%1$s · %2$s»). Desviación: `header_family_name`/`header_family_role` siguen en el archivo, marcadas con comentario, porque `MainHeader` estático y los androidTest de la 001 aún las usan; se retiran en T-13.

## Dominio y sesión

- [x] **T-05 · Modelos y contrato del repositorio**
  - **Cubre:** RF-01, RF-02, RF-04, RF-05 · **Depende de:** T-01
  - **Alcance:** `domain/header`: `Family(id, name, role)`, `FamilyRole` (OWNER, MEMBER), `HeaderData` (familias, nº de invitaciones, nombre, `savedAt` por recurso) y `HeaderRepository`; sin dependencias de Android, Compose ni DTOs.
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y el paquete de dominio no importa Android ni Compose.
  - **Evidencia:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. Creados en `domain/header`: `Family`, `FamilyRole`, `HeaderData` (con `Timed<T>`: valor + `savedAtMillis` por recurso, nulo si no hay dato), `HeaderRepository` (`header: Flow<HeaderData>` y `refresh()`) y `HeaderFailure`/`HeaderRefreshResult` (fallo independiente por recurso: red, rol insuficiente, no autorizado, datos inválidos, desconocido). Añadidos sobre el alcance para poder expresar CA-11 y la re-consulta por `FAMILY.INSUFFICIENT_ROLE`. `grep` de imports `android`/`androidx` en `domain` → sin resultados.

- [x] **T-06 · SessionProvider**
  - **Cubre:** CA-18 (restricción: sesión iniciada) · **Depende de:** T-02, T-05
  - **Alcance:** interfaz `SessionProvider` (userId y token) y `StaticSessionProvider` temporal solo debug: `POST /auth/login` con `DEV_EMAIL`/`DEV_PASSWORD` para obtener `userId` y token; sin refresco; error explícito si el login falla; sin credenciales ni token en logs.
  - **Validación:** test con `ktor-client-mock` (login correcto e `IDENTITY.INVALID_CREDENTIALS`) en `./gradlew :app:testDebugUnitTest` y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan, compila y el repositorio solo depende de la interfaz.
  - **Evidencia:** `./gradlew :app:assembleDebug :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.session.*"` → BUILD SUCCESSFUL; `StaticSessionProviderTest`: 4 tests, 0 fallos (login correcto con reutilización de la sesión, `IDENTITY.INVALID_CREDENTIALS` → `INVALID_CREDENTIALS`, sin credenciales → `NOT_CONFIGURED` sin llamar a la red, `toString` sin token). `SessionProvider.current()` devuelve `Session(userId, accessToken)` o lanza `SessionUnavailableException` con un `SessionFailure` (sin mensaje del backend). Sin `Log`/`println` en `session`. `StaticSessionProvider` vive en `main` (no en `src/debug`) para que compile en release, donde las credenciales vacías dan `NOT_CONFIGURED`; aún no se cablea en `AppContainer` (T-12/T-16). No probado contra el backend real.

- [x] **T-07 · Inicial del avatar**
  - **Cubre:** RF-05 (CA-07) · **Depende de:** T-05
  - **Alcance:** función pura: primer *code point* de `displayName` en mayúscula; nombre vacío → sin inicial (icono genérico). Test unitario con «rocío» → «R».
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.domain.header.*"`.
  - **Hecho cuando:** los tests pasan, incluido el caso vacío.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.domain.header.*"` → BUILD SUCCESSFUL; `AvatarInitialTest`: 5 tests, 0 fallos («rocío» → «R» (CA-07), espacios iniciales, «émile» → «É», code point fuera del BMP sin cortar, vacío/blanco/nulo → `null`). Función `avatarInitial(displayName: String?): String?` en `domain/header/AvatarInitial.kt`, sin dependencias de Android.

## Datos

- [x] **T-08 · Origen de datos local**
  - **Cubre:** RF-07 (CA-10) · **Depende de:** T-05, T-06
  - **Alcance:** `HeaderLocalDataSource` con DataStore: JSON por recurso con `userId`, `savedAt` y `schemaVersion`; leer, guardar y borrar; `schemaVersion` desconocido → sin datos.
  - **Validación:** tests unitarios con DataStore temporal en `./gradlew :app:testDebugUnitTest`.
  - **Hecho cuando:** guardar y leer devuelve lo mismo, borrar deja vacío y versión desconocida se descarta.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.header.*"` y `:app:assembleDebug` → BUILD SUCCESSFUL; `HeaderLocalDataSourceTest`: 7 tests, 0 fallos (guardar/leer devuelve lo mismo, sin datos → nulo, `clear()` deja vacío, guardar un recurso no altera los demás, `schemaVersion` 99 → sin datos, JSON ilegible → sin datos, rol guardado desconocido → sin datos). `HeaderLocalDataSource` (`data/header`) usa un JSON por recurso (`families`, `pending_invitations`, `profile`) con `schemaVersion`, `userId` y `savedAtMillis`; expone `Cached<T>` con el `userId` para que el repositorio (T-10) decida el borrado por usuario y por antigüedad. DataStore temporal con `PreferenceDataStoreFactory`. Añadido sobre el alcance: un JSON ilegible también se trata como sin datos.

- [x] **T-09 · Origen de datos remoto**
  - **Cubre:** RF-01, RF-04, RF-05 · **Depende de:** T-05, T-06
  - **Alcance:** `HeaderRemoteDataSource` con Ktor: `GET /me/families`, `/me/invitations`, `/me/profile` con Bearer; DTOs `@Serializable` privados con `ignoreUnknownKeys`; timeouts conexión 10 s y solicitud 15 s; rol desconocido → error; lectura del código `FAMILY.INSUFFICIENT_ROLE`; sin `message` ni tokens en logs.
  - **Validación:** tests con `ktor-client-mock` en `./gradlew :app:testDebugUnitTest`.
  - **Hecho cuando:** los tests cubren éxito, error HTTP, rol inválido y timeout.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.header.*" :app:assembleDebug` → BUILD SUCCESSFUL; `HeaderRemoteDataSourceTest`: 10 tests, 0 fallos (éxito de los 3 GET con cabecera `Bearer` y ruta correcta, invitaciones = tamaño de la lista, rol desconocido y cuerpo malformado → `InvalidData`, 403 `FAMILY.INSUFFICIENT_ROLE` → `InsufficientRole`, 401 → `Unauthorized`, 500 → `Unknown`, timeout y sin conexión → `Network`). `HeaderRemoteDataSource` devuelve `RemoteResult<T>` (`Success` o `Failure(HeaderFailure)`) y recibe la `Session` en cada llamada; DTOs privados con `ignoreUnknownKeys`; timeouts de 10 s/15 s aplicados por solicitud (y en el `HttpClient` de `AppContainer`); solo se lee `error`, nunca `message`; sin `Log`/`println`. La prueba de timeout simula `HttpRequestTimeoutException` (no espera 15 s reales). No probado contra el backend real.

- [x] **T-10 · Repositorio y reglas de caché**
  - **Cubre:** RF-07 (CA-09, CA-11, CA-16, CA-17, CA-18) · **Depende de:** T-08, T-09
  - **Alcance:** `HeaderRepositoryImpl` con reloj inyectable: caché de otro `userId` o ≥7 días → borrar; >24 h → marcar desactualizado hasta un refresco correcto; refresco en paralelo e independiente por recurso; sin datos válidos y fallo → error; con datos válidos y fallo → conserva caché; los `CancellationException` se relanzan.
  - **Validación:** tests unitarios con reloj falso en `./gradlew :app:testDebugUnitTest`.
  - **Hecho cuando:** pasan los escenarios de 1 h, 30 h, 8 días, otro usuario, sin red con y sin caché, y fallo solo de invitaciones.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL; `HeaderRepositoryImplTest`: 11 tests, 0 fallos, con reloj falso y DataStore temporal: carga inicial y guardado; caché de 1 h sin red (sin alerta); 30 h sin red (alerta `isStale`); 30 h + refresco correcto (sin alerta); 8 días → eliminada y no mostrada; otro usuario → eliminada, nunca mostrada (con y sin red); sin red y sin caché → fallo y sin datos; falla solo invitaciones (familias cargadas, sin contador; con caché conserva el contador); sesión no disponible → fallo `Unauthorized` sin tocar la caché. Decisiones de implementación: `HeaderData` gana `isStale` (calculado por el repositorio con el recurso más antiguo >24 h); si cualquier recurso guardado es de otro usuario o tiene ≥7 días se borra toda la caché; los tres recursos se piden en paralelo y cada éxito sustituye su recurso y su `savedAt`; `refresh()` se serializa con un `Mutex`; la cancelación no se captura. Limitación: la caché se valida con el `userId` de la sesión, así que necesita que `SessionProvider.current()` responda; con `StaticSessionProvider` (login por red) sin conexión no se muestra la caché (CA-10 solo se cumplirá con la spec de sesión que persista el `userId`).

- [x] **T-11 · Observador de conectividad**
  - **Cubre:** RF-07 · **Depende de:** T-02
  - **Alcance:** `ConnectivityObserver` con `NetworkCallback` → `Flow<Boolean>`; emite al recuperar la red.
  - **Validación:** `./gradlew :app:assembleDebug` y prueba manual en T-20.
  - **Hecho cuando:** compila y se cancela el registro al dejar de colectar.
  - **Evidencia:** `./gradlew :app:assembleDebug :app:lintDebug` → BUILD SUCCESSFUL. `ConnectivityObserver` (interfaz con `isOnline: Flow<Boolean>`, para poder sustituirla en los tests del ViewModel) y `AndroidConnectivityObserver` en `data/network`: `callbackFlow` con `registerDefaultNetworkCallback`, emite el estado inicial y cada cambio (`onAvailable`, `onCapabilitiesChanged`, `onLost`), `distinctUntilChanged`, y `awaitClose` desregistra el callback al dejar de colectar. Usa solo `ACCESS_NETWORK_STATE` (ya declarado). La prueba en ejecución (modo avión) queda para T-20.

## Estado

- [x] **T-12 · MainViewModel y estado**
  - **Cubre:** RF-01, RF-02, RF-03, RF-07, RF-08 (CA-01, CA-05, CA-12) · **Depende de:** T-10, T-11
  - **Alcance:** `MainUiState` (`Loading`, `NoData`, `Content`, `Error`; contador, nombre y `hasLocalChanges` = false) y `MainViewModel` con `StateFlow`: favorita = primera; `activeFamilyId` solo en memoria; `selectorOpen`; reintento sin duplicar cargas; `onInsufficientRole` re-consulta familias sin cambiar la activa; activa desaparecida → favorita; refresco al iniciar y al recuperar red.
  - **Validación:** `MainViewModelTest` con `kotlinx-coroutines-test` en `./gradlew :app:testDebugUnitTest`.
  - **Hecho cuando:** los tests cubren favorita, cambio de activa, ViewModel nuevo = favorita, doble pulsación y `onInsufficientRole`.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL; `MainViewModelTest`: 13 tests, 0 fallos, con repositorio y conectividad falsos: favorita = primera; cambiar de activa cierra el selector; ViewModel nuevo = favorita (RF-03); una sola familia no abre selector (CA-19); activa desaparecida → favorita; doble reintento/`onStart`/doble apertura → una sola carga y una sola lista (CA-12); sin datos y fallo → `Error` y el reintento carga; con datos guardados y fallo → sin error y con `isStale`; fallo solo de invitaciones → familias sin contador (CA-11); contador y nombre no cambian al elegir familia; `onInsufficientRole` vuelve a consultar sin cambiar la activa y refleja el rol nuevo; recuperar red refresca (no en la primera emisión); lista vacía → `NoData`. Archivos: `ui/main/MainUiState.kt` (`FamiliesState`: `Loading`, `NoData`, `Content`, `Error`, y `MainUiState` con `hasLocalChanges = false`) y `ui/main/MainViewModel.kt` (`StateFlow` con `WhileSubscribed`, `activeFamilyId` solo en memoria, `refreshJob` único). Decisiones: `NoData` = el backend respondió una lista vacía; «sin datos mientras se consulta» tras borrar la caché de otro usuario (CA-18) se muestra como `Loading` y la UI (T-13) puede usar `header_no_data` en ese estado; `onStart()` es el punto de entrada para `ON_START` (T-16); la fábrica del ViewModel se crea en T-16.

## Interfaz

- [ ] **T-13 · Cabecera con datos reales**
  - **Cubre:** RF-01, RF-02, RF-04, RF-05, RF-06, RF-07, RF-09 (CA-06, CA-08, CA-13, CA-19, CA-20) · **Depende de:** T-04, T-12
  - **Alcance:** retirar `header_family_name` y `header_family_role` de `strings.xml` (pendiente de T-04); modificar `MainHeader`: nombre y rol de la activa; estados carga, error con reintento y sin datos; una sola familia sin indicador ni clic; contador solo si N>0 con semántica plural; avatar con inicial o icono genérico, sin acción; icono de cambios locales a la izquierda de notificaciones, oculto si `hasLocalChanges` es false; áreas ≥48 dp, truncado de nombres largos, colores del tema; `@Preview` de los estados.
  - **Validación:** vistas previas y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** las vistas previas muestran los estados y cada elemento tiene etiqueta accesible.
  - **Evidencia:**

- [ ] **T-14 · Selector de familia**
  - **Cubre:** RF-02 (CA-02, CA-03, CA-04, CA-12) · **Depende de:** T-13
  - **Alcance:** `FamilySelectorSheet` con `ModalBottomSheet` único, lista desplazable con nombre y rol, activa marcada como seleccionada; elegir cierra y cambia la activa; Atrás cierra sin cambios.
  - **Validación:** vista previa y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y la vista previa muestra la lista con la activa marcada.
  - **Evidencia:**

- [ ] **T-15 · Aviso de datos desactualizados**
  - **Cubre:** RF-07 (CA-16) · **Depende de:** T-04, T-12
  - **Alcance:** `StaleDataBanner` bajo la cabecera, visible solo con datos guardados de más de 24 h y oculto tras un refresco correcto.
  - **Validación:** vista previa y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y la vista previa muestra el aviso.
  - **Evidencia:**

- [ ] **T-16 · Integración en MainScreen y MainActivity**
  - **Cubre:** RF-03, RF-07 · **Depende de:** T-13, T-14, T-15
  - **Alcance:** `MainScreen` obtiene el ViewModel y usa `collectAsStateWithLifecycle`; refresco en `ON_START`; `MainScreenContent` recibe estado y callbacks (stateless) y pasa la familia activa al cuerpo; la pestaña activa sigue en `remember`.
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y la app abre con cabecera conectada al ViewModel.
  - **Evidencia:**

- [ ] **T-17 · Pestañas con la familia activa**
  - **Cubre:** RF-08 (CA-14, CA-15) · **Depende de:** T-16
  - **Alcance:** `TabPlaceholder` y `HomeContent` muestran «Título · Familia»; `key(familyId)` fuerza la recarga al cambiar de familia; Inicio incluido.
  - **Validación:** vistas previas y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y cada pestaña muestra el formato con el nombre de la familia.
  - **Evidencia:**

## Validación

- [ ] **T-18 · Tests de interfaz**
  - **Cubre:** CA-02, CA-03, CA-04, CA-06, CA-08, CA-12, CA-13, CA-14, CA-15, CA-19, CA-20 y regresión de la 001 · **Depende de:** T-17
  - **Alcance:** adaptar `MainScreenTest` y `MainScreenBehaviorTest` a la nueva firma y datos de prueba; añadir tests de selector, contador, una familia, icono de cambios locales y pestañas.
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest` (requiere emulador o dispositivo).
  - **Hecho cuando:** los tests pasan o queda registrado el bloqueo del entorno.
  - **Evidencia:**

- [ ] **T-19 · Compilación, tests unitarios y lint**
  - **Cubre:** todos los RF · **Depende de:** T-18
  - **Alcance:** ejecutar el conjunto completo.
  - **Validación:** `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`.
  - **Hecho cuando:** las tres terminan sin errores, o se registran los fallos.
  - **Evidencia:**

- [ ] **T-20 · Pruebas manuales en emulador**
  - **Cubre:** CA-05, CA-09, CA-10, CA-13, CA-15 · **Depende de:** T-19
  - **Alcance:** matar el proceso y reabrir (vuelve la favorita); instalación limpia sin red, error y reintento; datos previos sin red; TalkBack y fuente máxima; recorrer las 5 pestañas. Requiere el backend en `localhost:3000` (emulador: `http://10.0.2.2:3000`) y un usuario de prueba con varias familias en `DEV_EMAIL`/`DEV_PASSWORD` de `local.properties`. Para CA-19 y CA-06 se usa `DEV_GUEST_EMAIL`/`DEV_GUEST_PASSWORD` (una sola familia y 1 invitación pendiente): no aceptar su invitación mientras tanto. El rol «Integrante» se probará más adelante, al aceptarla.
  - **Validación:** ejecución manual con resultados anotados.
  - **Hecho cuando:** cada escenario tiene resultado registrado, o motivo de bloqueo (sin AVD o backend).
  - **Evidencia:**

- [ ] **T-21 · Revisión final**
  - **Cubre:** CA-01 a CA-20 · **Depende de:** T-20
  - **Alcance:** contrastar cada criterio con su evidencia, confirmar que no se implementó nada fuera de alcance y que SPEC, PLAN y TASKS son coherentes.
  - **Validación:** revisión documental.
  - **Hecho cuando:** cada criterio tiene evidencia o está marcado como no verificado.
  - **Evidencia:**
