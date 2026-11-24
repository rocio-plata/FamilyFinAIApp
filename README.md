# FamilyFin AI · App móvil

> **Smarter family finances.**

App Android para la gestión colaborativa de las finanzas familiares. Varios miembros de una familia usan su propio teléfono y comparten una misma economía. La IA ayuda a registrar y analizar, pero **propone: la persona revisa y confirma**.

Este repositorio contiene el cliente móvil. Se construye con **Spec-Driven Development (SDD)**: primero se especifica, luego se planifica y solo después se implementa, contra criterios de aceptación verificables.

<p align="center">
<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=Kotlin&logoColor=white" alt="Kotlin">
<img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
<img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android">
<img src="https://img.shields.io/badge/SDD-Spec%20Driven-%2300E58E?style=for-the-badge" alt="Spec Driven Development">
</p>

> Proyecto de fin de máster en desarrollo con IA.
> Backend: [`familyFinAI-backend`](https://github.com/rocio-plata/familyFinAI-backend) (TypeScript · Fastify · PostgreSQL).

----------

## 🎯 Qué es FamilyFin AI

- **Economía compartida:** cada miembro tiene su usuario, pero los movimientos pertenecen a la familia.
- **Varias familias por usuario:** además de la familia personal que se crea al registrarse, una persona puede pertenecer a otras. Siempre trabaja en la *familia activa*.
- **Gastos e ingresos separados:** el tipo pertenece a la categoría, y cada movimiento lo hereda.
- **Categorías, tags y medios de pago** definidos por la familia.
- **Dashboard, movimientos, reportes con drill-down y presupuestos mensuales.**
- **IA con control humano:**
  - registro por texto libre ("gasté $25.000 en bencina ayer");
  - registro por foto de recibo (un gasto por recibo);
  - sugerencia de categoría y tag;
  - consultas en lenguaje natural;
  - insights y recomendaciones.
- **Seguridad:** la app nunca tiene credenciales de base de datos ni API keys de IA. Todo pasa por el backend, que decide a qué familia accede cada usuario.

La moneda es la de cada familia (**CLP** por defecto). El dinero se maneja siempre como un entero en la unidad menor de la moneda, nunca como `Double`.

> Hoy la app ya permite registrarse e iniciar sesión, cambiar de familia, gestionar categorías, tags y medios de pago, llevar los movimientos de cada mes (alta, listado con resumen y filtro, detalle, edición y eliminación), ver el dashboard del mes y gestionar las familias (crear, reordenar, miembros y roles, invitaciones enviadas y recibidas, moneda y salir; el backend ya permite salir también a un Integrante, BG-01, aún sin verificar en ejecución). El resto (presupuestos, reportes, IA, etc.) está por construir: ver la sección *Features y estado*.

----------

## 🧭 Metodología: Spec-Driven Development

El problema de programar con IA no es que escriba mal el código, sino que **rellena los huecos que no definiste**. SDD pone un proceso delante:

| Etapa | Documento | Qué resuelve |
| :--: | --- | --- |
| **1️⃣** | **SPEC.md** | Qué se construye, qué queda fuera, reglas de negocio, casos de error y **criterios de aceptación verificables**. Sin código. |
| **2️⃣** | **PLAN.md** | Cómo se construye: capas, contratos con la API, estado, navegación, errores y estrategia de validación. Sin implementar. |
| **3️⃣** | **TASKS.md** | Tareas pequeñas y ordenadas, con dependencias, ligadas a los criterios que resuelven. Cada una registra su **evidencia**. |
| **4️⃣** | Implementación | Solo lo que está en el alcance, demostrando cada criterio con evidencia real. |

> [!IMPORTANT]
> Que un documento esté **completo** no significa que esté **aprobado**. Ninguna etapa avanza sola: cada una se revisa y se aprueba explícitamente. "Debería funcionar" no es evidencia.

### Organización de los documentos

```
AGENTS.md                           # instrucciones del proyecto para el agente (única fuente)
CLAUDE.md                           # solo enlaza a AGENTS.md
docs/
├── ROADMAP.md                      # orden de implementación y estado de cada feature
├── SPEC_TEMPLATE.md                # plantillas compartidas (no se editan por feature)
├── PLAN_TEMPLATE.md
├── MOBILE_GUIDELINES.md            # checklist específico de móvil
├── GENERIC_RULES.md                # reglas de trabajo del agente (y su versión en español)
├── DESIGN.md                       # sistema visual: colores, tipografía, componentes
├── backend/
│   ├── api-contract.md             # contrato con el backend: endpoints, errores, brechas
│   └── openapi.yaml
├── ui/
│   ├── prompts-stitch.md           # prompts de diseño (Google Stitch) por pantalla
│   └── snapshots/                  # referencias visuales de las pantallas
└── features/
    └── NNN-<nombre-de-la-feature>/
        ├── SPEC.md                 # copia de SPEC_TEMPLATE.md, completada
        ├── PLAN.md                 # tras aprobar la spec
        ├── TASKS.md                # derivado del plan aprobado, con la evidencia de cada tarea
        └── BACKEND_GAPS.md         # brechas del backend detectadas (si las hay)
scripts/
└── dev-reverse.sh                  # expone el backend local en un dispositivo o emulador (adb reverse)
```

`TASKS.md` no tiene plantilla a propósito: **se deriva del plan aprobado**, porque las tareas dependen de las decisiones técnicas del plan.

----------

## 🧱 Stack y arquitectura

### Decidido y en uso

- **Kotlin 2.2** + **Jetpack Compose** (BOM 2026.02.01, Material Design 3). `minSdk 34`, `targetSdk` y `compileSdk 36`.
- **MVVM** con flujo de datos unidireccional: cada pantalla expone un `StateFlow<UiState>` y su contenido es una función *stateless* que recibe estado y callbacks.
- **Ktor Client 3** (motor OkHttp) con **kotlinx.serialization** como cliente HTTP.
- **Clean Architecture** por capas: `ui → domain ← data`. El dominio no conoce Android, Compose, Ktor ni los DTO.
- **Corrutinas** estructuradas y `viewModelScope`; la cancelación nunca se oculta.
- **Inyección de dependencias manual:** `FamilyFinApp` crea un `AppContainer` con el cliente HTTP, el almacenamiento, el observador de conectividad, la sesión y los repositorios. No hay librería de DI.
- **Sin librería de navegación:** las pestañas y las pantallas a pantalla completa (gestiones, formulario y detalle de un movimiento) son estado en memoria de `MainViewModel`. Se vuelve a Inicio si el proceso muere.
- **Sesión:** `SessionManager` es la única fuente de verdad (`SignedOut`, `Loading` o `SignedIn`). Guarda la sesión cifrada con AES-GCM y clave del Keystore (excluida de las copias de seguridad) y hace una sola renovación del token ante varios `401`.
- **Caché offline:** DataStore Preferences, un JSON por recurso con `schemaVersion`, usuario y fecha. Se descarta a los 7 días o si es de otro usuario y se avisa de "datos desactualizados" a partir de 24 h. Todas las cachés se vacían al terminar la sesión y están excluidas de las copias de seguridad.
- **Dinero:** `Money` es un entero en la unidad menor con su moneda; el formato de salida usa la configuración regional del dispositivo.
- Principio de diseño: **minimizar dependencias externas** para controlar mejor la seguridad.

### Por decidir (se resuelve en el PLAN de la feature que lo necesite)

| Tema | Notas |
| --- | --- |
| Imágenes y cámara | Captura de recibos, permisos y compresión previa al envío |
| Gráficos | Donut y barras para Dashboard y Reportes |
| Librería de navegación | Solo si la navegación en memoria deja de bastar; no se introducen dos stacks sin una decisión documentada |
| Moneda configurable | Hoy cada familia usa la moneda que devuelve el backend; elegirla vendrá con los ajustes |

### Estructura actual

Por capa y, dentro de cada una, por feature:

```
app/src/main/java/com/rocioplata/familyfinaiapp/
├── FamilyFinApp.kt, AppContainer.kt, MainActivity.kt
├── session/             # SessionManager: sesión, renovación, cierre
├── domain/              # modelos, contratos de repositorio y reglas, sin Android
│   ├── auth/  header/  categories/  paymentmethods/
│   ├── members/  money/  movements/
├── data/                # remoto (Ktor), local (DataStore) y repositorios
│   ├── network/         # clientes HTTP y plugin SessionAuth (Bearer + renovación)
│   ├── cache/           # CachePolicy (7 días / 24 h)
│   ├── session/  auth/  header/  categories/  paymentmethods/
│   └── members/  movements/
└── ui/
    ├── theme/           # colores y tipografía (sin hexadecimales en las pantallas)
    ├── root/            # AppRoot: login o pantalla principal según la sesión
    └── auth/  main/  categories/  paymentmethods/  movements/
```

Los tests JVM están en `app/src/test/…` (corrutinas, `ktor-client-mock`) y los de interfaz en `app/src/androidTest/…`, en el mismo paquete que lo que prueban.

### Reglas que afectan a todas las features

1. **Todo se resuelve en la familia activa**; el selector de familia está en la barra superior y cambia el contexto de toda la app.
2. **Autenticación:** access token de corta duración y refresh token con rotación. Si el refresh falla o se detecta reuso, la sesión se cierra y se vuelve al login.
3. **Los roles (Owner / Member) se respetan en la UI**, pero la autorización real siempre la hace el backend.
4. **Lo que propone la IA nunca se guarda solo:** se muestra editable con badge "Sugerido por IA" y botón "Confirmar".
5. **Categorías, tags y medios de pago archivados** siguen visibles en lo que ya los usa, marcados como "Archivada" o "Archivado". La app no permite reactivarlos todavía (el backend ya lo permite para categorías y tags desde el 2026-11-24; falta especificarlo).
6. Los **íconos de categoría** llegan de la API como una clave de texto. Hay un catálogo de 15 y la app tolera una categoría sin ícono o con uno desconocido (ícono genérico).
7. Los errores del backend llegan con un `code` de dominio: la app los traduce a mensajes comprensibles, sin mostrar errores técnicos. Hoy el backend emite `FINANCIAL_TRACKING.*` aunque el contrato documente `FINANCIAL.*`: la app acepta ambos prefijos.
8. **Los cambios requieren conexión:** sin red se avisa y se conserva lo escrito; no hay cola offline.

----------

## 🗺️ Features y estado

Cada feature tiene su carpeta en `docs/features/` y recorre SPEC → PLAN → TASKS. El orden vigente, las brechas del backend y lo que queda sin verificar de cada una están en [`docs/ROADMAP.md`](docs/ROADMAP.md).

| # | Feature | Carpeta | Estado | Contexto del backend |
| :--: | --- | --- | :--: | --- |
| 1 | Andamiaje de la app | `001-scaffold` | ✅ | — |
| 2 | Cabecera principal: familia activa, invitaciones, caché | `002-main-header` | ✅ | Family & Access |
| 3 | Autenticación y sesión: registro, login, renovación, cierre | `003-auth` | ✅ | Identity |
| 4 | Identidad visual: icono y nombre de la app | `004-brand-identity` | ✅ | — |
| 5 | Categorías y tags | `005-categories` | ✅ | Financial Tracking |
| 6 | Medios de pago | `006-payment-methods` | ✅ | Financial Tracking |
| 7 | Movimientos: alta, listado, detalle, edición y eliminación | `007-financial-items` | ✅ | Financial Tracking |
| 8 | Dashboard | `008-dashboard` | ✅ | Financial Tracking |
| 9 | Gestión de familias: miembros, roles, invitaciones | `009-family-management` | 🚧 | Family & Access |
| 10 | Categorías reversibles: reactivar categorías y tags | `010-categories-reversible` | 📝 | Financial Tracking |
| 11 | Presupuestos | `budgets` | ⏳ | Budgeting |
| 12 | Reportes | `reports` | ⏳ | Reporting |
| 13 | Registro por texto con IA | `ai-text-entry` | ⏳ | AI Assistance |
| 14 | Registro por recibo con IA | `ai-receipt-entry` | ⏳ | AI Assistance |
| 15 | Asistente: chat e insights | `assistant` | ⏳ | AI Assistance |
| 16 | Perfil y ajustes | `profile` | ⏳ | Identity |

El número de cada fila es el de su carpeta en `docs/features/` y el de `docs/ROADMAP.md`. Estados: ✅ hecha · 🚧 en curso · 📝 con spec · ⏳ pendiente.

> [!NOTE]
> El orden depende del avance del backend: una feature de la app se especifica cuando los endpoints que necesita existen o su contrato está acordado. Reportes, Presupuestos y la IA real se abordan al final. La IA empieza con un adaptador *fake* determinista en el backend.

----------

## 🛠 Instalación

### Requisitos

- **Android Studio** (versión estable reciente).
- **JDK 17 o superior** (el Gradle Wrapper usa Gradle 9.5 y el plugin de Android 9.3; el código compila a Java 11).
- Un **emulador o dispositivo con Android 14 (API 34) o superior**.
- El **backend en ejecución** (local con Docker + Postgres, o desplegado).
- Un **agente de IA con acceso al repo** (Claude Code, etc.) si vas a trabajar una feature con SDD.

### Puesta en marcha

1. Clona el repositorio:

    ```bash
    git clone https://github.com/rocio-plata/FamilyFinAIApp.git
    ```

2. Levanta el backend siguiendo su README (Postgres con Docker y `npm run dev`; escucha en el puerto 3000).
3. Configura el destino del backend en `local.properties` (ver *Configuración*).
4. Abre el proyecto en Android Studio, sincroniza Gradle y ejecuta la app.
5. En la pantalla de acceso, crea una cuenta: el backend crea además tu familia personal.

### Configuración

La URL del backend **no se escribe en el código**: sale de `local.properties` (archivo local, no versionado) y llega al build de debug como un campo `BuildConfig`. En release queda vacía: todavía no hay una URL de producción definida.

| Propiedad | Valor | Para qué |
| --- | --- | --- |
| `DEV_TARGET=emulator` (por defecto) | `http://10.0.2.2:3000` | Emulador → backend local |
| `DEV_TARGET=device` | `http://localhost:3000` | Dispositivo físico o emulador con `adb reverse tcp:3000 tcp:3000`; `scripts/dev-reverse.sh` lo aplica a todos los conectados (repítelo tras reiniciarlos) |
| `BACKEND_BASE_URL=<url>` | la que indiques | Sobrescribe a `DEV_TARGET` (por ejemplo, la IP de tu PC) |

Un `DEV_TARGET` distinto de `emulator` y `device` (sin `BACKEND_BASE_URL`) hace fallar el build de debug con un mensaje claro.

> [!WARNING]
> El tráfico sin cifrar (HTTP) solo se permite en builds de debug y solo hacia `10.0.2.2` y `localhost`. Producción exigirá HTTPS. La app no contiene secretos: ni credenciales de base de datos ni API keys de proveedores de IA.

### Comandos

```bash
./gradlew :app:assembleDebug              # compilar
./gradlew :app:testDebugUnitTest          # tests de JVM
./gradlew :app:lintDebug                  # lint
./gradlew :app:connectedDebugAndroidTest  # tests instrumentados (requiere dispositivo o emulador)
```

Para una prueba concreta:

```bash
./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.main.MainViewModelTest"
```

> La suite instrumentada completa tarda decenas de minutos en un emulador. Evita que el equipo se duerma mientras corre (por ejemplo, con `caffeinate -i`), porque los procesos quedan pausados.

### Datos de prueba (solo desarrollo)

No hay usuarios ni contraseñas en el repositorio: crea una cuenta de prueba desde la pantalla de registro de tu backend local. Los movimientos, categorías y medios de pago que crees para probar bórralos al terminar, y no escribas credenciales en código, logs ni documentación.

----------

## 🚦 Cómo trabajar una feature (flujo SDD)

1. **Lee las reglas:** [`AGENTS.md`](AGENTS.md) y [`docs/GENERIC_RULES.md`](docs/GENERIC_RULES.md).
2. **Pide la feature a tu agente** indicando su carpeta en `docs/features/`. El agente lee `AGENTS.md`, `docs/GENERIC_RULES.md`, `docs/MOBILE_GUIDELINES.md` y el contrato de la API (`docs/backend/`) antes de empezar.
3. **Escribe la SPEC contigo, no por ti** (con [`docs/SPEC_TEMPLATE.md`](docs/SPEC_TEMPLATE.md)). El agente revisa el repo, el contrato de la API y el diseño, y pregunta lo que no puede deducir. Pocas preguntas por vez. **Lo no decidido se marca como PENDIENTE.**
4. **Aprueba la SPEC** y solo entonces pasa al `PLAN.md` (con [`docs/PLAN_TEMPLATE.md`](docs/PLAN_TEMPLATE.md)). Aprueba el plan y solo entonces se deriva `TASKS.md`.
5. **Implementa por tareas**, de a una, con tests que se ejecutan de verdad. Las tareas se marcan como hechas solo tras ejecutar su validación.
6. **Exige evidencia:** un test con nombre no es un test ejecutado, y una captura no demuestra persistencia. Cada tarea registra en `TASKS.md` lo que se ejecutó y lo que no se pudo verificar.
7. **Actualiza** `docs/ROADMAP.md` y `AGENTS.md` cuando la feature cambie de estado o de arquitectura.

### Qué debe cubrir cada SPEC móvil

Además del negocio, cada spec revisa el checklist de [`docs/MOBILE_GUIDELINES.md`](docs/MOBILE_GUIDELINES.md):

- ciclo de vida y rotación de pantalla;
- estado sin guardar (formularios a medias);
- conectividad: sin red, red lenta, timeouts y reintentos;
- sesión expirada durante una operación;
- cambio de familia activa mientras hay datos en pantalla;
- permisos (cámara, galería);
- accesibilidad (tamaños de texto, contraste, `contentDescription`);
- estados vacíos, de carga y de error.

----------

## 🎨 Diseño

Las pantallas se diseñan con **Google Stitch** como referencia visual (Material Design 3, tema claro primero). El sistema visual (colores, tipografía y componentes) está en [`docs/DESIGN.md`](docs/DESIGN.md), los prompts en [`docs/ui/prompts-stitch.md`](docs/ui/prompts-stitch.md) y las referencias de cada pantalla en `docs/ui/snapshots/`. Stitch no exporta Compose: las pantallas se traducen a Compose manualmente o con ayuda del agente, respetando el tema definido en `ui/theme` (sin colores hexadecimales en las pantallas).

----------

## 🔐 Seguridad y privacidad

- Autenticación individual con JWT de acceso corto y refresh token rotatorio; la app renueva una sola vez ante varios `401`, porque el backend revoca todas las sesiones si se reutiliza un refresh token.
- La sesión se guarda cifrada con AES-GCM y una clave del Keystore, y queda fuera de las copias de seguridad, igual que todas las cachés de datos.
- Al terminar la sesión se borran la sesión guardada y todas las cachés.
- Nunca se registran en logs tokens, contraseñas ni montos; los objetos con tokens ocultan su contenido en `toString()`.
- La lista de miembros de una familia solo se usa para mostrar nombres y roles: el correo de otros miembros no se lee ni se guarda.
- Aislamiento por familia garantizado por el backend: manipular un `familyId` en una petición no da acceso a datos ajenos.
- Límites de uso de IA aplicados en el backend.

----------

## 📌 Estado del proyecto

| Área | Estado |
| --- | --- |
| Definición del producto y bounded contexts | ✅ |
| Proyecto Android, arquitectura base, sesión y caché offline | ✅ |
| Features 1 a 7 (andamiaje, cabecera, autenticación, identidad visual, categorías, medios de pago y movimientos) | ✅ con SPEC, PLAN, TASKS y evidencia |
| Features 8 a 15 (dashboard, familias, presupuestos, reportes, IA, asistente y perfil) | ⏳ pendientes |
| Pruebas | Unas mil pruebas JVM y unas trescientas instrumentadas. El recuento, lo que queda sin verificar de cada feature (TalkBack, una segunda cuenta en la familia, etc.) y los tests de «atrás del sistema» que a veces fallan en un emulador saturado están en cada `TASKS.md` y en `docs/ROADMAP.md` |
| Backend (Identity, Family & Access, Financial Tracking) | Las features 1 a 7 se probaron contra el backend local; las brechas conocidas están en los `BACKEND_GAPS.md` y en `docs/backend/api-contract.md` |
| Producción | ⏳ sin URL de backend de producción ni licencia definidas |

----------

## 📄 Licencia y autoría

Proyecto académico (fin de máster) desarrollado por **Rocío Plata**.
Licencia: _por definir_.
