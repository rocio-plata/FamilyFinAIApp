# PLAN: Autenticación y sesión (registro, login, renovación y cierre)

**SPEC de referencia:** `docs/features/003-auth/SPEC.md`
**Versión de la spec revisada:** SPEC aprobada el 2026-11-03, con RF-01 a RF-12 y CA-01 a CA-23 (sin commit propio al redactar este plan).
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
| `FamilyFinApp` / `AppContainer` | `FamilyFinApp.kt`, `AppContainer.kt` | DI manual: un `HttpClient` (OkHttp, JSON `ignoreUnknownKeys`, timeouts 10/15 s), DataStore `header_cache`, `ConnectivityObserver`, `StaticSessionProvider` y `HeaderRepository`. Se amplía con la sesión real y un segundo cliente autenticado. |
| `MainActivity` | `MainActivity.kt` | `setContent { MainScreen() }`. Pasa a mostrar la raíz que decide entre acceso y pantalla principal. |
| `SessionProvider`, `Session`, `SessionFailure`, `SessionUnavailableException` | `session/SessionProvider.kt` | Contrato que usa la cabecera para obtener `userId` y token. Se conserva; cambia la implementación. `Session.toString()` ya oculta el token. |
| `StaticSessionProvider` (+ test) | `session/StaticSessionProvider.kt`, `app/src/test/.../session/StaticSessionProviderTest.kt` | Login temporal con `BuildConfig.DEV_EMAIL/DEV_PASSWORD`, en memoria, sin refresh. Se elimina (RF-11). Su manejo de errores y DTOs sirve de referencia. |
| `HeaderRemoteDataSource` | `data/header/HeaderRemoteDataSource.kt` | 3 GET con `bearerAuth(session.accessToken)`; 401 → `HeaderFailure.Unauthorized`; lee solo `error` del cuerpo. Pasa a usar el cliente autenticado (sin añadir el token por su cuenta). |
| `HeaderLocalDataSource` / `HeaderRepositoryImpl` | `data/header/` | Caché en DataStore con `clear()`; el repositorio guarda además el último `HeaderData` en memoria (`MutableStateFlow`). Hay que vaciar ambos al terminar la sesión (RF-09). |
| `MainScreen` / `MainViewModel` | `ui/main/` | Entrada con estado (ViewModel por `viewModelFactory`, refresco en `ON_START`) y sobrecarga sin estado. `BackHandler` solo fuera de Inicio, por lo que Atrás en Inicio sale de la app. |
| `ConnectivityObserver` | `data/network/ConnectivityObserver.kt` | `Flow<Boolean>` de conectividad. Sin cambios; la renovación fallida por red se reintenta con el refresco existente al recuperar red (RF-10). |
| Build y configuración local | `app/build.gradle.kts` | `DEV_TARGET`/`BACKEND_BASE_URL` → `BuildConfig.BACKEND_BASE_URL`; `DEV_EMAIL`/`DEV_PASSWORD` → `BuildConfig` y `checkDevProperties` que falla si faltan. En release `BACKEND_BASE_URL` queda vacío. |
| Manifest, backup y red de debug | `AndroidManifest.xml`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `app/src/debug/...` | `INTERNET`, `ACCESS_NETWORK_STATE`, `adjustResize`, solo vertical; backups excluyen `header_cache`; cleartext solo en debug hacia `10.0.2.2` y `localhost`. |
| Dependencias | `gradle/libs.versions.toml` | Compose BOM 2026.02.01 (Material 3), lifecycle 2.10.0, Ktor 3.3.3 (core, okhttp, content-negotiation, mock), kotlinx-serialization 1.9.0, coroutines-test 1.10.2, DataStore 1.3.0. **No hay** librería de navegación ni de cifrado. |
| Recursos | `res/values/strings.xml`, `res/drawable/ic_launcher_foreground.xml` | Textos traducibles; el logo vectorial del lanzador puede reutilizarse en la pantalla de acceso. |
| Contrato backend | `docs/backend/api-contract.md` §1.1, §2.1, §3.2, §4 | `register` (201, incluye `userId`, tokens, `defaultFamilyId`), `login` (200), `refresh` (200; 401 `AUTH.INVALID_REFRESH_TOKEN` / `AUTH.POSSIBLE_TOKEN_THEFT`), `logout` (204, Bearer). Errores `{error, message, details}`. |

**Convenciones y patrón de referencia:** paquetes por capa y funcionalidad
(`domain/header`, `data/header`, `ui/main`, `session`). Entrada de pantalla con
estado + contenido sin estado, `StateFlow` + `collectAsStateWithLifecycle`,
ViewModels creados con `viewModelFactory` leyendo `AppContainer`. Data sources
que nunca lanzan por red/servidor y relanzan `CancellationException`. Textos en
`strings.xml`, colores en `ui/theme`. Patrón de referencia: la 002
(`docs/features/002-main-header/PLAN.md`).

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de datos y eventos hasta la interfaz. Usa un diagrama si aporta claridad. -->

**Decisiones confirmadas por la persona:**
1. **Navegación:** sin librería nueva. Una raíz elige qué mostrar según el estado
   de la sesión. Login y registro son dos pestañas de una misma pantalla.
2. **Almacenamiento de tokens:** DataStore con el contenido cifrado (AES-GCM) con
   una clave del Android Keystore, excluido de backups. Sin dependencias nuevas.
3. **Renovación:** plugin propio de Ktor con `ktor-client-core`, con renovación
   única compartida entre peticiones simultáneas.
4. **Validación del logout:** tests con `ktor-client-mock`; la comprobación
   contra el backend real se hace en la spec de perfil.

**Enfoque:**

```
MainActivity ──► AppRoot ──► RootViewModel (StateFlow<SessionState>)
                   │            ▲
                   │            │ observa
                   ├─ Loading ──┤  (fondo vacío mientras se lee la sesión)
                   ├─ SignedOut(reason) ──► AuthScreen ──► AuthViewModel ──► AuthRepository
                   └─ SignedIn(userId, generation) ──► MainScreen (key = generation)
                                │
                   SessionManager (session/): única fuente de verdad de la sesión
                     ├─ SessionStore (data/session): DataStore cifrado con Keystore
                     ├─ AuthRemoteDataSource (data/auth): register, login, refresh, logout
                     │     └─ publicClient (sin Bearer)
                     └─ onSessionEnded ──► HeaderRepository.clear()

apiClient = publicClient + plugin SessionAuth ──► HeaderRemoteDataSource y futuras features
   SessionAuth: añade Bearer ► 401 ► SessionManager.refreshAfter(token) ► reintenta una vez
```

**Recorrido:**

- **Arranque (RF-05, RF-10):** `RootViewModel` pide a `SessionManager` que
  cargue la sesión guardada. Mientras tanto la raíz muestra un fondo vacío del
  tema, de modo que el login no aparece ni un instante. Si hay sesión →
  `SignedIn`; si no hay, o no se puede descifrar → se borran sesión y caché y
  pasa a `SignedOut(null)`. No se llama a la red al arrancar: la cabecera
  refresca como hoy y, sin conexión, muestra la caché.
- **Login y registro (RF-01, RF-02, RF-04):** `AuthViewModel` valida con reglas
  puras del dominio, llama a `AuthRepository`, y con éxito `SessionManager`
  guarda la sesión cifrada y emite `SignedIn`. La raíz cambia a `MainScreen`,
  cuyo ViewModel arranca y carga la cabecera (familia personal tras registrarse).
- **Renovación (RF-06):** el plugin `SessionAuth` añade el Bearer. Ante un 401,
  llama a `SessionManager.refreshAfter(tokenUsado)`, que con un `Mutex`:
  - si el token guardado ya es distinto al usado, otra petición ya renovó → lo
    devuelve sin llamar al backend (evita reutilizar el refresh token, CA-09);
  - si no, llama a `POST /auth/refresh`, guarda el nuevo par **antes** de
    devolverlo y reintenta la petición original una sola vez.
- **Cierre forzado (RF-07, RF-09):** si `refresh` responde 401,
  `SessionManager.endSession(reason)` borra la sesión, ejecuta
  `onSessionEnded` (caché y memoria de la cabecera) y emite
  `SignedOut(Expired | Security)`. La petición original devuelve su 401 y la
  raíz muestra el acceso con el mensaje. Fallo de red o 5xx al renovar: la
  sesión se conserva y la petición falla como error de red/desconocido.
- **Cierre de sesión (RF-08):** `SessionManager.logout()` llama a
  `POST /auth/logout` por `apiClient`. 204 → `endSession(LoggedOut)`. Sin red →
  devuelve `LogoutResult.NoConnection` y no toca nada (CA-21). Si la sesión ya
  no era válida, la renovación la habrá cerrado y el resultado es éxito. Otros
  errores → `LogoutResult.Unknown`, sesión intacta. Sin llamador de UI en esta
  spec.

## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

| Módulo | Existe / nuevo | Responsabilidad y cambios | Dependencias afectadas |
| --- | --- | --- | --- |
| No aplica: único módulo `:app`; modularizar queda fuera de alcance | Existente | Nuevos paquetes `domain/auth`, `data/auth`, `data/session`, `ui/auth`, `ui/root`; se amplían `session` y `data/network`. | Ninguna librería nueva. |

Dirección de dependencias dentro de `:app`: `ui` → `domain` ← `data`;
`domain/auth` es Kotlin puro (validación y tipos de fallo), sin Android, Ktor ni DTOs.

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `domain/auth/AuthValidation.kt` | Crear | Reglas puras: email (recortado, regex del contrato), contraseña de registro ≥8, contraseña de login no vacía, repetición igual, nombre 1–60 recortado. Devuelven el error por campo. | RF-03 |
| `domain/auth/AuthFailure.kt`, `SessionEndReason.kt` | Crear | `AuthFailure`: `InvalidCredentials`, `RegistrationRejected`, `Network`, `Unknown`. `SessionEndReason`: `Expired`, `Security`, `LoggedOut`. | RF-04, RF-07 |
| `domain/auth/AuthRepository.kt` | Crear | Contrato `login(email, password)` y `register(name, email, password)` → `Result` de dominio. | RF-01, RF-02 |
| `data/auth/AuthRemoteDataSource.kt` (+ DTOs `@Serializable` privados) | Crear | 4 POST con `publicClient` (logout con `apiClient`). Mapea códigos: `IDENTITY.INVALID_CREDENTIALS` → `InvalidCredentials`; `IDENTITY.EMAIL_ALREADY_REGISTERED`, `IDENTITY.WEAK_PASSWORD`, `IDENTITY.INVALID_DISPLAY_NAME`, `SHARED.INVALID_EMAIL` → `RegistrationRejected` (mensaje neutro); refresh 401 + código → motivo. DTOs con `toString()` sin secretos. | RF-01, RF-02, RF-04, RF-06–RF-08 |
| `data/auth/AuthRepositoryImpl.kt` | Crear | Llama al remoto y, con éxito, entrega la sesión a `SessionManager.start()`. | RF-01, RF-02 |
| `data/session/SessionStore.kt` | Crear | DataStore `session` con un único valor cifrado (AES/GCM, IV aleatorio, clave Keystore no exportable). JSON interno con `schemaVersion`, `userId`, `accessToken`, `refreshToken`. Lectura fallida o versión desconocida → sin sesión. | RF-05, RF-09, privacidad |
| `session/SessionManager.kt` | Crear | `StateFlow<SessionState>`, `load()`, `start()`, `refreshAfter(token)` (single-flight con `Mutex`), `endSession(reason)`, `logout()`. Implementa `SessionProvider` (devuelve la sesión guardada o lanza `SessionUnavailableException`). | RF-05–RF-10 |
| `session/SessionProvider.kt` | Modificar | `SessionFailure` se reduce a los casos reales (`NOT_SIGNED_IN`, `UNKNOWN`); `HeaderRepositoryImpl` ajusta su mapeo (sin sesión → `Unauthorized`). | RF-11 |
| `session/StaticSessionProvider.kt` y su test | Eliminar | Sustituido por `SessionManager`. | RF-11 |
| `data/network/SessionAuthPlugin.kt` | Crear | Plugin de cliente Ktor: Bearer en cada petición; ante 401, renovación compartida y un único reintento; nunca registra tokens. | RF-06, RF-07 |
| `data/header/HeaderRemoteDataSource.kt` | Modificar | Usa `apiClient` y deja de añadir `bearerAuth` por su cuenta. Sin otros cambios de comportamiento. | RF-06 |
| `domain/header/HeaderRepository.kt`, `data/header/HeaderRepositoryImpl.kt` | Modificar | Nuevo `clear()`: vacía caché (`local.clear()`) y estado en memoria. | RF-09 |
| `AppContainer.kt` | Modificar | `publicClient`, `apiClient`, `SessionStore`, `SessionManager` (con `onSessionEnded = headerRepository::clear`), `AuthRepository`; sin `BuildConfig.DEV_*`. | RF-05, RF-11 |
| `ui/root/AppRoot.kt`, `RootViewModel.kt` | Crear | Muestra fondo vacío / `AuthScreen` / `MainScreen` según `SessionState`; `MainScreen` con `key(generation)` y ViewModel con clave por generación para no reutilizar estado de una sesión anterior. | RF-01, RF-05, RF-07 |
| `ui/auth/AuthScreen.kt`, `AuthContent` (sin estado) | Crear | Logo, nombre y lema; `SingleChoiceSegmentedButtonRow` "Iniciar sesión / Crear cuenta"; formularios; enlace al pie; mensaje de sesión terminada; progreso en el botón; desplazable con teclado (`imePadding`). | RF-01–RF-04, RF-07, RF-12 |
| `ui/auth/AuthViewModel.kt`, `AuthUiState.kt` | Crear | Estado de ambos formularios (valores, campos tocados, errores, visibilidad de contraseña), pestaña activa, envío en curso, error general y motivo de cierre. | RF-01–RF-04, RF-12 |
| `MainActivity.kt` | Modificar | `setContent { AppRoot() }`. | RF-01, RF-05 |
| `res/values/strings.xml` | Modificar | Textos de acceso, errores y mensajes de la spec. | Todos |
| `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Modificar | Excluir `datastore/session.preferences_pb`. | Restricción de la spec |
| `app/build.gradle.kts` | Modificar | Quitar `DEV_EMAIL`/`DEV_PASSWORD` (campos, comprobación y mensaje). `checkDevProperties` solo valida `DEV_TARGET`. | RF-11 |
| `CLAUDE.md`, `docs/ROADMAP.md` | Modificar | Quitar la referencia a credenciales de desarrollo y a la sesión temporal; estado del roadmap. | Documentación |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Modelos y contratos de entrada y salida:** requests `{email, password}`,
  `{email, password, displayName}`, `{refreshToken}`; respuestas con
  `userId`, `accessToken`, `refreshToken` (se ignoran `email`, `displayName` y
  `defaultFamilyId`: la cabecera ya los obtiene). Errores: solo se lee `error`;
  `message` y `details` no se muestran ni se registran. `SessionState`:
  `Loading` | `SignedOut(reason: SessionEndReason?)` | `SignedIn(userId, generation)`.
- **Identificadores, relaciones y restricciones:** la sesión pertenece a un
  `userId`; la caché de la cabecera ya se asocia a él (CA-18 de la 002).
  `generation` es un contador en memoria que cambia en cada inicio de sesión.
- **Origen de los datos mostrados y transformaciones:** email y nombre se
  recortan antes de validar y enviar; la contraseña se envía tal cual. Los
  mensajes de error salen de `strings.xml` según `AuthFailure` /
  `SessionEndReason`.
- **Persistencia, consultas y actualizaciones:** DataStore `session` con un único
  valor: Base64(IV + texto cifrado AES-256-GCM) del JSON de sesión. Clave
  `familyfin_session_key` en `AndroidKeyStore` (no exportable). Se escribe en
  login, registro y cada renovación (antes de usar los tokens nuevos) y se borra
  al terminar la sesión. Las contraseñas nunca se guardan.
- **Convivencia entre datos locales y remotos:** el backend es la autoridad de
  la validez de la sesión; la app solo la descubre al recibir 401. Sin conexión
  la sesión guardada se considera válida (RF-10).
- **Compatibilidad y migraciones de datos existentes:** no hay sesión guardada
  previa (la temporal era en memoria). La caché `header_cache` existente se
  mantiene; al primer arranque sin sesión se borra junto con todo lo demás.
  `schemaVersion` desconocido o fallo al descifrar (p. ej. clave perdida tras
  restaurar el teléfono) → sin sesión.

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de MOBILE_GUIDELINES.md. -->

- **Gestión del estado de interfaz y navegación:** `RootViewModel` expone
  `SessionState`; la raíz es un `when` sin pila, por lo que Atrás en la pantalla
  principal (Inicio) sale de la app y nunca vuelve al acceso (CA-19).
  `AuthUiState`: pestaña (`Login`/`Register`), formularios con valor, `touched`
  y error por campo, `passwordVisible`, `submitting`, `generalError`
  (`AuthFailure`) y `endReason`. En la pestaña "Crear cuenta", un `BackHandler`
  vuelve a "Iniciar sesión" (CA-18); en "Iniciar sesión" Atrás sale de la app.
  El mensaje de cierre forzado se muestra sobre el formulario hasta que el
  usuario envía o cambia de pestaña.
- **Conservación y restauración del estado:** el estado de los formularios vive
  en `AuthViewModel` (sobrevive a recreación y segundo plano, CA-18) y **no** se
  guarda en `SavedStateHandle` ni `rememberSaveable`: tras terminarse el proceso
  los campos aparecen vacíos y las contraseñas nunca acaban en el estado
  guardado. Al pasar a `SignedIn` y luego a `SignedOut`, la raíz crea un
  `AuthViewModel` nuevo (clave por generación), de modo que el login aparece
  vacío (RF-09).
- **Ejecución, concurrencia y cancelación de operaciones:** envíos en
  `viewModelScope`; un envío en curso bloquea nuevos envíos (CA-16) y continúa
  aunque la app pase a segundo plano. `SessionManager` usa un `Mutex` para
  `refreshAfter`, `start`, `endSession` y `logout`; su ámbito es de aplicación
  (`AppContainer`), así una renovación no se cancela al cerrar una pantalla.
  `CancellationException` se relanza siempre. Timeouts 10/15 s como en la 002.
- **Errores, reintentos y prevención de duplicados:** validación al perder el
  foco (`onFocusChanged` tras haber tenido foco) y al enviar; con errores no se
  llama al remoto y se mueve el foco al primer campo con error (CA-04).
  `IOException` → "Sin conexión…" (CA-17); otros → error genérico con reintento
  (volver a pulsar el botón). El plugin reintenta una sola vez tras renovar; si
  el reintento vuelve a dar 401, no renueva de nuevo. Las llamadas a
  `/auth/*` no pasan por el plugin.
- **Otras consideraciones mobile aplicables y su solución:** teclado: email con
  `KeyboardType.Email`, contraseñas con `KeyboardType.Password`, `ImeAction.Next`
  y `Done` (que envía) en el último campo. Pantalla en `Column` desplazable con
  `imePadding` y `adjustResize` (ya configurado). Accesibilidad: etiquetas
  visibles en cada campo, error con `isError` + `supportingText` (texto, no solo
  color) y anunciado; control de contraseña con descripción "Mostrar
  contraseña"/"Ocultar contraseña"; segmentos con estado seleccionado nativo;
  áreas ≥48 dp (CA-20, CA-22, CA-23). Privacidad: tokens y contraseñas fuera de
  logs y `toString()`; sesión cifrada y excluida de backups. Colores solo desde
  `ui/theme`.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

- **Sin dependencias nuevas.** Ktor, kotlinx.serialization, DataStore,
  lifecycle y Material 3 ya están en `gradle/libs.versions.toml`. El cifrado usa
  `javax.crypto` y `AndroidKeyStore` de la plataforma (minSdk 34). El plugin usa
  `createClientPlugin` y el hook `Send` de `ktor-client-core` 3.3.3 (se verifica
  la API en la primera tarea).
- **Configuración:** `local.properties` deja de necesitar `DEV_EMAIL` y
  `DEV_PASSWORD`; si siguen presentes, se ignoran (conviene borrarlos a mano).
  Se mantienen `DEV_TARGET` y `BACKEND_BASE_URL`.
- **Permisos:** sin cambios.
- **Fuera de este plan:** la URL de producción (en release `BACKEND_BASE_URL`
  sigue vacía, como hoy).

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, UI o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

Todos los tests son nuevos salvo indicación. "Mock" = `ktor-client-mock`.

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Unit `RootViewModelTest` (sin sesión → `SignedOut(null)`) + UI `AuthScreenTest` (campos vacíos, acceso a registro) | JVM; emulador | Tests en verde |
| CA-02 | Unit `AuthViewModelTest` (éxito → `start`) + manual contra backend | JVM; emulador + backend local con cuenta de prueba | Tests + nota manual |
| CA-03 | Unit `AuthViewModelTest` (registro OK) + manual contra backend | Emulador + backend; email nuevo | Tests + nota manual |
| CA-04 | Unit `AuthValidationTest` (cada regla) y `AuthViewModelTest` (blur/envío/corrección, sin llamada al remoto) + UI (foco al primer error) | JVM; emulador | Tests en verde |
| CA-05 | Unit `AuthRemoteDataSourceTest` (401 `IDENTITY.INVALID_CREDENTIALS` → `InvalidCredentials`) + UI (mensaje sin marcar campo) | JVM mock; emulador | Tests en verde |
| CA-06 | Unit (409 → `RegistrationRejected`) + UI (mensaje neutro, email sin error) | JVM mock; emulador | Tests en verde |
| CA-07 | Instrumentado `SessionStoreTest` (guardar, leer en instancia nueva) + manual (matar proceso y reiniciar) | Emulador con Keystore | Test + nota manual |
| CA-08 | Unit `SessionAuthPluginTest` (401 → refresh → reintento OK) + manual (esperar >15 min) | JVM mock; emulador + backend | Test + nota manual |
| CA-09 | Unit `SessionAuthPluginTest`/`SessionManagerTest` (N peticiones concurrentes con 401 → una sola llamada a `/auth/refresh`) | JVM mock | Test en verde (cuenta de llamadas) |
| CA-10 | Unit `SessionManagerTest` (refresh 401 `AUTH.INVALID_REFRESH_TOKEN` → `SignedOut(Expired)`) + UI (mensaje) + manual (logout desde otro cliente y esperar caducidad) | JVM mock; emulador + backend | Tests + nota manual |
| CA-11 | Unit (refresh 401 `AUTH.POSSIBLE_TOKEN_THEFT` → `SignedOut(Security)`) + UI (mensaje) | JVM mock; emulador | Tests en verde |
| CA-12 | Unit `SessionManagerTest` (logout 204 → `SignedOut(LoggedOut)`, sesión y caché borradas, petición con Bearer). Backend real: en la spec de perfil | JVM mock | Test en verde |
| CA-13 | Unit (`endSession` → store vacío + `onSessionEnded` llamado) + unit `HeaderRepositoryImplTest` (`clear()` vacía caché y memoria) + manual (reabrir sin red) | JVM con DataStore temporal; emulador | Tests + nota manual |
| CA-14 | Unit `SessionManagerTest` (refresh con `IOException` → sesión intacta) + manual (modo avión, reabrir, activar red) | JVM mock; emulador | Test + nota manual |
| CA-15 | `./gradlew :app:assembleDebug` sin `DEV_EMAIL`/`DEV_PASSWORD` + abrir la app | Copia de `local.properties` sin esas claves | Salida del build + nota manual |
| CA-16 | Unit `AuthViewModelTest` (dos envíos durante uno en curso → una llamada) + UI (botón deshabilitado) | JVM; emulador | Tests en verde |
| CA-17 | Unit (`IOException` → `Network`, valores conservados) + manual (modo avión) | JVM; emulador | Test + nota manual |
| CA-18 | UI (`StateRestorationTester`: datos y errores conservados; Atrás en "Crear cuenta" → "Iniciar sesión") | Emulador | Test en verde |
| CA-19 | UI `AppRootTest` (`SignedIn`, Atrás → la Activity termina, sin acceso) + manual | Emulador | Test + nota manual |
| CA-20 | UI (etiquetas y errores con `contentDescription`/semántica) + manual TalkBack, fuente máxima y teclado abierto | Emulador | Test + nota manual |
| CA-21 | Unit `SessionManagerTest` (logout con `IOException` → `NoConnection`, sesión y caché intactas) | JVM mock | Test en verde |
| CA-22 | UI (alternar visibilidad; descripción cambia) | Emulador | Test en verde |
| CA-23 | UI (escribir en ambos, alternar por segmentos y enlace, contenido conservado, segmento `selected`) | Emulador | Test en verde |

**Comprobaciones de regresión:** tests existentes de la 002
(`HeaderRepositoryImplTest`, `HeaderRemoteDataSourceTest`, `MainViewModelTest`,
`MainScreenTest`, `MainScreenBehaviorTest`) adaptados al cliente autenticado y al
`SessionProvider` nuevo, sin cambiar lo que comprueban. Revisar en manual los
criterios de la 002 que quedaron bloqueados por `StaticSessionProvider`
(CA-09, CA-10 y CA-16 de la 002), ahora verificables con sesión persistente.

**Comandos verificados para compilar y ejecutar tests:** (de `AGENTS.md`; no
ejecutados al planificar) `./gradlew :app:assembleDebug`,
`./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug` y
`./gradlew :app:connectedDebugAndroidTest` con emulador.

**Pruebas en dispositivo, emulador o simulador:** emulador API 34+ con el
backend local (`DEV_TARGET=emulator`): login y registro reales, matar el
proceso, reinicio del emulador, modo avión, caducidad del acceso (15 min),
TalkBack y fuente máxima. No hace falta hardware real.

**Limitaciones del entorno:** CA-11 (robo) solo se valida con mock: provocar el
reuso real exige manipular tokens. La invalidación real del logout (CA-12) queda
para la spec de perfil. La disponibilidad de un AVD y del backend se comprueba
al ejecutar; si faltan, se registra como bloqueado en `TASKS.md`.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. **Dominio de auth** (validación, fallos, motivos, contrato del repositorio).
   Comprobación: `AuthValidationTest` (CA-04).
2. **Almacenamiento cifrado de sesión** (`SessionStore` + reglas de backup).
   Comprobación: test instrumentado (CA-07).
3. **Remoto de auth** (4 endpoints, mapeo de códigos). Comprobación: tests con
   mock (CA-05, CA-06, CA-17).
4. **`SessionManager` y plugin `SessionAuth`** (carga, inicio, renovación
   única, cierre, logout, `clear()` de la cabecera). Comprobación: CA-08–CA-14,
   CA-21 en JVM.
5. **Integración en `AppContainer` y cabecera** (dos clientes, `HeaderRemote`
   sin Bearer propio, eliminar `StaticSessionProvider` y `DEV_*`).
   Comprobación: regresión de la 002 en verde y `assembleDebug` sin `DEV_*` (CA-15).
6. **Raíz y ViewModels** (`RootViewModel`, `AuthViewModel`). Comprobación:
   CA-01, CA-02, CA-03, CA-16 en JVM.
7. **UI de acceso** (pestañas, formularios, mensajes, accesibilidad).
   Comprobación: tests UI de CA-18–CA-20, CA-22, CA-23.
8. **Validación completa**: `assembleDebug`, `testDebugUnitTest`, `lintDebug`,
   `connectedDebugAndroidTest`, pruebas manuales contra el backend y
   actualización de `CLAUDE.md` y `docs/ROADMAP.md`.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - **Proceso terminado durante una renovación:** si el backend rota el token
    pero la app muere antes de guardarlo, la siguiente renovación reutiliza el
    anterior y el backend cierra todas las sesiones (mensaje de seguridad). Se
    reduce guardando el par nuevo antes de usarlo; el caso residual se acepta y
    queda documentado.
  - **Clave del Keystore perdida o inválida** (restauración, cambios del
    sistema): el descifrado falla → se trata como sin sesión y se pide login. No
    hay pérdida de datos de usuario (todo vive en el backend).
  - **API del hook `Send` de Ktor 3.3.3:** se verifica en la primera tarea de la
    etapa 4; si no permitiera reintentar, se usaría `HttpSend.intercept` del
    mismo `ktor-client-core`.
  - **Estado en memoria de una sesión anterior:** el `MainViewModel` previo queda
    en el `ViewModelStore` de la Activity hasta que esta termina; no se muestra
    porque la clave cambia por generación y la memoria del repositorio se vacía
    en `clear()`.
  - **Cambio de firma en la cabecera** (`SessionProvider`, cliente HTTP): se
    actualizan sus tests en la misma etapa para no perder la regresión.
- **Decisiones pendientes:** Ninguna. Propuestas incluidas en este plan para tu
  revisión:
  1. Pantalla en blanco del tema (sin splash nuevo) mientras se lee la sesión al
     arrancar.
  2. Pestañas con `SingleChoiceSegmentedButtonRow` de Material 3 (estilo de la
     referencia) y logo reutilizando `ic_launcher_foreground`.
  3. El mensaje de sesión terminada desaparece al enviar o cambiar de pestaña.
  4. Logout con error distinto de red o 401: sesión intacta y resultado
     `Unknown`; su mensaje se define en la spec de perfil.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
