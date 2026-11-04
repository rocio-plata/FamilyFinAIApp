# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Las reglas del proyecto (flujo SDD, convenciones, validación, informe final) están en
`AGENTS.md` y se aplican íntegramente; este archivo solo añade contexto que no está allí.

@AGENTS.md

## Comandos

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.main.MainViewModelTest"
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest   # requiere dispositivo/emulador
```

Los builds debug dependen de la tarea `checkDevProperties` (enganchada a `preDebugBuild`),
que falla si `local.properties` (no versionado) no define `DEV_EMAIL` y `DEV_PASSWORD`, o si
`DEV_TARGET` no es `emulator` (`http://10.0.2.2:3000`) o `device` (`http://localhost:3000`,
requiere `adb reverse tcp:3000 tcp:3000`). `BACKEND_BASE_URL` lo sobrescribe. Estos valores
llegan como campos `BuildConfig`; en release quedan vacíos. Nunca escribir sus valores en
código, logs ni documentación.

minSdk 34, target/compileSdk 36, Java 11.

## Arquitectura

Paquetes bajo `app/src/main/java/com/rocioplata/familyfinaiapp/`, organizados por capa y
luego por funcionalidad (`domain/header`, `data/header`, `ui/main`), no según la estructura
`core/` + `features/` propuesta en el README.

- **DI manual**: `FamilyFinApp` (Application) crea un `AppContainer` con el `HttpClient` de
  Ktor (OkHttp, kotlinx.serialization con `ignoreUnknownKeys`, timeouts), DataStore,
  `ConnectivityObserver`, `SessionProvider` y los repositorios. Los ViewModels se crean en la
  entrada de pantalla con `viewModelFactory { initializer { ... } }` leyendo el container
  desde `LocalContext.current.applicationContext`.
- **Sesión**: `SessionProvider.current()` lanza `SessionUnavailableException(SessionFailure)`.
  `StaticSessionProvider` es temporal (login con credenciales de desarrollo, en memoria, sin
  refresh) hasta que exista la spec de autenticación. `Session` y los DTOs de login
  sobrescriben `toString()` para no filtrar tokens.
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
- **Navegación**: aún no hay librería; las pestañas inferiores son el enum `MainDestination`
  (su orden es el de la barra).
- **Tests**: unitarios JVM con `kotlinx-coroutines-test` y `ktor-client-mock`; los de UI
  (`androidTest`) usan `TestMainHost`, que reproduce lo mínimo del ViewModel sobre la
  sobrecarga stateless de `MainScreen`, sin red ni caché.

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
