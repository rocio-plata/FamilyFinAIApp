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
| 2 | Cabecera principal | `002-main-header` | Hecha | Ya usa la sesión real de la 003; su caché se vacía al terminar la sesión. Pendientes de verificar: TalkBack y CA-18 con dos cuentas reales |
| 3 | Autenticación y sesión | `003-auth` | Hecha | BG-01: el registro del backend permite enumerar emails. Sin verificar: CA-03, CA-12 y CA-21 de extremo a extremo y TalkBack con voz |
| 4 | Identidad visual de la app | `004-brand-identity` | Hecha | CA-06 sin verificar: no se logró ver la pantalla de arranque del sistema |
| 5 | Categorías y tags | `005-categories` | Hecha | Listado, alta, edición, archivar, eliminar y tags (con reordenado). BG-01: el backend no permite reactivar lo archivado. BG-02 a BG-04: la categoría no tiene color, fecha de archivado ni orden modificable. BG-05: sin categorías de ingreso por defecto. BG-06: el backend emite `FINANCIAL_TRACKING.*` en vez de `FINANCIAL.*`. BG-07: el nombre duplicado se rechaza entre tipos. Sin verificar de extremo a extremo: CA-07, los casos «con movimientos» de CA-12 y CA-17, CA-19 con datos de más de 24 h o sin datos, rol Integrante y TalkBack |
| 6 | Medios de pago | `006-payment-methods` | Hecha | Listado, alta, renombrado, archivar y eliminar. BG-01: el backend no permite leer el medio predeterminado, que pasa a «Perfil y ajustes». BG-02: no se puede reactivar lo archivado. BG-04: el backend emite `FINANCIAL_TRACKING.*` en vez de `FINANCIAL.*` (confirmado en ejecución). BG-07: el backend permite renombrar un archivado, que la app no ofrece. Sin verificar de extremo a extremo: CA-14 («Efectivo» es predeterminado), CA-06 (Integrante), el caso «con movimientos» de CA-13 y CA-15 sin copia o con datos de más de 24 h. Sin verificar manualmente: TalkBack, fuente máxima con teclado, foco automático del campo y atrás o tocar fuera con el formulario abierto |
| 7 | Movimientos | `007-financial-items` | Hecha | Listado por mes con resumen, filtro por tipo y subtotales por día; alta, detalle, edición y eliminación; miembros de la familia para mostrar quién registró; moneda de la familia; pestaña seleccionada en `MainViewModel`. BG-06: el backend no tiene consulta individual y el detalle sale de los meses cargados. BG-07: el backend emite `FINANCIAL_TRACKING.*` en vez de `FINANCIAL.*` (sin comprobar el prefijo real en ejecución). BG-11: el backend no admite movimientos con fecha futura (a pedir). Verificado en el emulador con el backend local (2026-11-17), con los matices de la tabla de `TASKS.md`: CA-01 a CA-11, CA-13, CA-14, CA-16, CA-17, CA-19, CA-20, CA-21, CA-23, CA-25 y CA-26, y la parte propia de CA-24. Sin verificar de extremo a extremo: CA-15 y los casos de otro miembro y «Ex-miembro» de CA-24 (sin segunda cuenta en la familia), CA-12 y CA-22 (medio, categoría o etiqueta archivados por otra sesión), CA-18, CA-16 con datos de más de 24 h, la elección de etiqueta con datos reales y TalkBack |
| 8 | Dashboard | `008-dashboard` | Hecha | Inicio con resumen del mes, gráfico y categorías, comparación con el mes anterior, alta desde una categoría y monto del mes por medio de pago; calculado en la app con los movimientos del mes. Color de categoría elegible (BG-01, resuelta en el backend). Sin verificar: TalkBack y el cambio entre dos familias (ver `TASKS.md`) |
| 9 | Gestión de familias | `009-family-management` | Tareas | BG-01: el backend no permite a un Integrante salir de una familia (bloquea solo ese requisito). BG-02: no hay operación para renombrar ni eliminar una familia |
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

### 3. Autenticación y sesión — Hecha

- **Por qué ahora:** todas las funcionalidades necesitan una sesión real y la
  que había era temporal (retirada).
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

- **Alcance orientativo:** listado, alta, renombrado, archivado y eliminación.
  El medio predeterminado es una preferencia del usuario y pasa a «Perfil y
  ajustes». Se abre desde «Gestionar» en Inicio, como categorías.
- **Depende de:** auth. Puede hacerse en paralelo con categorías.
- **Backend:** Financial Tracking.

### 7. Movimientos — Hecha

- **Por qué ahora:** es el valor central de la app y la base de dashboard,
  presupuestos, reportes e IA.
- **Alcance orientativo:** alta manual, listado, detalle y edición. Se
  implementó también la eliminación, el filtro por tipo y el resumen del mes.
- **Depende de:** categorías y medios de pago.
- **Decisiones que fuerza:** representación y formato del dinero (entero en la
  unidad menor, nunca `Double`; CLP por defecto) y un formulario de movimiento
  reutilizable por las funcionalidades de IA. Resueltas en la 007: el dinero es
  `Money` con la moneda de la familia (`Family.currency`), el formulario es un
  solo `MovementFormViewModel` para el alta y la edición, y la pestaña
  seleccionada vive en `MainViewModel`.
- **Backend:** Financial Tracking.

### 8. Dashboard

- **Alcance orientativo:** balance del período y categorías con monto. A
  valorar en su spec: que tocar una categoría del resumen abra el alta de un
  movimiento (idea surgida en la 007).
- **Depende de:** movimientos (sin ellos se muestra vacío).
- **Backend:** Financial Tracking.

### 9. Gestión de familias

- **Alcance orientativo:** miembros, roles, invitaciones (enviar, revocar,
  aceptar y rechazar), crear familia, reordenar familias, salir de una familia y
  cambiar su moneda. Completa lo que la cabecera solo muestra.
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

- **Alcance orientativo:** datos de perfil, ajustes y medio de pago
  predeterminado del usuario por familia (requiere que el backend permita
  leerlo: BG-01 de `006-payment-methods`).
  El logout se entrega en auth.
- **Depende de:** auth.
- **Backend:** Identity, Family & Access.
