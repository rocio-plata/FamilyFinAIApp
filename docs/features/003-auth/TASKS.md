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

- [ ] **T-05 · Login y registro remotos**
  - **Cubre:** RF-01, RF-02, RF-04 (CA-05, CA-06, CA-17) · **Depende de:** T-02
  - **Alcance:** `data/auth/AuthRemoteDataSource.kt` con `POST /auth/login` y `POST /auth/register` y DTOs privados (`toString()` sin secretos). Mapeo: 401 `IDENTITY.INVALID_CREDENTIALS` → `InvalidCredentials`; 409 `IDENTITY.EMAIL_ALREADY_REGISTERED` y 400 `IDENTITY.WEAK_PASSWORD`/`IDENTITY.INVALID_DISPLAY_NAME`/`SHARED.INVALID_EMAIL` → `RegistrationRejected`; `IOException` → `Network`; resto → `Unknown`. Solo se lee `error`. Relanza `CancellationException`.
  - **Validación:** `AuthRemoteDataSourceTest` con `ktor-client-mock` (éxito con `userId` y tokens, cada código, sin red, cuerpo inválido).
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-06 · Renovación y cierre remotos**
  - **Cubre:** RF-06, RF-07, RF-08 · **Depende de:** T-05
  - **Alcance:** `POST /auth/refresh` (200 → nuevo par; 401 `AUTH.POSSIBLE_TOKEN_THEFT` → `Security`; otro 401 → `Expired`; `IOException` → red; 5xx → desconocido) y `POST /auth/logout` (204 → éxito; `IOException` → sin conexión; otros → desconocido).
  - **Validación:** casos añadidos a `AuthRemoteDataSourceTest`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Sesión

- [ ] **T-07 · Vaciado de la cabecera**
  - **Cubre:** RF-09 (CA-13) · **Depende de:** —
  - **Alcance:** `HeaderRepository.clear()` y su implementación en `HeaderRepositoryImpl`: `local.clear()` y estado en memoria a `HeaderData()`.
  - **Validación:** test en `HeaderRepositoryImplTest` (tras `clear()`, `header` emite vacío y la caché no tiene datos) y regresión de la clase.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-08 · SessionManager: carga, inicio y fin de sesión**
  - **Cubre:** RF-05, RF-09, RF-10 (CA-07, CA-13) · **Depende de:** T-03, T-07
  - **Alcance:** `session/SessionManager.kt` con `StateFlow<SessionState>`, `load()` (sesión guardada → `SignedIn`; ninguna → borrar caché y `SignedOut(null)`), `start(session)` (guardar y `SignedIn` con nueva `generation`), `endSession(reason)` (borrar sesión, `onSessionEnded`, `SignedOut(reason)`), con `Mutex`. Implementa `SessionProvider`. Store abstraído para testear en JVM.
  - **Validación:** `SessionManagerTest` (JVM, store falso): cada transición, `onSessionEnded` llamado, `current()` sin sesión lanza `SessionUnavailableException`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-09 · SessionManager: renovación única**
  - **Cubre:** RF-06, RF-07, RF-10 (CA-09, CA-10, CA-11, CA-14) · **Depende de:** T-06, T-08
  - **Alcance:** `refreshAfter(usedToken)`: con `Mutex`; si el token guardado difiere del usado, lo devuelve sin llamar al backend; si no, renueva, guarda el par nuevo **antes** de devolverlo; 401 → `endSession(Expired|Security)` y `null`; red o 5xx → sesión intacta y fallo.
  - **Validación:** `SessionManagerTest`: 10 llamadas concurrentes → 1 llamada a `/auth/refresh`; cada motivo de cierre; `IOException` conserva la sesión.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-10 · SessionManager: cierre de sesión**
  - **Cubre:** RF-08 (CA-12, CA-21) · **Depende de:** T-09
  - **Alcance:** `logout()` → `LogoutResult` (`Success`, `NoConnection`, `Unknown`). 204 → `endSession(LoggedOut)`; sin red o error → sesión y caché intactas; si la sesión ya terminó durante la llamada → `Success`.
  - **Validación:** `SessionManagerTest` (204, `IOException`, 500, 401 con renovación rechazada).
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

- [ ] **T-11 · Plugin SessionAuth**
  - **Cubre:** RF-06, RF-07 (CA-08, CA-09) · **Depende de:** T-09
  - **Alcance:** verificar el hook `Send` de `createClientPlugin` en Ktor 3.3.3 (alternativa del plan: `HttpSend.intercept`). `data/network/SessionAuthPlugin.kt`: Bearer con el token actual; ante 401, `refreshAfter` y un único reintento; si no hay token nuevo, devuelve el 401 original. Sin logs de tokens.
  - **Validación:** `SessionAuthPluginTest` con `ktor-client-mock`: Bearer presente; 401 → refresh → reintento 200; 401 tras reintento no renueva de nuevo; peticiones concurrentes con 401 → un solo refresh.
  - **Hecho cuando:** los tests pasan y la API usada queda anotada en la evidencia.
  - **Evidencia:** —

## Integración

- [ ] **T-12 · Cableado en AppContainer y cabecera**
  - **Cubre:** RF-05, RF-06 · **Depende de:** T-05, T-10, T-11
  - **Alcance:** `AppContainer`: `publicClient`, `apiClient` (con `SessionAuth`), `SessionStore`, `SessionManager` (`onSessionEnded = headerRepository::clear`), `AuthRepositoryImpl` (llama a `start()` tras login/registro). `HeaderRemoteDataSource` usa `apiClient` y deja de añadir `bearerAuth`. Adaptar `HeaderRemoteDataSourceTest` y `HeaderRepositoryImplTest` sin cambiar lo que comprueban.
  - **Validación:** `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
  - **Hecho cuando:** compila y toda la suite JVM pasa.
  - **Evidencia:** —

- [ ] **T-13 · Retirar la sesión temporal y las credenciales de desarrollo**
  - **Cubre:** RF-11 (CA-15) · **Depende de:** T-12
  - **Alcance:** eliminar `StaticSessionProvider` y `StaticSessionProviderTest`; reducir `SessionFailure` a `NOT_SIGNED_IN` y `UNKNOWN` y ajustar el mapeo de `HeaderRepositoryImpl`; en `app/build.gradle.kts` quitar `DEV_EMAIL`/`DEV_PASSWORD` (campos `BuildConfig`, comprobación y mensaje), manteniendo la validación de `DEV_TARGET`.
  - **Validación:** `./gradlew :app:assembleDebug` con un `local.properties` sin `DEV_EMAIL`/`DEV_PASSWORD`; `./gradlew :app:testDebugUnitTest`; `grep` sin restos de `DEV_EMAIL`, `DEV_PASSWORD` ni `StaticSessionProvider` en el código.
  - **Hecho cuando:** compila sin esas claves, los tests pasan y no quedan referencias en el código.
  - **Evidencia:** —

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
