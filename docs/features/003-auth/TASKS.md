# TASKS: Autenticación y sesión

**SPEC:** `docs/features/003-auth/SPEC.md` (Aprobada)
**PLAN:** `docs/features/003-auth/PLAN.md` (Aprobado)

<!-- Tareas de máx. 20-30 min, en orden de dependencia. Marcar [x] solo tras
ejecutar la validación y registrar en "Evidencia" el resultado real. Si una
tarea queda bloqueada, anotar el motivo en "Evidencia". -->

## Dominio

- [x] **T-01 · Reglas de validación de los formularios**
  - **Cubre:** RF-03 (CA-04) · **Depende de:** —
  - **Alcance:** `domain/auth/AuthValidation.kt`, Kotlin puro: email (recortado, obligatorio, regex del contrato), contraseña de registro (obligatoria, ≥8), contraseña de login (no vacía), repetición igual a la contraseña, nombre (recortado, 1–60). Cada regla devuelve el error del campo o ninguno.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.domain.auth.*"` con `AuthValidationTest` (casos válidos, vacíos, límites 7/8 y 60/61, espacios, contraseña con espacios no recortada).
  - **Hecho cuando:** los tests pasan y `domain/auth` no importa Android ni Ktor.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.domain.auth.*"` → BUILD SUCCESSFUL; `AuthValidationTest`: 12 tests, 0 fallos, 0 errores. `AuthValidation` (objeto) con `validateEmail`, `validateLoginPassword`, `validateNewPassword`, `validatePasswordConfirmation` y `validateDisplayName`, que devuelven `FieldError?` (`REQUIRED`, `INVALID_EMAIL`, `PASSWORD_TOO_SHORT`, `PASSWORDS_DO_NOT_MATCH`, `NAME_TOO_LONG`). Las longitudes cuentan unidades UTF-16, como `length` en el backend (supuesto: no verificado en su código). Una contraseña de login solo con espacios se acepta (no vacía), según la spec. `grep` de imports `android`/`androidx`/`io.ktor` en `domain/auth` → sin resultados.

- [x] **T-02 · Tipos de dominio y contratos**
  - **Cubre:** RF-01, RF-02, RF-04, RF-07 · **Depende de:** T-01
  - **Alcance:** `AuthFailure` (`InvalidCredentials`, `RegistrationRejected`, `Network`, `Unknown`), `SessionEndReason` (`Expired`, `Security`, `LoggedOut`), contrato `AuthRepository` (`login`, `register`) y `SessionState` (`Loading`, `SignedOut(reason?)`, `SignedIn(userId, generation)`).
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y el dominio no importa Android, Ktor ni DTOs.
  - **Evidencia:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (exit 0). Creados en `domain/auth`: `AuthFailure.kt`, `SessionState.kt` (con `SessionEndReason` y `SessionState`) y `AuthRepository.kt`. Añadido sobre el alcance: `AuthResult` (`Success` | `Failure(AuthFailure)`) como resultado de `login`/`register`, porque `kotlin.Result` exige un `Throwable` y el contrato no lanza por red o servidor. `grep` de `import` en `domain/auth` → ninguno.

## Datos locales

- [x] **T-03 · Almacenamiento cifrado de la sesión**
  - **Cubre:** RF-05, RF-09 (CA-07) · **Depende de:** T-02
  - **Alcance:** `data/session/SessionStore.kt`: DataStore `session` con un valor Base64(IV + AES-256-GCM) del JSON `{schemaVersion, userId, accessToken, refreshToken}`; clave `familyfin_session_key` en `AndroidKeyStore` (no exportable); `read()`, `save()`, `clear()`. Fallo al descifrar o versión desconocida → `null` y se borra el valor. Sin tokens en logs ni `toString()`.
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest` con `SessionStoreTest` instrumentado (guardar y leer en una instancia nueva, `clear()`, valor corrupto → `null`, el archivo no contiene el token en claro).
  - **Hecho cuando:** los tests instrumentados pasan.
  - **Evidencia:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rocioplata.familyfinaiapp.data.session.SessionStoreTest` en el AVD `Pixel_9` (emulador sin ventana) → BUILD SUCCESSFUL; 8 tests, 0 fallos: lectura desde otra instancia, sin sesión → `null`, `clear()`, valor corrupto → `null` y borrado, clave del Keystore eliminada → `null` y borrado, `schemaVersion` desconocido → `null` y borrado, el archivo no contiene `userId` ni tokens en claro, `toString()` sin tokens. Implementación: `data/session/SessionCipher.kt` (`SessionCipher` + `KeystoreSessionCipher`, AES-256-GCM, IV de 12 bytes generado por el Keystore, etiqueta de 128 bits) y `data/session/SessionStore.kt` (`StoredSession`, interfaz `SessionStore` para poder usar un doble en JVM en T-08, `EncryptedSessionStore`). Desviaciones: el cifrado se separa en `SessionCipher` para poder probar el store sin Keystore; el DataStore `session` se crea en `AppContainer` en T-12 (el test usa un archivo temporal y un alias de clave propio). Un `IOException` de lectura de DataStore no se captura aquí; su manejo se decide en T-08/T-12.

- [x] **T-04 · Exclusión de la sesión de las copias de seguridad**
  - **Cubre:** restricción de la spec (privacidad) · **Depende de:** T-03
  - **Alcance:** excluir `datastore/session.preferences_pb` en `backup_rules.xml` y `data_extraction_rules.xml` (cloud-backup y device-transfer).
  - **Validación:** `./gradlew :app:assembleDebug` y revisión de los XML.
  - **Hecho cuando:** ambos archivos excluyen la sesión y compila.
  - **Evidencia:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (exit 0). `backup_rules.xml` (full-backup-content) y `data_extraction_rules.xml` (cloud-backup y device-transfer) excluyen `domain="file" path="datastore/session.preferences_pb"`, junto a la exclusión existente de `header_cache`. Es el archivo que creará el DataStore `session` en T-12 (`preferencesDataStore(name = "session")`); el nombre debe mantenerse. Revisión de XML; no se probó un backup real.

## Datos remotos

- [x] **T-05 · Login y registro remotos**
  - **Cubre:** RF-01, RF-02, RF-04 (CA-05, CA-06, CA-17) · **Depende de:** T-02
  - **Alcance:** `data/auth/AuthRemoteDataSource.kt` con `POST /auth/login` y `POST /auth/register` y DTOs privados (`toString()` sin secretos). Mapeo: 401 `IDENTITY.INVALID_CREDENTIALS` → `InvalidCredentials`; 409 `IDENTITY.EMAIL_ALREADY_REGISTERED` y 400 `IDENTITY.WEAK_PASSWORD`/`IDENTITY.INVALID_DISPLAY_NAME`/`SHARED.INVALID_EMAIL` → `RegistrationRejected`; `IOException` → `Network`; resto → `Unknown`. Solo se lee `error`. Relanza `CancellationException`.
  - **Validación:** `AuthRemoteDataSourceTest` con `ktor-client-mock` (éxito con `userId` y tokens, cada código, sin red, cuerpo inválido).
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.auth.*"` → BUILD SUCCESSFUL; `AuthRemoteDataSourceTest`: 13 tests, 0 fallos. Cubre: login 200 → `StoredSession`, POST a `/auth/login` sin `Authorization` y contraseña enviada sin recortar; 401 `IDENTITY.INVALID_CREDENTIALS` → `InvalidCredentials` (CA-05); 401 sin código conocido y 500 → `Unknown`; sin red → `Network` (CA-17); respuesta 200 incompleta → `Unknown`; registro 201 con los tres campos; 409 `IDENTITY.EMAIL_ALREADY_REGISTERED` y 400 `IDENTITY.WEAK_PASSWORD`/`IDENTITY.INVALID_DISPLAY_NAME`/`SHARED.INVALID_EMAIL` → `RegistrationRejected` (CA-06); código desconocido o estado distinto de 201 → `Unknown`. Implementación: `data/auth/AuthRemoteDataSource.kt` con `AuthCallResult` (`Success(StoredSession)` | `Failure(AuthFailure)`); DTOs privados con `toString()` sin secretos. Desviación respecto al plan: las esperas excesivas (`HttpRequestTimeoutException`, `ConnectTimeoutException`, `SocketTimeoutException`, que son `IOException`) se mapean a `Unknown` y no a `Network`, porque la tabla mobile de la SPEC distingue "espera excesiva" (mensaje genérico con reintento) de "sin conexión"; cubierto por `login_esperaExcesiva_devuelveUnknown`.

- [x] **T-06 · Renovación y cierre remotos**
  - **Cubre:** RF-06, RF-07, RF-08 · **Depende de:** T-05
  - **Alcance:** `POST /auth/refresh` (200 → nuevo par; 401 `AUTH.POSSIBLE_TOKEN_THEFT` → `Security`; otro 401 → `Expired`; `IOException` → red; 5xx → desconocido) y `POST /auth/logout` (204 → éxito; `IOException` → sin conexión; otros → desconocido).
  - **Validación:** casos añadidos a `AuthRemoteDataSourceTest`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.auth.*"` → BUILD SUCCESSFUL; `AuthRemoteDataSourceTest`: 23 tests, 0 fallos (13 de T-05 + 10 nuevos). Renovación: 200 → `RefreshCallResult.Success` por el cliente público, sin `Authorization` y con `refreshToken` en el cuerpo; 401 `AUTH.POSSIBLE_TOKEN_THEFT` → `Rejected(Security)`; 401 `AUTH.INVALID_REFRESH_TOKEN` o sin código → `Rejected(Expired)`; sin red → `Network`; 500, espera excesiva o respuesta incompleta → `Unknown`; `toString()` sin tokens. Logout: 204 → `Success` por el cliente autenticado (0 llamadas al público); sin red → `NoConnection`; 401 → `Unauthorized`; 500 o espera → `Unknown`. Cambios: `AuthRemoteDataSource` recibe ahora `publicClient` y `apiClient` (logout por `apiClient` para que el plugin añada el Bearer y renueve, según el plan); los tests de T-05 usan el mismo cliente en ambos. Se añade `LogoutCallResult.Unauthorized` (no previsto en la tarea) para que `SessionManager` (T-10) distinga una sesión ya cerrada durante la llamada. Se unifica el manejo de excepciones en un `Outcome` privado (respuesta, sin red, espera excesiva, otro error). Nota para T-12: `apiClient` → plugin → `SessionManager` → `AuthRemoteDataSource` → `apiClient` forma un ciclo de construcción; el plugin recibirá una función que resuelve `SessionManager` al llamarse.

## Sesión

- [x] **T-07 · Vaciado de la cabecera**
  - **Cubre:** RF-09 (CA-13) · **Depende de:** —
  - **Alcance:** `HeaderRepository.clear()` y su implementación en `HeaderRepositoryImpl`: `local.clear()` y estado en memoria a `HeaderData()`.
  - **Validación:** test en `HeaderRepositoryImplTest` (tras `clear()`, `header` emite vacío y la caché no tiene datos) y regresión de la clase.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL; suite JVM completa: 88 tests, 0 fallos. `HeaderRepositoryImplTest`: 13 tests (11 previos + 2 nuevos): `clear_vaciaLaCacheYLosDatosEnMemoria` (tras un refresco, `header` emite familias, invitaciones y nombre nulos y la caché no tiene datos) y `clear_duranteUnRefresco_noQuedanDatosGuardadosAlTerminar` (un `clear()` lanzado con un refresco bloqueado en la red espera a que termine y deja la caché vacía). Implementación: `HeaderRepository.clear()` y en `HeaderRepositoryImpl` `local.clear()` + `HeaderData()` bajo el mismo `Mutex` que `refresh()`, para que un refresco en curso no vuelva a guardar datos del usuario anterior. `MainViewModelTest.FakeRepository` implementa `clear()`; sus 13 tests siguen en verde.

- [x] **T-08 · SessionManager: carga, inicio y fin de sesión**
  - **Cubre:** RF-05, RF-09, RF-10 (CA-07, CA-13) · **Depende de:** T-03, T-07
  - **Alcance:** `session/SessionManager.kt` con `StateFlow<SessionState>`, `load()` (sesión guardada → `SignedIn`; ninguna → borrar caché y `SignedOut(null)`), `start(session)` (guardar y `SignedIn` con nueva `generation`), `endSession(reason)` (borrar sesión, `onSessionEnded`, `SignedOut(reason)`), con `Mutex`. Implementa `SessionProvider`. Store abstraído para testear en JVM.
  - **Validación:** `SessionManagerTest` (JVM, store falso): cada transición, `onSessionEnded` llamado, `current()` sin sesión lanza `SessionUnavailableException`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL; suite JVM: 99 tests, 0 fallos. `SessionManagerTest`: 11 tests con un store falso: estado inicial `Loading`; `load()` con sesión → `SignedIn(u-1, 1)` sin borrar datos; sin sesión → `SignedOut(null)` y datos del usuario borrados; archivo ilegible (`IOException`) → igual que sin sesión; `load()` solo lee una vez por proceso; `start()` guarda y entra; cada inicio cambia la generación; `endSession()` borra sesión y datos y guarda el motivo; repetido conserva el primer motivo y no vuelve a borrar; `current()` sin sesión lanza `NOT_SIGNED_IN`; otro usuario tras cerrar sesión entra con su sesión. Implementación: `session/SessionManager.kt` (`StateFlow<SessionState>`, `Mutex`, implementa `SessionProvider`; `current()` lee la sesión en memoria sin bloquear). `SessionFailure` gana `NOT_SIGNED_IN` (mapeado a `HeaderFailure.Unauthorized` en `HeaderRepositoryImpl`); los valores de `StaticSessionProvider` se retiran en T-13. Notas para tareas siguientes: (1) `logout()` (T-10) no debe hacer la llamada de red con el `Mutex` tomado, porque el plugin llamará a `refreshAfter`, que lo necesita; (2) si borrar sesión o datos falla, el estado pasa igualmente a `SignedOut` y la excepción se propaga: `RootViewModel` (T-15) debe tratarla.

- [x] **T-09 · SessionManager: renovación única**
  - **Cubre:** RF-06, RF-07, RF-10 (CA-09, CA-10, CA-11, CA-14) · **Depende de:** T-06, T-08
  - **Alcance:** `refreshAfter(usedToken)`: con `Mutex`; si el token guardado difiere del usado, lo devuelve sin llamar al backend; si no, renueva, guarda el par nuevo **antes** de devolverlo; 401 → `endSession(Expired|Security)` y `null`; red o 5xx → sesión intacta y fallo.
  - **Validación:** `SessionManagerTest`: 10 llamadas concurrentes → 1 llamada a `/auth/refresh`; cada motivo de cierre; `IOException` conserva la sesión.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL; suite JVM: 107 tests, 0 fallos. `SessionManagerTest`: 19 tests (11 de T-08 + 8 nuevos, con `AuthRemoteDataSource` real sobre `ktor-client-mock` que cuenta las llamadas a `/auth/refresh`): éxito → guarda el par nuevo, `current()` devuelve el token nuevo y se envía el refresh token anterior; **10 llamadas concurrentes → 1 sola llamada al backend** y todas reciben el token nuevo (CA-09); token ya renovado → sin llamada; 401 `AUTH.INVALID_REFRESH_TOKEN` → `SignedOut(Expired)` con sesión y datos borrados (CA-10); 401 `AUTH.POSSIBLE_TOKEN_THEFT` → `SignedOut(Security)` (CA-11); sin red → `Unavailable(offline = true)` y sesión intacta (CA-14); 500 → `Unavailable(offline = false)` y sesión intacta; sin sesión → `SessionEnded` sin llamada. Implementación: `refreshAfter(usedAccessToken)` bajo el `Mutex` y `RefreshOutcome` (`Refreshed(token)`, `SessionEnded`, `Unavailable(offline)`), para que el plugin (T-11) distinga reintentar, devolver el 401 o fallar como red/desconocido. `SessionManager` recibe ahora `AuthRemoteDataSource`. Desviación: el par renovado se actualiza en memoria **antes** de guardarlo en disco; si el guardado falla, la excepción se propaga pero el proceso sigue usando el par válido (con el orden inverso, un fallo de disco dejaría en memoria un refresh token ya invalidado por el backend).

- [x] **T-10 · SessionManager: cierre de sesión**
  - **Cubre:** RF-08 (CA-12, CA-21) · **Depende de:** T-09
  - **Alcance:** `logout()` → `LogoutResult` (`Success`, `NoConnection`, `Unknown`). 204 → `endSession(LoggedOut)`; sin red o error → sesión y caché intactas; si la sesión ya terminó durante la llamada → `Success`.
  - **Validación:** `SessionManagerTest` (204, `IOException`, 500, 401 con renovación rechazada).
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL; suite JVM: 112 tests, 0 fallos. `SessionManagerTest`: 24 tests (19 previos + 5 nuevos): 204 → `Success`, `POST /auth/logout`, `SignedOut(LoggedOut)` con sesión y datos borrados (CA-12, con mock); sin red → `NoConnection` con sesión, estado y datos intactos (CA-21); 500 → `Unknown` con sesión intacta; 401 → se cierra igualmente en el teléfono (`Success`, regla de RF-08); sesión ya cerrada → `Success` sin llamar al backend y conservando el motivo anterior. Implementación: `logout()` y `LogoutResult` (`Success`, `NoConnection`, `Unknown`) en `SessionManager`; la llamada de red se hace sin el `Mutex` (lo exige el plugin, que usará `refreshAfter`) y solo `endSession` lo toma. Si el plugin cierra la sesión durante el logout (renovación rechazada), `endSession(LoggedOut)` no sobrescribe el motivo original. El Bearer del logout lo añade el plugin: se comprueba en T-11. La invalidación real en el backend queda para la spec de perfil (plan).

- [x] **T-11 · Plugin SessionAuth**
  - **Cubre:** RF-06, RF-07 (CA-08, CA-09) · **Depende de:** T-09
  - **Alcance:** verificar el hook `Send` de `createClientPlugin` en Ktor 3.3.3 (alternativa del plan: `HttpSend.intercept`). `data/network/SessionAuthPlugin.kt`: Bearer con el token actual; ante 401, `refreshAfter` y un único reintento; si no hay token nuevo, devuelve el 401 original. Sin logs de tokens.
  - **Validación:** `SessionAuthPluginTest` con `ktor-client-mock`: Bearer presente; 401 → refresh → reintento 200; 401 tras reintento no renueva de nuevo; peticiones concurrentes con 401 → un solo refresh.
  - **Hecho cuando:** los tests pasan y la API usada queda anotada en la evidencia.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL; suite JVM: 123 tests, 0 fallos. API verificada en el código fuente de `ktor-client-core-jvm` 3.3.3 (`plugins/api/CommonHooks.kt`): `Send` es un `ClientHook` con `Sender.proceed(HttpRequestBuilder)`, que puede llamarse más de una vez; no hace falta la alternativa `HttpSend.intercept`. `SessionAuthPluginTest`: 11 tests. Unitarios (7, con renovación simulada): Bearer con el token vigente; sin sesión → sin Bearer ni renovación; 401 → renueva con el token usado y reintenta con el nuevo (CA-08); 401 en el reintento → se devuelve sin renovar otra vez; renovación rechazada → 401 original sin reintento; renovación sin conexión → `SessionRefreshOfflineException` (`IOException`, error de red en los data sources); error del servidor → `SessionRefreshFailedException` (no `IOException`, error desconocido). Integración con `SessionManager` y `AuthRemoteDataSource` reales sobre un mismo `MockEngine` (4): 10 peticiones concurrentes con acceso caducado → **1 sola llamada a `/auth/refresh`**, todas 200 y par nuevo guardado (CA-09); renovación rechazada → 401 y `SignedOut(Expired)` con sesión borrada; `logout()` envía `Bearer acc-1` (completa CA-12 con mock); `logout()` con acceso caducado renueva durante la llamada y cierra sin bloquearse (confirma que el `Mutex` no se toma durante la red). Implementación: `data/network/SessionAuthPlugin.kt` (`createClientPlugin` + `on(Send)`, configurado con `accessToken: () -> String?` y `refresh: suspend (String) -> RefreshOutcome`, que se resuelven al llamarse y rompen el ciclo de construcción con `SessionManager`); `SessionManager.accessToken()`. Sin logs de tokens.

## Integración

- [x] **T-12 · Cableado en AppContainer y cabecera**
  - **Cubre:** RF-05, RF-06 · **Depende de:** T-05, T-10, T-11
  - **Alcance:** `AppContainer`: `publicClient`, `apiClient` (con `SessionAuth`), `SessionStore`, `SessionManager` (`onSessionEnded = headerRepository::clear`), `AuthRepositoryImpl` (llama a `start()` tras login/registro). `HeaderRemoteDataSource` usa `apiClient` y deja de añadir `bearerAuth`. Adaptar `HeaderRemoteDataSourceTest` y `HeaderRepositoryImplTest` sin cambiar lo que comprueban.
  - **Validación:** `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
  - **Hecho cuando:** compila y toda la suite JVM pasa.
  - **Evidencia:** `./gradlew :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL; suite JVM: 123 tests, 0 fallos, 0 errores, 0 omitidos (resultados leídos de `app/build/test-results/testDebugUnitTest`; `HeaderRemoteDataSourceTest` 10 y `HeaderRepositoryImplTest` ejecutados). Implementación: `AppContainer` crea `publicClient` (OkHttp, JSON, timeouts) y `apiClient = publicClient.config { install(SessionAuth) }`, que comparte motor y configuración; el plugin resuelve `sessionManager` al llamarse. `SessionManager` (con `EncryptedSessionStore` sobre el DataStore `session` y `KeystoreSessionCipher`), `AuthRepository` y `HeaderRepository` son `by lazy` porque `SessionManager` ↔ `HeaderRepository` se referencian (`onSessionEnded = { headerRepository.clear() }`); el DataStore se llama `session`, como excluyen las reglas de backup (T-04). Nuevo `data/auth/AuthRepositoryImpl.kt`: con `AuthCallResult.Success` llama a `SessionManager.start()` y devuelve `AuthResult.Success`; con fallo devuelve el `AuthFailure`. `HeaderRemoteDataSource` usa el cliente autenticado y sus tres métodos ya no reciben `Session` (dejaba de usarse al quitar `bearerAuth`); `HeaderRepositoryImpl` solo cambia esas llamadas y sigue pidiendo `userId` a `SessionProvider` (ahora `SessionManager`). `HeaderRemoteDataSourceTest`: se quitan los argumentos de sesión y el cliente de prueba instala `SessionAuth` con el token `tok`, de modo que sigue comprobando el Bearer; `HeaderRepositoryImplTest` no necesitó cambios. Verificación en emulador (añadida después): `./gradlew :app:connectedDebugAndroidTest` con `-Pandroid.testInstrumentationRunnerArguments.class=…AuthWiringTest` y `email`/`password` leídos de `local.properties` en el propio comando (no se escriben en archivos; sin ellos el test se omite), en el AVD `Medium_Phone_API_37.0` contra el backend local (`DEV_TARGET=emulator`) → BUILD SUCCESSFUL; `AuthWiringTest`: 1 test, 0 fallos, 0 errores, 0 omitidos (leído del XML de resultados, que no contiene las credenciales). Con el `AppContainer` real comprueba: sin sesión → `SignedOut(null)`; `authRepository.login` real → `Success` y `SignedIn`; `headerRepository.refresh()` con las tres llamadas correctas y familias y nombre presentes (el backend solo responde si el Bearer lo pone `apiClient`); un `AppContainer` nuevo en `Loading` recupera la misma sesión desde el Keystore sin nuevo login y refresca la cabecera; `endSession(LoggedOut)` deja `SignedOut(LoggedOut)`, la caché `header_cache` vacía y un tercer contenedor sin sesión (RF-09, CA-13). Prueba manual previa (sin sesión, app con datos borrados, servidor de registro de peticiones): 0 peticiones al abrir y al pulsar «Reintentar», con captura de la cabecera en error. Límites: el «reinicio» es un `AppContainer` nuevo en el mismo proceso, no un proceso terminado; no se llama a `logout` (invalidaría las sesiones del usuario en el backend); la renovación tras un 401 real (CA-08) sigue cubierta solo con mocks. Sin verificar: no hay test unitario de `AuthRepositoryImpl` (no previsto en la tarea); los `BuildConfig.DEV_*` y `StaticSessionProvider` siguen en el código hasta T-13, pero `AppContainer` ya no los usa. **La app, tal como está ahora, no tiene pantalla de acceso (T-15 a T-17): sin sesión guardada la cabecera muestra error de autenticación.**

- [x] **T-13 · Retirar la sesión temporal y las credenciales de desarrollo**
  - **Cubre:** RF-11 (CA-15) · **Depende de:** T-12
  - **Alcance:** eliminar `StaticSessionProvider` y `StaticSessionProviderTest`; reducir `SessionFailure` a `NOT_SIGNED_IN` y `UNKNOWN` y ajustar el mapeo de `HeaderRepositoryImpl`; en `app/build.gradle.kts` quitar `DEV_EMAIL`/`DEV_PASSWORD` (campos `BuildConfig`, comprobación y mensaje), manteniendo la validación de `DEV_TARGET`.
  - **Validación:** `./gradlew :app:assembleDebug` con un `local.properties` sin `DEV_EMAIL`/`DEV_PASSWORD`; `./gradlew :app:testDebugUnitTest`; `grep` sin restos de `DEV_EMAIL`, `DEV_PASSWORD` ni `StaticSessionProvider` en el código.
  - **Hecho cuando:** compila sin esas claves, los tests pasan y no quedan referencias en el código.
  - **Evidencia:** `./gradlew :app:assembleDebug :app:testDebugUnitTest` con un `local.properties` reducido a `sdk.dir` y `DEV_TARGET` (copia del original guardada fuera del repositorio y restaurada después; `cmp` confirma que quedó idéntico) → BUILD SUCCESSFUL; suite JVM: 119 tests, 0 fallos, 0 errores, 0 omitidos (los 123 de T-12 menos los 4 de `StaticSessionProviderTest`; informes leídos de `app/build/test-results/testDebugUnitTest`, todos generados en esa ejecución). `BuildConfig` de debug generado solo con `BACKEND_BASE_URL` además de los campos estándar. `grep -rnE "DEV_EMAIL|DEV_PASSWORD|StaticSessionProvider" app/src app/build.gradle.kts` → sin resultados. Cambios: eliminados `session/StaticSessionProvider.kt` y su test; `SessionFailure` = `NOT_SIGNED_IN`, `UNKNOWN`; `HeaderRepositoryImpl` mapea `NOT_SIGNED_IN` → `Unauthorized` y `UNKNOWN` → `Unknown`; en `build.gradle.kts` desaparecen `devCredentialNames`, los dos campos de `BuildConfig`, `missingDevProperties` y su mensaje (la release sigue vaciando `BACKEND_BASE_URL`). Ajuste no previsto: `HeaderRepositoryImplTest.sesionNoDisponible_informaElFalloYNoTocaLaCache` usaba `SessionFailure.INVALID_CREDENTIALS`; ahora usa `NOT_SIGNED_IN`, que da el mismo `Unauthorized`, así que comprueba lo mismo. No ejecutado: la parte manual de CA-15 («abrir la app»; hoy no hay pantalla de acceso, llega con T-15 a T-17) ni una prueba con `DEV_TARGET` inválido (esa rama del build no se tocó). `CLAUDE.md` aún menciona `DEV_EMAIL`/`DEV_PASSWORD` y `StaticSessionProvider`: el plan lo actualiza en la validación final (etapa 8).

## Presentación

- [ ] **T-14 · Cadenas de acceso**
  - **Cubre:** RF-01–RF-04, RF-07, RF-12 · **Depende de:** —
  - **Alcance:** `strings.xml`: lema, pestañas, etiquetas y ayudas de campos, botones, enlaces del pie, errores por campo, mensajes de RF-04 y RF-07, "Sin conexión…", "Necesitas conexión para cerrar sesión.", "Mostrar contraseña"/"Ocultar contraseña".
  - **Validación:** `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** compila y los textos coinciden con la spec.
  - **Evidencia:** —

- [ ] **T-15 · Raíz de la app**
  - **Cubre:** RF-01, RF-05, RF-07 (CA-01, CA-07, CA-19) · **Depende de:** T-12
  - **Alcance:** `ui/root/RootViewModel.kt` (llama a `load()`, expone `SessionState`) y `ui/root/AppRoot.kt` (fondo del tema en `Loading`, `AuthScreen` en `SignedOut`, `MainScreen` con `key(generation)` y ViewModel con clave por generación en `SignedIn`); `MainActivity` muestra `AppRoot`.
  - **Validación:** `RootViewModelTest` (JVM) y `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan y compila.
  - **Evidencia:** —

- [ ] **T-16 · AuthViewModel**
  - **Cubre:** RF-01–RF-04, RF-12 (CA-02, CA-03, CA-04, CA-16, CA-17) · **Depende de:** T-01, T-12, T-15
  - **Alcance:** `ui/auth/AuthUiState.kt` y `AuthViewModel.kt`: pestaña, ambos formularios (valor, tocado, error), visibilidad de contraseñas, `submitting`, `generalError`, `endReason`. Validación al perder el foco y al enviar; envío único; errores por `AuthFailure`; el mensaje de cierre se oculta al enviar o cambiar de pestaña. Sin `SavedStateHandle`.
  - **Validación:** `AuthViewModelTest` (JVM, repositorio falso): validación por campo, sin llamada con errores, dos envíos → una llamada, cada fallo, valores conservados tras error de red, alternar pestaña conserva valores.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-17 · Pantalla de acceso: estructura e inicio de sesión**
  - **Cubre:** RF-01, RF-04, RF-07, RF-12 · **Depende de:** T-14, T-16
  - **Alcance:** `ui/auth/AuthScreen.kt` (con estado) y `AuthContent` (sin estado): logo (`ic_launcher_foreground`), nombre y lema; `SingleChoiceSegmentedButtonRow`; formulario de login (email y contraseña con mostrar/ocultar); botón con progreso y deshabilitado al enviar; mensaje de cierre o error general; enlace al pie; columna desplazable con `imePadding`; colores del tema; `@Preview`.
  - **Validación:** `./gradlew :app:assembleDebug :app:lintDebug` y revisión en preview.
  - **Hecho cuando:** compila, lint sin errores nuevos y la preview muestra el login.
  - **Evidencia:** —

- [ ] **T-18 · Pantalla de acceso: registro, teclado y foco**
  - **Cubre:** RF-02, RF-03, RF-12 · **Depende de:** T-17
  - **Alcance:** formulario de registro (nombre, email, contraseña, repetir, mostrar/ocultar en ambos); `KeyboardType` e `ImeAction` (Next/Done que envía); validación al perder el foco; foco al primer error al enviar; `BackHandler` en "Crear cuenta" → "Iniciar sesión"; errores con `supportingText` e `isError`.
  - **Validación:** `./gradlew :app:assembleDebug :app:lintDebug` y revisión en preview.
  - **Hecho cuando:** compila, lint sin errores nuevos y la preview muestra el registro.
  - **Evidencia:** —

- [ ] **T-19 · Tests de UI de acceso y raíz**
  - **Cubre:** CA-01, CA-04, CA-05, CA-06, CA-18, CA-19, CA-20, CA-22, CA-23 · **Depende de:** T-15, T-18
  - **Alcance:** `AuthScreenTest` (campos vacíos y acceso a registro; errores por campo y foco; mensajes de RF-04 sin marcar el email; `StateRestorationTester` con datos y errores; Atrás en registro; mostrar/ocultar y su descripción; alternar por segmentos y por enlace con `selected`; etiquetas accesibles) y `AppRootTest` (`SignedIn` + Atrás termina la Activity).
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest`.
  - **Hecho cuando:** los tests pasan en emulador.
  - **Evidencia:** —

## Validación

- [ ] **T-20 · Comprobaciones automáticas completas**
  - **Cubre:** todos · **Depende de:** T-19
  - **Alcance:** suite completa, incluida la regresión de la 002 (`MainScreenTest`, `MainScreenBehaviorTest`, `MainViewModelTest`, `HeaderRepositoryImplTest`, `HeaderRemoteDataSourceTest`).
  - **Validación:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` y `./gradlew :app:connectedDebugAndroidTest`.
  - **Hecho cuando:** todo pasa o cada fallo queda registrado con su causa.
  - **Evidencia:** —

- [ ] **T-21 · Pruebas manuales contra el backend**
  - **Cubre:** CA-02, CA-03, CA-07, CA-08, CA-10, CA-13, CA-14, CA-15, CA-17, CA-19, CA-20 y pendientes de la 002 · **Depende de:** T-20
  - **Alcance:** emulador o dispositivo con backend local: login y registro reales; matar el proceso y reiniciar; esperar >15 min y recargar; cerrar sesiones desde otro cliente y esperar la caducidad; reabrir sin red con y sin sesión; modo avión en login y registro; Atrás en la pantalla principal; TalkBack y fuente máxima con el teclado abierto; build sin `DEV_*`. De la 002: CA-09, CA-10, CA-16, CA-13 (TalkBack), CA-17 y CA-18 con dos usuarios.
  - **Validación:** pasos manuales registrados con resultado observado.
  - **Hecho cuando:** cada escenario tiene resultado o motivo de bloqueo.
  - **Evidencia:** —

- [ ] **T-22 · Documentación y revisión final**
  - **Cubre:** CA-01 a CA-23 · **Depende de:** T-21
  - **Alcance:** actualizar `CLAUDE.md` (sin credenciales de desarrollo ni sesión temporal; sesión y plugin), `docs/ROADMAP.md` (estado y nota de la 002) y la evidencia de la 002 que se haya verificado; tabla de cada criterio con su evidencia; confirmar que no se implementó nada fuera de alcance.
  - **Validación:** revisión documental.
  - **Hecho cuando:** cada criterio tiene evidencia o está marcado como no verificado, y SPEC, PLAN y TASKS son coherentes.
  - **Evidencia:** —
