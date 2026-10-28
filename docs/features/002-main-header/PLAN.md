# PLAN: Cabecera principal operativa (selector de familia, notificaciones y perfil)

**SPEC de referencia:** `docs/features/002-main-header/SPEC.md`
**Versión de la spec revisada:** SPEC con RF-01..RF-09 y CA-01..CA-20, revisada en esta sesión; la persona confirmó considerarla aprobada y su estado pasa a Aprobada (sin commit propio: cambios sin confirmar en el árbol de trabajo). Su único PENDIENTE (tiempo máximo de espera) se propone en este plan.
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

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| `MainActivity` | `app/src/main/java/com/rocioplata/familyfinaiapp/MainActivity.kt` | Edge-to-edge y `setContent { MainScreen() }`. Se usará para obtener el ViewModel. |
| `MainScreen` / `MainScreenContent` | `.../ui/main/MainScreen.kt` | `selected` en `remember`; `Scaffold` con `MainHeader`, barra inferior, FAB y cuerpo. Pasará a recibir estado de cabecera y familia activa. |
| `MainHeader` (+ `FamilySelector` privado) | `.../ui/main/MainHeader.kt` | Cabecera estática (textos de ejemplo, sin acciones), áreas táctiles de 48 dp y etiquetas accesibles. Punto de partida obligatorio de la spec. |
| `TabPlaceholder`, `HomeContent`, `MainDestination` | `.../ui/main/` | Cuerpo de Movimientos/Reportes/Presupuestos/Asistente (solo título), cuerpo vacío de Inicio y enum de pestañas. |
| Tema | `.../ui/theme/Color.kt`, `Type.kt`, `Theme.kt` | Colores y tipografías; no se añaden hex en pantallas. |
| Strings | `app/src/main/res/values/strings.xml` | Textos `header_*`, `tab_*`; `header_family_name` y `header_family_role` son de ejemplo. |
| Dependencias | `gradle/libs.versions.toml`, `app/build.gradle.kts` | **Solo** Compose BOM, Material 3, activity-compose, core-ktx, lifecycle-runtime-ktx 2.6.1 y JUnit/Espresso. **No hay** red, serialización, persistencia, ViewModel, corrutinas explícitas ni sesión. `minSdk 34`, JVM 11. |
| Manifest y backup | `app/src/main/AndroidManifest.xml`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | Sin permisos ni clase `Application`; `allowBackup=true`; orientación vertical; reglas de backup sin personalizar. |
| Tests | `app/src/androidTest/.../ui/main/MainScreenTest.kt`, `MainScreenBehaviorTest.kt`; `app/src/test/.../ExampleUnitTest.kt` | UI con `createComposeRule`; verifican textos estáticos "Familia Plata, Propietario", cabecera inerte y pestañas. Requieren actualización (ver regresión). |
| Contrato backend | `docs/backend/api-contract.md` (`GET /me/families`, `GET /me/invitations`, `GET /me/profile`; errores §1.1) | Respuestas: `[{familyId,name,role,defaultCurrency,memberCount}]`, lista de invitaciones (el contador es su tamaño) y `{userId,email,displayName,createdAt}`. Auth `Bearer`; JSON `camelCase`. |

**Convenciones y patrón de referencia:** Paquete `com.rocioplata.familyfinaiapp` con `ui/main`, `ui/theme`. Composables con entrada con estado y contenido sin estado (`MainScreen` → `MainScreenContent`), `@Preview`, `testTag` y `contentDescription` desde `strings.xml`. Catálogo de versiones en `gradle/libs.versions.toml`. La spec 001 (`docs/features/001-scaffold/PLAN.md`) es el patrón de estructura.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de datos y eventos hasta la interfaz. Usa un diagrama si aporta claridad. -->

**Decisiones confirmadas por la persona:** cliente HTTP **Ktor Client + kotlinx.serialization**; persistencia local con **DataStore (Preferences)** guardando JSON; la sesión se aísla tras una interfaz `SessionProvider` (userId + token) con una implementación temporal hasta la spec de sesión.

**Enfoque:** módulo único (`:app`), capas ligeras y proporcionales (no se modulariza):

```
MainActivity ──► MainViewModel (StateFlow<MainUiState>)
                    │  usa
                    ▼
            HeaderRepository (domain/header, interfaz)
              ▲                        ▲
   HeaderRepositoryImpl (data/header)  SessionProvider (interfaz)
     ├─ HeaderRemoteDataSource (Ktor) ──► GET /me/families, /me/invitations, /me/profile
     └─ HeaderLocalDataSource (DataStore, JSON por recurso + userId + savedAt)
```

Recorrido: al iniciar (y al volver a primer plano o recuperar red) el ViewModel pide refrescar al repositorio. El repositorio compara el `userId` de sesión: si la caché es de otro usuario o tiene ≥7 días, la elimina; si es válida, la emite de inmediato (marcada como desactualizada si tiene >24 h) y consulta en paralelo los 3 endpoints; cada éxito sustituye su recurso y su `savedAt`. El ViewModel combina todo en `MainUiState` y la UI es sin estado. La familia activa es `activeFamilyId` en el ViewModel (sobrevive a segundo plano y recreación, no al proceso: RF-03); la pestaña activa recibe `activeFamilyName` (RF-08).

Reutiliza `MainHeader`, `FamilySelector`, `TabPlaceholder`, `HomeContent`, `MainDestination`. Sin navegación nueva.

## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

| Módulo | Existe / nuevo | Responsabilidad y cambios | Dependencias afectadas |
| --- | --- | --- | --- |
| No aplica: el proyecto tiene un único módulo `:app` y modularizar queda fuera de alcance | Existente | Se añaden paquetes `domain/header`, `data/header`, `session` y se amplía `ui/main`. | Nuevas librerías (ver Dependencias). |

Dirección de dependencias dentro de `:app`: `ui` → `domain` ← `data`; `domain` sin Android, Compose ni DTOs (AGENTS.md).

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `domain/header/Family.kt`, `FamilyRole.kt` (`OWNER`, `MEMBER`) | Crear | Modelos de dominio: id, nombre, rol. | RF-01, RF-02 |
| `domain/header/HeaderData.kt` | Crear | Familias, número de invitaciones, nombre del usuario y `savedAt` por recurso. | RF-01, RF-04, RF-05 |
| `domain/header/HeaderRepository.kt` | Crear | Contrato: flujo de estado de cabecera y `refresh()`. | RF-01, RF-04, RF-05, RF-07 |
| `session/SessionProvider.kt` | Crear | Interfaz `userId` + token. Sin login ni refresco (fuera de alcance). | RF-07 (CA-18) |
| `session/StaticSessionProvider.kt` | Crear (temporal) | Solo debug: hace `POST /auth/login` con `BuildConfig.DEV_EMAIL/DEV_PASSWORD` (de `local.properties`, no versionado) y obtiene `userId` y token; sin refresco. Se sustituye en la spec de sesión. | Restricción: sesión iniciada |
| `data/header/HeaderRemoteDataSource.kt` + DTOs `@Serializable` | Crear | 3 GET con Ktor; timeouts; mapeo DTO→dominio; lectura del código `FAMILY.INSUFFICIENT_ROLE`. | RF-01, RF-04, RF-05 |
| `data/header/HeaderLocalDataSource.kt` (DataStore) | Crear | Guarda y lee JSON por recurso con `userId` y `savedAt`; borrado. | RF-07 (CA-10, CA-16, CA-17, CA-18) |
| `data/header/HeaderRepositoryImpl.kt` | Crear | Reglas: otro usuario → borrar; ≥7 días → borrar; >24 h → desactualizado; refresco en paralelo e independiente por recurso. Reloj inyectable. | RF-07 |
| `data/network/ConnectivityObserver.kt` | Crear | `NetworkCallback` → `Flow<Boolean>`; dispara refresco al recuperar red. | RF-07 (CA-09, CA-10) |
| `FamilyFinApp.kt` (`Application`) y `AppContainer` | Crear | Instancia única de `HttpClient`, DataStore y repositorio (sin framework DI). | Soporte |
| `ui/main/MainViewModel.kt`, `MainUiState.kt` | Crear | `StateFlow`, `activeFamilyId`, `selectorOpen` y acciones `onSelectorClick/onFamilySelected/onDismiss/onRetry/onInsufficientRole`. | RF-01..RF-03, RF-07, RF-08 |
| `ui/main/MainScreen.kt` | Modificar | Estado con `collectAsStateWithLifecycle`, refresco en `ON_START`, familia activa al cuerpo. Cambia la firma de `MainScreenContent`. | RF-03, RF-07, RF-08 |
| `ui/main/MainHeader.kt` | Modificar | Datos reales; sin selector con 1 familia; estados carga/error/reintento/sin datos; contador con semántica; inicial o icono genérico; icono de cambios locales. | RF-01, RF-02, RF-04..RF-07, RF-09 |
| `ui/main/FamilySelectorSheet.kt` | Crear | `ModalBottomSheet` con lista desplazable (nombre, rol, activa marcada); Atrás la cierra. | RF-02 |
| `ui/main/StaleDataBanner.kt` | Crear | Aviso "datos podrían estar desactualizados" (>24 h). | RF-07 (CA-16) |
| `ui/main/TabPlaceholder.kt`, `HomeContent.kt` | Modificar | Muestran "Título · Familia"; `key(familyId)` para recargar. | RF-08 |
| `res/values/strings.xml` | Modificar | Textos nuevos y plurales; se retiran los de ejemplo. | Todos |
| `AndroidManifest.xml` | Modificar | `INTERNET`, `ACCESS_NETWORK_STATE`, `android:name=".FamilyFinApp"`. | RF-07 |
| `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Modificar | Excluir el DataStore de la caché (datos personales) de las copias de seguridad. | Privacidad |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modificar | Dependencias, plugin de serialización, `buildConfig`. | Soporte |
| `MainScreenTest`, `MainScreenBehaviorTest` | Modificar | Se adaptan a la nueva firma y a datos de prueba. | Regresión |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Modelos y contratos de entrada y salida:** DTOs privados a `data/header` con `ignoreUnknownKeys`; un `role` desconocido se trata como error de datos (el contrato solo admite OWNER/MEMBER). Dominio: `Family(id, name, role)`; invitaciones = `size` de la lista (RF-04); nombre = `displayName` (RF-05). Error `{error,message,details}`: se interpreta `error`; `message` no se muestra ni se registra.
- **Identificadores, relaciones y restricciones:** Familias por `familyId` (UUID), nunca por nombre. La favorita es la primera de la lista (RF-01). Se asume nombre único, no vacío y sin emojis (validado en otra spec).
- **Origen de los datos mostrados y transformaciones:** Familias, invitaciones y perfil desde el backend; rol → "Propietario" / "Integrante" (strings); inicial = primer *code point* de `displayName` en mayúscula (RF-05); sin nombre → icono de persona genérico.
- **Persistencia, consultas y actualizaciones:** DataStore (`header_cache`) con una clave JSON por recurso y `userId` + `savedAt` (epoch ms UTC). Solo para ver datos sin conexión. Borrado a los 7 días o al detectar otro `userId`; no se borra al cerrar sesión. Con >24 h se marca desactualizado; el aviso desaparece cuando el refresco termina bien. Sin datos personales en logs.
- **Convivencia entre datos locales y remotos:** El backend manda; la caché solo se muestra si es del usuario actual y <7 días. Cada recurso se refresca y cae a su caché de forma independiente (CA-11). Sin cambios locales de usuario en esta spec; la sincronización queda fuera de alcance.
- **Compatibilidad y migraciones de datos existentes:** No aplica: no existen datos locales previos. El JSON lleva `schemaVersion`; un valor desconocido se descarta como "sin datos".

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de MOBILE_GUIDELINES.md. -->

- **Gestión del estado de interfaz y navegación:** `StateFlow<MainUiState>` con `FamiliesState` = `Loading` | `NoData` (aviso mientras se consulta tras borrar datos de otro usuario, CA-18) | `Content(families, activeId, stale)` | `Error`; contador, nombre y `hasLocalChanges` (siempre `false` aquí, RF-09) como campos aparte. `collectAsStateWithLifecycle`. Una familia → cabecera sin indicador ni clic (RF-02, CA-19). Sin navegación nueva; Atrás con la lista abierta la cierra (CA-04).
- **Conservación y restauración del estado:** `activeFamilyId` vive en el `ViewModel` (sobrevive a segundo plano y recreación, CA-05) y **no** se guarda en `SavedStateHandle` ni en disco, así tras terminar el proceso vuelve a la favorita (RF-03). Si el id activo deja de existir tras un refresco, vuelve a la favorita. La lista abierta no se conserva (permitido). La pestaña activa sigue en `remember` (comportamiento de la 001 sin cambios).
- **Ejecución, concurrencia y cancelación de operaciones:** `viewModelScope`; refresco con `coroutineScope { async ×3 }`; un único `refreshJob` activo (sin llamadas duplicadas al volver de segundo plano). `CancellationException` se relanza siempre. Refresco al iniciar, en `ON_START` y al recuperar red. Timeouts propuestos (decisión pendiente 1): conexión 10 s, solicitud 15 s.
- **Errores, reintentos y prevención de duplicados:** Sin datos válidos y fallo → `Error` con reintento (CA-09, CA-17); con datos válidos y fallo → se conserva la caché sin error (CA-10). Fallo solo de invitaciones/perfil → sin contador / icono genérico (CA-11). `FAMILY.INSUFFICIENT_ROLE` → `onInsufficientRole()` re-consulta familias sin cambiar la activa (regla de la SPEC; sin llamador en esta spec, se prueba con test unitario). `401/403`: sin tratamiento especial (spec de sesión), error genérico. Selector: bandera `selectorOpen` + `ModalBottomSheet` único; el reintento ignora pulsaciones con una carga en curso (CA-12).
- **Otras consideraciones mobile aplicables y su solución:** Accesibilidad: contador anunciado con plural ("N invitaciones pendientes"), activa con `selected`, icono de cambios locales con etiqueta "Cambios solo en este dispositivo" (CA-13, CA-20); áreas ≥48 dp; nombres largos truncados; textos en `strings.xml`. Colores solo desde `ui/theme`. Privacidad: sin nombres ni tokens en logs, caché excluida de backups. Orientación vertical y ciclo de vida sin cambios. Sin trabajo en segundo plano.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

Todas nuevas; versiones a fijar en `gradle/libs.versions.toml` durante la implementación (compatibles con Kotlin 2.2.10 y AGP 9.3.3):
- **Ktor Client** (`ktor-client-core`, `ktor-client-okhttp`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`) y plugin + `kotlinx-serialization-json`: *confirmado*; necesarias para los 3 GET y sus DTOs.
- **DataStore Preferences**: *confirmado*; caché local sin esquema relacional.
- **lifecycle-viewmodel-compose** y **lifecycle-runtime-compose** (`collectAsStateWithLifecycle`), alineadas con lifecycle 2.6.1 o superior: AGENTS.md pide `StateFlow` + `collectAsStateWithLifecycle`.
- Tests: **kotlinx-coroutines-test**, **ktor-client-mock**.
- Versiones fijadas por compatibilidad con Kotlin 2.2.10 y `compileSdk` 36: Ktor 3.3.3, kotlinx-serialization 1.9.0, kotlinx-coroutines 1.10.2 (ver evidencia de T-01).
- Permisos: `INTERNET` y `ACCESS_NETWORK_STATE` (normales, sin diálogo).
- `buildFeatures { buildConfig = true }` y lectura de `local.properties` (no versionado) hacia `BuildConfig` solo en `debug`: `BACKEND_BASE_URL=http://10.0.2.2:3000` (emulador), `DEV_EMAIL` y `DEV_PASSWORD`; en `release` quedan vacías. Si faltan en debug, el build falla con un mensaje claro. Sin secretos en el repositorio.
- `DEV_TARGET` (`emulator` por defecto, o `device`) en `local.properties` elige la URL: `emulator` → `http://10.0.2.2:3000`; `device` (móvil físico) → `http://localhost:3000` con `adb reverse tcp:3000 tcp:3000`. `BACKEND_BASE_URL` explícito, si existe, lo anula.
- Tráfico HTTP en claro **solo en debug** y solo hacia `10.0.2.2` y `localhost`, mediante `src/debug/res/xml/network_security_config.xml` y manifest de debug (confirmado); release no permite cleartext.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, UI o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Unit propuesto `MainViewModelTest` (activa = primera) + UI propuesto | JVM; repositorio falso con 2 familias | Test en verde |
| CA-02 | UI propuesto (hoja con nombre, rol y activa `selected`) | Emulador; estado de prueba | Test en verde |
| CA-03 | UI propuesto: elegir cierra la hoja y cambia la cabecera | Emulador | Test en verde |
| CA-04 | UI propuesto (`onBackPressedDispatcher`) en `MainScreenBehaviorTest` | Emulador | Test en verde |
| CA-05 | Unit (ViewModel conserva activa; ViewModel nuevo = favorita) + manual (matar proceso) | JVM; emulador con 2 familias | Test + nota manual |
| CA-06 | UI propuesto (N>0 y 0; clic sin efecto) + semántica plural | Emulador | Test en verde |
| CA-07 | Unit (inicial «rocío» → «R») | JVM | Test en verde |
| CA-08 | Adaptar `headerAndFabAreInert` a todas las pestañas | Emulador | Test en verde |
| CA-09 | Unit del repositorio (sin red ni caché → Error; reintento → Content) + manual en instalación limpia | JVM con `ktor-client-mock`; emulador sin red | Test + nota manual |
| CA-10 | Unit/integración con DataStore temporal (caché válida + fallo de red → datos, sin error) + manual | JVM; emulador | Test + nota manual |
| CA-11 | Unit (invitaciones fallan → sin contador, selector operativo) | JVM | Test en verde |
| CA-12 | UI propuesto (clics rápidos, una sola hoja) | Emulador | Test en verde |
| CA-13 | UI con `assertContentDescriptionEquals` + manual con TalkBack y fuente máxima | Emulador | Test + nota manual |
| CA-14 | UI propuesto en cada pestaña con familia nueva → "Título · Familia" | Emulador | Test en verde |
| CA-15 | UI propuesto (5 pestañas) + manual reabrir app | Emulador | Test + nota manual |
| CA-16 | Unit con reloj falso (1 h sin alerta; 30 h con alerta; tras éxito desaparece) | JVM | Test en verde |
| CA-17 | Unit con reloj falso (8 días → borrada, Error con reintento) | JVM | Test en verde |
| CA-18 | Unit (caché de otro `userId` → borrada, `NoData`, luego propios / Error sin red) | JVM | Test en verde |
| CA-19 | UI propuesto (una familia: sin indicador ni lista) | Emulador | Test en verde |
| CA-20 | UI propuesto con `hasLocalChanges` false/true (a la izquierda de notificaciones, con etiqueta) | Emulador; parámetro de prueba | Test en verde |

**Comprobaciones de regresión:** Adaptar `MainScreenTest` y `MainScreenBehaviorTest`: cabecera visible en las 5 pestañas, áreas táctiles y etiquetas, FAB solo en Inicio, Atrás desde otra pestaña vuelve a Inicio, pulsaciones repetidas sin duplicar, barra inferior sin cambios.

**Comandos verificados para compilar y ejecutar tests:** (de AGENTS.md; no ejecutados al planificar) `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug` y `./gradlew :app:connectedDebugAndroidTest` con dispositivo o emulador.

**Pruebas en dispositivo, emulador o simulador:** Emulador API 34+ para los tests de Compose y las pruebas manuales de segundo plano, proceso terminado, modo avión, TalkBack y fuente máxima. No hace falta hardware real.

**Limitaciones del entorno:** Existe `~/Android/Sdk` con `emulator`, pero no se verificó un AVD. La disponibilidad real de los endpoints no está verificada (SPEC, restricciones): hasta entonces se valida con `ktor-client-mock`. No hay sesión real: las pruebas manuales usan `StaticSessionProvider` (login de desarrollo contra el backend en `localhost:3000`, verificado con `GET /health`).

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. **Dependencias y configuración** (catálogo, plugin, permisos, `buildConfig`, `Application`, reglas de backup). Comprobación: `:app:assembleDebug`.
2. **Dominio y sesión** (modelos, `HeaderRepository`, `SessionProvider`). Comprobación: compila y test de la inicial (CA-07).
3. **Datos locales** (DataStore; reglas de 7 días, 24 h y otro usuario). Comprobación: CA-10, CA-16, CA-17, CA-18.
4. **Datos remotos** (Ktor, DTOs, errores, timeouts). Comprobación: tests con `ktor-client-mock` (CA-09, CA-11).
5. **Repositorio y ViewModel** (refresco, activa, favorita, rol). Comprobación: CA-01, CA-05, CA-12 a nivel unitario.
6. **UI de cabecera** (datos reales, selector, estados, contador, avatar, icono local, aviso de antigüedad). Comprobación: tests UI de CA-02..CA-04, CA-06, CA-08, CA-13, CA-19, CA-20.
7. **Pestañas con familia activa** (RF-08). Comprobación: CA-14, CA-15.
8. **Regresión y validación completa**: adaptar tests de la 001, `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `connectedDebugAndroidTest` y pasos manuales.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - La sesión real no existe: se limita a `SessionProvider` y su implementación temporal; el cambio posterior no debe tocar la cabecera.
  - Endpoints sin verificar en el backend real: validación con mocks y revisión manual cuando estén disponibles.
  - Versiones de lifecycle (2.6.1) y de las librerías nuevas: fijar versiones compatibles en el paso 1 y comprobarlo con `assembleDebug`.
  - `MainScreenContent` cambia de firma: se actualizan los tests en el mismo paso para no perder regresión.
  - Datos personales en caché: excluidos de backup, sin logs y con borrado por caducidad o cambio de usuario.
- **Decisiones pendientes:** Ninguna. Las propuestas siguientes quedaron aprobadas junto con el plan (2026-10-21):
  1. **Tiempo máximo de espera** (pendiente de la SPEC): propongo conexión 10 s y solicitud 15 s.
  2. **Selector**: propongo `ModalBottomSheet`, acorde a `docs/ui/snapshots/003-selector-familia.png`, mostrando solo nombre, rol y activa (sin los elementos fuera de alcance).
  3. **Dónde y cómo se muestran** el aviso de datos >24 h, el estado «sin datos» (CA-18) y la carga/error: propongo un aviso fino bajo la cabecera y texto dentro del propio selector.
  4. **Refresco al recuperar la red** mediante `ConnectivityObserver` (añade `ACCESS_NETWORK_STATE`), para cumplir «al recuperar la conexión se actualizan los datos».
  5. **Propiedades locales** (resuelto): `BACKEND_BASE_URL=http://10.0.2.2:3000`, `DEV_EMAIL`, `DEV_PASSWORD` en `local.properties`; la persona escribe el usuario de prueba (con varias familias para CA-01..CA-05) localmente, nunca en el repositorio.
  6. Si la familia activa desaparece tras refrescar, vuelve a la favorita (no definido en la SPEC; sin impacto en criterios).

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
