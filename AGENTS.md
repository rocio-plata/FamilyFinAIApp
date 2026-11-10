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

Los builds debug dependen de la tarea `checkDevProperties` (enganchada a `preDebugBuild`),
que falla si `DEV_TARGET` en `local.properties` (no versionado) no es `emulator`
(`http://10.0.2.2:3000`, el valor por defecto) o `device` (`http://localhost:3000`, requiere
`adb reverse tcp:3000 tcp:3000`). `BACKEND_BASE_URL` lo sobrescribe; en release queda vacío. La
app ya no lleva credenciales de desarrollo: se entra por la pantalla de acceso. Solo
`AuthWiringTest` (instrumentado) usa una cuenta real y la recibe por los argumentos `email` y
`password` del runner; nunca escribir esos valores en código, logs ni documentación.

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
  un contador de época (ver `SessionEndDuringHeaderRefreshTest`). `Session`, los DTOs de auth y
  `RefreshOutcome` sobrescriben `toString()` para no filtrar tokens.
- **Datos**: los data sources remotos devuelven `RemoteResult.Success/Failure` con fallos de
  dominio tipados (`HeaderFailure`) y nunca lanzan por red/servidor; sí relanzan
  `CancellationException`. Los repositorios exponen un `Flow` de datos y un `suspend refresh()`
  que devuelve un resultado por recurso (cada recurso se refresca de forma independiente).
- **Caché offline** (`HeaderLocalDataSource`): un JSON por recurso en DataStore Preferences con
  `schemaVersion`, `userId` y `savedAt`. El repositorio descarta caché de otro usuario o de
  ≥7 días y marca `isStale` a partir de 24 h. Versión desconocida o JSON ilegible = sin datos.
- **Presentación**: `MainViewModel` combina el flujo del repositorio con estado local
  (familia activa solo en memoria, selector abierto, refresco en curso) en un
  `StateFlow<MainUiState>`; `FamiliesState` es un sealed interface
  (Loading/NoData/Content/Error). Se refresca al iniciar, en `ON_START` y al recuperar
  conectividad. `MainScreen` tiene una sobrecarga con estado (ViewModel) y otra stateless.
  `AuthViewModel` (`ui/auth`) gestiona login y registro con las reglas de `AuthValidation` y los
  mensajes de `AuthTexts`; `AuthContent` es la parte stateless.
- **Navegación**: aún no hay librería. `AppRoot`/`RootViewModel` eligen la pantalla según
  `SessionState`: nada en `Loading`, `AuthScreen` en `SignedOut` y `MainScreen` en `SignedIn`; la
  generación de la sesión reinicia el estado de los ViewModels al cambiar de usuario. Las pestañas
  inferiores son el enum `MainDestination` (su orden es el de la barra).
- **Tests**: unitarios JVM con `kotlinx-coroutines-test` y `ktor-client-mock`; los de UI
  (`androidTest`) usan `TestMainHost`, que reproduce lo mínimo del ViewModel sobre la
  sobrecarga stateless de `MainScreen`, sin red ni caché; los de acceso (`AuthScreenTest`,
  `AppRootTest`) usan `AuthContent` y `AppRootContent` con repositorios falsos.

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
./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.SomeTest"
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
