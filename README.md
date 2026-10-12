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

La moneda por defecto es **CLP**, con apertura a más monedas desde los ajustes en el futuro.

----------

## 🧭 Metodología: Spec-Driven Development

El problema de programar con IA no es que escriba mal el código, sino que **rellena los huecos que no definiste**. SDD pone un proceso delante:

| Etapa | Documento | Qué resuelve |
| :--: | --- | --- |
| **1️⃣** | **SPEC.md** | Qué se construye, qué queda fuera, reglas de negocio, casos de error y **criterios de aceptación verificables**. Sin código. |
| **2️⃣** | **PLAN.md** | Cómo se construye: capas, contratos con la API, estado, navegación, errores y estrategia de validación. Sin implementar. |
| **3️⃣** | **TASKS.md** | Tareas pequeñas y ordenadas, con dependencias, ligadas a los criterios que resuelven. |
| **4️⃣** | Implementación | Solo lo que está en el alcance, demostrando cada criterio con evidencia real. |

> [!IMPORTANT]
> Que un documento esté **completo** no significa que esté **aprobado**. Ninguna etapa avanza sola: cada una se revisa y se aprueba explícitamente. "Debería funcionar" no es evidencia.

### Organización de los documentos

```
docs/
├── SPEC_TEMPLATE.md            # plantillas compartidas (no se editan por feature)
├── PLAN_TEMPLATE.md
├── MOBILE_GUIDELINES.md        # checklist específico de móvil
├── GENERIC_RULES.md            # reglas de trabajo del agente
├── PROMPTS.md                  # prompt inicial
├── api-contract.md             # resumen del contrato con el backend (endpoints, errores)
├── ui/
│   └── prompts-stitch.md       # prompts de diseño (Google Stitch) por pantalla
└── features/
    └── <nombre-de-la-feature>/
        ├── SPEC.md             # copia de SPEC_TEMPLATE.md, completada
        ├── PLAN.md             # tras aprobar la spec
        └── TASKS.md            # derivado del plan aprobado
AGENTS.md                       # instrucciones del proyecto para el agente
```

`TASKS.md` no tiene plantilla a propósito: **se deriva del plan aprobado**, porque las tareas dependen de las decisiones técnicas del plan.

----------

## 🧱 Stack y arquitectura

### Decidido

- **Kotlin** + **Jetpack Compose** (Material Design 3)
- **MVVM** con flujo de datos unidireccional (`StateFlow<UiState>`)
- **Ktor Client** como cliente HTTP
- **Clean Architecture** por capas: `presentation → domain ← data`. El dominio no conoce Android, Ktor ni los DTO.
- Principio de diseño: **minimizar dependencias externas** para controlar mejor la seguridad.

### Por decidir (se resuelve en el PLAN de cada feature)

| Tema | Notas |
| --- | --- |
| Inyección de dependencias | Hilt, Koin o manual |
| Serialización | kotlinx.serialization es lo natural con Ktor |
| Navegación | Navigation Compose o Navigation 3 con rutas tipadas |
| Almacenamiento seguro de tokens | Debe cubrir access token (15 min) y refresh token (30 días, con rotación) |
| Caché / modo sin conexión | Qué se guarda localmente y qué no |
| Imágenes y cámara | Captura de recibos, permisos y compresión previa al envío |
| Gráficos | Donut y barras para Dashboard y Reportes |

### Estructura propuesta

```
app/src/main/java/<paquete>/
├── core/
│   ├── network/        # cliente Ktor, interceptor de auth, refresh de token, mapeo de errores
│   ├── navigation/
│   ├── session/        # usuario, tokens y familia activa
│   └── di/
├── features/
│   └── <feature>/
│       ├── data/           # API, DTOs @Serializable, mappers, repositorio
│       ├── domain/         # modelos, contrato del repositorio, casos de uso
│       └── presentation/   # Screen + ViewModel + UiState
└── ui/
    ├── theme/
    └── components/     # componentes reutilizables (selector de familia, tile de categoría, etc.)
```

> La estructura es una propuesta inicial: se confirma o ajusta en el `PLAN.md` de la primera feature.

### Reglas que afectan a todas las features

1. **Todo se resuelve en la familia activa**; el selector de familia está en la barra superior y cambia el contexto de toda la app.
2. **Autenticación:** access token de corta duración y refresh token con rotación. Si el refresh falla o se detecta reuso, la sesión se cierra y se vuelve al login.
3. **Los roles (Owner / Member) se respetan en la UI**, pero la autorización real siempre la hace el backend.
4. **Lo que propone la IA nunca se guarda solo:** se muestra editable con badge "Sugerido por IA" y botón "Confirmar".
5. **Categorías deprecadas** siguen visibles en el histórico, marcadas como "Archivada".
6. Los **íconos de categoría** llegan de la API como una clave de texto. El catálogo se define más adelante, y la app debe tolerar una categoría sin ícono.
7. Los errores del backend llegan con un `code` de dominio: la app los traduce a mensajes comprensibles, sin mostrar errores técnicos.

----------

## 🗺️ Features y orden sugerido

Cada feature tiene su carpeta en `docs/features/` y recorre SPEC → PLAN → TASKS.

| # | Feature | Contexto del backend |
| :--: | --- | --- |
| 1 | `auth` — registro, login, sesión y refresh | Identity |
| 2 | `family-selector` — familia activa, reordenar, crear, aceptar invitación | Family & Access |
| 3 | `dashboard` — balance, categorías con monto, período | Financial Tracking |
| 4 | `categories` — categorías de gasto/ingreso, íconos, tags | Financial Tracking |
| 5 | `payment-methods` — medios de pago y predeterminado | Financial Tracking |
| 6 | `financial-items` — registro manual, listado, detalle, edición | Financial Tracking |
| 7 | `family-management` — miembros, roles, invitaciones | Family & Access |
| 8 | `ai-text-entry` — registro por texto | AI Assistance |
| 9 | `ai-receipt-entry` — registro por foto de recibo | AI Assistance |
| 10 | `reports` — gráficos, comparativas, drill-down | Reporting |
| 11 | `budgets` — presupuestos mensuales | Budgeting |
| 12 | `assistant` — chat e insights | AI Assistance |
| 13 | `profile` — perfil, ajustes, cierre de sesión | Identity |

> [!NOTE]
> El orden depende del avance del backend: una feature de la app se especifica cuando los endpoints que necesita existen o su contrato está acordado. Reportes, Presupuestos y la IA real se abordan al final. La IA empieza con un adaptador *fake* determinista en el backend.

----------

## 🛠 Instalación

### Requisitos

- **Android Studio** (versión estable reciente)
- **JDK 17+**
- **Kotlin** y **compileSdk / minSdk**: _por definir al crear el proyecto_
- Un **agente de IA con acceso al repo** (Claude Code, etc.)
- El **backend en ejecución** (local con Docker + Postgres, o desplegado)

### Puesta en marcha

1. Clona el repositorio:

    ```bash
    git clone https://github.com/rocio-plata/<repo-mobile>.git
    ```

2. Levanta el backend siguiendo su README (Postgres con Docker y `npm run dev`).
3. Configura la URL base de la API (ver *Configuración*).
4. Abre el proyecto en Android Studio, sincroniza Gradle y ejecuta la app.

### Configuración

La URL del backend **no se escribe en el código**: se define por variante de build.

| Entorno | URL base |
| --- | --- |
| Emulador → backend local | `http://10.0.2.2:<puerto>` |
| Dispositivo físico → backend local | `http://<IP-de-tu-PC>:<puerto>` |
| Producción | `https://<tu-dominio>` (HTTPS obligatorio) |

> [!WARNING]
> El tráfico sin cifrar (HTTP) solo se permite en builds de debug. La app no contiene secretos: ni credenciales de base de datos ni API keys de proveedores de IA.

### Comandos

```bash
./gradlew :app:assembleDebug              # compilar
./gradlew :app:testDebugUnitTest          # tests de JVM
./gradlew :app:lintDebug                  # lint
./gradlew :app:connectedDebugAndroidTest  # tests instrumentados (requiere dispositivo)
```

### Datos de prueba (solo desarrollo)

Usuario `aaaa` · contraseña `bbbb`

----------

## 🚦 Cómo trabajar una feature (flujo SDD)

1. **Lee las reglas:** [`AGENTS.md`](AGENTS.md) y [`docs/GENERIC_RULES.md`](docs/GENERIC_RULES.md).
2. **Lanza el prompt inicial** de [`docs/PROMPTS.md`](docs/PROMPTS.md) en tu agente, indicando la feature.
3. **Escribe la SPEC contigo, no por ti.** El agente revisa el repo, el contrato de la API y el diseño, y pregunta lo que no puede deducir. Pocas preguntas por vez. **Lo no decidido se marca como PENDIENTE.**
4. **Aprueba la SPEC** y solo entonces pasa al `PLAN.md`. Aprueba el plan y solo entonces se deriva `TASKS.md`.
5. **Implementa por tareas**, de a una, con tests que se ejecutan de verdad.
6. **Exige evidencia:** un test con nombre no es un test ejecutado, y una captura no demuestra persistencia.

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

Las pantallas se diseñan con **Google Stitch** como referencia visual (Material Design 3, tema claro primero). Los prompts están en [`docs/ui/prompts-stitch.md`](docs/ui/prompts-stitch.md). Stitch no exporta Compose: las pantallas se traducen a Compose manualmente o con ayuda del agente, respetando el tema definido en `ui/theme`.

----------

## 🔐 Seguridad y privacidad

- Autenticación individual con JWT de acceso corto y refresh token rotatorio.
- Tokens guardados con almacenamiento seguro del sistema (a definir en el plan de `auth`).
- Nunca se registran en logs tokens, contraseñas ni montos.
- Aislamiento por familia garantizado por el backend: manipular un `familyId` en una petición no da acceso a datos ajenos.
- Límites de uso de IA aplicados en el backend.

----------

## 📌 Estado del proyecto

| Área | Estado |
| --- | --- |
| Definición del producto y bounded contexts | ✅ |
| Backend (Identity, Family & Access, Financial Tracking) | 🚧 en construcción |
| Diseño de pantallas (Stitch) | 🚧 en curso |
| SPEC de la primera feature (`auth`) | ⏳ pendiente |
| Proyecto Android creado | ⏳ pendiente |

----------

## 📄 Licencia y autoría

Proyecto académico (fin de máster) desarrollado por **Rocío Plata**.
Licencia: _por definir_.
