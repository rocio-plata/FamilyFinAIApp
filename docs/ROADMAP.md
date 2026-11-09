# Roadmap de implementación

Orden esperado de construcción de la app. Es la guía para decidir **qué
funcionalidad se especifica a continuación**; no reemplaza a la SPEC, el PLAN ni
las TASKS de cada una.

## Cómo usar este documento

- Cada funcionalidad sigue el flujo SDD de `AGENTS.md`: `SPEC.md` → aprobación →
  `PLAN.md` → aprobación → `TASKS.md` → implementación con evidencia.
- Sus documentos viven en `docs/features/<NNN>-<nombre>/`. El número `NNN` se
  asigna cuando se crea la carpeta, siguiendo el orden en que se aborda.
- El alcance de cada paso es orientativo: lo fija su SPEC. Si una SPEC cambia el
  alcance o el orden, se actualiza este documento en el mismo cambio.
- Una funcionalidad se especifica cuando los endpoints que necesita existen o su
  contrato está acordado (`docs/backend/api-contract.md`). Si el backend bloquea
  un paso, se registra en la columna **Notas** y se puede adelantar el siguiente
  que no dependa de él.
- Estados: `Pendiente` · `Spec` · `Plan` · `Tareas` · `En curso` · `Hecha`.
  Coinciden con el documento más avanzado aprobado de la funcionalidad.

## Estado general

| Orden | Funcionalidad | Carpeta | Estado | Notas |
| :--: | --- | --- | --- | --- |
| 1 | Andamiaje de la app | `001-scaffold` | Hecha | |
| 2 | Cabecera principal | `002-main-header` | Hecha | Usa una sesión temporal (`StaticSessionProvider`) |
| 3 | Autenticación y sesión | `003-auth` | Tareas | BG-01: el registro del backend permite enumerar emails |
| 4 | Identidad visual de la app | `004-brand-identity` | Hecha | CA-06 sin verificar: no se logró ver la pantalla de arranque del sistema |
| 5 | Categorías y tags | `categories` | Pendiente | |
| 6 | Medios de pago | `payment-methods` | Pendiente | |
| 7 | Movimientos | `financial-items` | Pendiente | |
| 8 | Dashboard | `dashboard` | Pendiente | |
| 9 | Gestión de familias | `family-management` | Pendiente | |
| 10 | Presupuestos | `budgets` | Pendiente | |
| 11 | Reportes | `reports` | Pendiente | |
| 12 | Registro por texto con IA | `ai-text-entry` | Pendiente | |
| 13 | Registro por recibo con IA | `ai-receipt-entry` | Pendiente | |
| 14 | Asistente | `assistant` | Pendiente | |
| 15 | Perfil y ajustes | `profile` | Pendiente | |

## Detalle por paso

### 1. Andamiaje de la app — Hecha

Estructura base del proyecto, tema y pantalla principal con barra inferior.

### 2. Cabecera principal — Hecha

Selector de familia activa, contador de invitaciones pendientes, inicial del
perfil y caché offline de solo lectura. La familia activa vive solo en memoria
de `MainViewModel`.

### 3. Autenticación y sesión

- **Por qué ahora:** todas las funcionalidades necesitan una sesión real y la
  actual es temporal.
- **Alcance orientativo:** registro, login, almacenamiento seguro de tokens,
  refresh con rotación, cierre de sesión por refresh fallido o reuso, logout.
- **Depende de:** 001.
- **Decisiones que fuerza:** navegación (flujo login → principal), almacenamiento
  seguro de tokens, manejo común de 401 y traducción de `code` de error del
  backend a mensajes.
- **Backend:** Identity.

### 4. Identidad visual de la app — Hecha

Icono propio del lanzador (adaptativo, redondo y temático), nombre "FamilyFin AI"
y logo de la pantalla de acceso en variantes clara y oscura, en lugar de los de la
plantilla de Android. Queda sin verificar que la pantalla de arranque que dibuja
el sistema muestre el icono nuevo (CA-06).

### 5. Categorías y tags

- **Por qué ahora:** un movimiento necesita una categoría, de la que hereda el
  tipo (gasto o ingreso).
- **Alcance orientativo:** listado por tipo, alta, edición, archivado, tags;
  categorías sin ícono y estado "Archivada".
- **Depende de:** auth.
- **Decisiones que fuerza:** primera funcionalidad que opera sobre la familia
  activa: dónde se comparte ese estado fuera de `MainViewModel`.
- **Backend:** Financial Tracking.

### 6. Medios de pago

- **Alcance orientativo:** listado, alta, edición y medio predeterminado.
- **Depende de:** auth. Puede hacerse en paralelo con categorías.
- **Backend:** Financial Tracking.

### 7. Movimientos

- **Por qué ahora:** es el valor central de la app y la base de dashboard,
  presupuestos, reportes e IA.
- **Alcance orientativo:** alta manual, listado, detalle y edición.
- **Depende de:** categorías y medios de pago.
- **Decisiones que fuerza:** representación y formato del dinero (entero en la
  unidad menor, nunca `Double`; CLP por defecto) y un formulario de movimiento
  reutilizable por las funcionalidades de IA.
- **Backend:** Financial Tracking.

### 8. Dashboard

- **Alcance orientativo:** balance del período y categorías con monto.
- **Depende de:** movimientos (sin ellos se muestra vacío).
- **Backend:** Financial Tracking.

### 9. Gestión de familias

- **Alcance orientativo:** miembros, roles, invitaciones (enviar y aceptar),
  crear familia y reordenar familias. Completa lo que la cabecera solo muestra.
- **Depende de:** auth.
- **Backend:** Family & Access.

### 10. Presupuestos

- **Alcance orientativo:** presupuestos mensuales por categoría y su progreso.
- **Depende de:** categorías y movimientos.
- **Backend:** Budgeting.

### 11. Reportes

- **Alcance orientativo:** gráficos, comparativas y drill-down.
- **Depende de:** movimientos.
- **Decisiones que fuerza:** cómo dibujar los gráficos (donut y barras).
- **Backend:** Reporting.

### 12. Registro por texto con IA

- **Alcance orientativo:** texto libre → movimiento sugerido, editable, con
  badge "Sugerido por IA" y confirmación explícita.
- **Depende de:** movimientos (reutiliza su formulario).
- **Backend:** AI Assistance.

### 13. Registro por recibo con IA

- **Alcance orientativo:** foto de recibo → un gasto sugerido por recibo.
- **Depende de:** registro por texto (mismo flujo de confirmación).
- **Decisiones que fuerza:** cámara, permisos y compresión previa al envío.
- **Backend:** AI Assistance.

### 14. Asistente

- **Alcance orientativo:** consultas en lenguaje natural, insights y
  recomendaciones.
- **Depende de:** movimientos; se beneficia de presupuestos y reportes.
- **Backend:** AI Assistance.

### 15. Perfil y ajustes

- **Alcance orientativo:** datos de perfil, ajustes y moneda de la familia.
  El logout se entrega en auth.
- **Depende de:** auth.
- **Backend:** Identity, Family & Access.
