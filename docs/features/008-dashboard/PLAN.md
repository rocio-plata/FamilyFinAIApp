# PLAN: Dashboard

**SPEC de referencia:** `docs/features/008-dashboard/SPEC.md`
**Versión de la spec revisada:** aprobada el 2026-11-19 (rama `feature/008-dashboard`, sin commit todavía)
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

Rutas relativas a `app/src/main/java/com/rocioplata/familyfinaiapp/` salvo que se indique otra raíz.

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| `MovementsRepository` | `domain/movements/MovementsRepository.kt` | `movements(familia, mes)` (Flow con caché y `isStale`) y `refresh(familia, mes)`. Fuente de los importes del dashboard (RF-13); las mutaciones ya actualizan memoria y caché del mes, así que el dashboard se actualiza solo. Sin cambios. |
| `Movement`, `MovementsData`, `MovementType` | `domain/movements/Movement.kt` | Movimiento con `type`, `amount: Money`, `categoryId` y `date: LocalDate` en la zona del dispositivo. Se reutilizan. |
| `MovementDates` | `domain/movements/MovementDates.kt` | `today()` y `currentMonth()` con el reloj del dispositivo; inyectable en tests. Se reutiliza para el mes en curso y el tramo de la comparación (RF-01, RF-06). |
| `Money`, `sum` | `domain/money/Money.kt` | Montos enteros en unidad menor con aritmética exacta. Totales, montos por categoría y disponible. |
| `MoneyFormat` | `ui/movements/MoneyFormat.kt` | `formatted`, `formattedSigned` y `formatMonth`. Se reutilizan para montos y el selector de mes. |
| `MovementsViewModel` | `ui/movements/MovementsViewModel.kt` | Patrón de referencia: mes en `MutableStateFlow`, `flatMapLatest` por mes, `refreshJobs` por mes, refresco en `init`, `onStart` y al recuperar conexión; `Loading`/`Error`/`Content` según caché y resultado del refresco; monedas mezcladas = error. |
| `CategoriesRepository`, `Category`, `IconUpdate` | `domain/categories/` | Lista de la familia (con archivadas) en el orden del backend, alta y edición. Se añade el color (RF-14, RF-15). |
| `CategoriesRemoteDataSource` | `data/categories/CategoriesRemoteDataSource.kt` | DTO de categoría (`icon` opcional), `createCategory` envía `icon` si lo hay y `updateCategory` envía `newIcon` (o `null`). Se añade `color`/`newColor`. Un `400` con código desconocido ya se traduce a `CategoryFailure.Unknown`. |
| `CategoriesLocalDataSource` | `data/categories/CategoriesLocalDataSource.kt` | Caché `categories_cache`, `schemaVersion = 1`, JSON con `ignoreUnknownKeys` e `icon` opcional. Se añade `color` opcional. |
| `CategoriesRepositoryImpl.updateCategory` | `data/categories/CategoriesRepositoryImpl.kt` | Aplica el cambio de icono a memoria y caché tras el éxito. Se aplica igual el color. |
| `CategoryIcons` | `ui/categories/CategoryIcons.kt` | Catálogo de iconos por clave con descripción accesible. Patrón para el catálogo de colores. |
| `CategoryEditViewModel`, `CategoryEditUiState`, `CategoryEditContent` | `ui/categories/` | Borrador de nombre, icono y tipo; `hasChanges` y confirmación al salir (CA-24 de la 005). Se añade el color al borrador y el selector a la pantalla. |
| `CategoriesContent` | `ui/categories/CategoriesContent.kt` | Listado de la gestión con el icono de cada categoría. Mostrará el icono en su color (RF-14). |
| `MainViewModel`, `MainUiState` | `ui/main/` | Pestaña seleccionada, familia activa, rutas en memoria (`managementRoute`, `managementRouteInstance`) y `openMovementCreate()`. Se amplía para el alta con categoría y para reiniciar el dashboard al cambiar de familia. |
| `MovementRoute` | `ui/main/MovementRoute.kt` | `Create` es un `data object`. Pasa a llevar la categoría opcional (RF-11). |
| `MainScreen` | `ui/main/MainScreen.kt` | Entrada con estado (crea los ViewModels con `viewModelFactory` y claves `main-<generación>-…`) y sobrecarga stateless con huecos `movementsContent` y `managementContent`. Se añade el hueco del dashboard. |
| `HomeContent` | `ui/main/HomeContent.kt` | Hoy: filas "Medios de pago" y "Categorías" con "Gestionar" y un texto provisional. Pasa a ser el contenedor desplazable de Inicio: dashboard + "Medios de pago" (RF-10, RF-12). |
| `MovementFormViewModel`, `MovementFormScreen` | `ui/movements/` | Alta con tipo `EXPENSE`, sin categoría y fecha de hoy; `hasChanges` del alta compara con esos valores iniciales. Se añade un tipo y una categoría iniciales (RF-11). |
| `StaleDataBanner` | `ui/main/StaleDataBanner.kt` | Aviso de datos de más de 24 h. Patrón para el aviso del dashboard (CA-16). |
| Tema | `ui/theme/Color.kt`, `ui/theme/Theme.kt` | Ya define `Income`, `Expense`, `IncomeContainer`, `ExpenseContainer` y los acentos de categoría de `docs/DESIGN.md` (`SlateBlue`, `SoftPlum`, `SagePine`, `NordicSand`, `Mauve`, `TerracottaClay`), solo para modo claro. |
| Falsos de tests | `app/src/test/java/com/rocioplata/familyfinaiapp/ui/movements/Fakes.kt` | `FakeMovements` (datos por mes, `refreshed`, `onRefresh`, `gate`) y falsos de categorías, medios de pago y miembros. Se reutilizan. |
| Host de UI | `app/src/androidTest/java/com/rocioplata/familyfinaiapp/ui/main/TestMainHost.kt` | Reproduce lo mínimo de `MainViewModel` sobre la sobrecarga stateless de `MainScreen`; los tests de Inicio dependen de `home_manage_categories` y `home_manage_payment_methods`. |

**Convenciones y patrón de referencia:** capas `domain`/`data`/`ui` por funcionalidad; DI manual con
`AppContainer`; ViewModel con `StateFlow<…UiState>` creado en la entrada con estado y contenido stateless;
estados sellados `Loading`/`Content`/`Error`; refresco en `init`, `ON_START` y al recuperar conexión; rutas en
memoria de `MainViewModel`; textos en `strings.xml`; colores en `ui/theme/`. Patrón principal:
`MovementsViewModel` + `MovementsContent`.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de datos y eventos hasta la interfaz. Usa un diagrama si aporta claridad. -->

El dashboard es una vista calculada en la app sobre datos que ya existen (restricción de la spec): los
movimientos del mes mostrado y del anterior (`MovementsRepository`) y las categorías de la familia
(`CategoriesRepository`). No hay repositorio ni caché nuevos: la caché por mes de la 007 da el modo sin
conexión (CA-16) y, como las altas, ediciones y eliminaciones ya se aplican a esa caché, el dashboard las
refleja sin recargar (RF-13, CA-12).

```
MovementsRepository ──movements(f, mes)────┐
                    ──movements(f, mes−1)──┤
CategoriesRepository ─categories(f)────────┼─> DashboardViewModel ──StateFlow<DashboardUiState>──> DashboardContent
MovementDates (hoy, mes en curso) ─────────┘        │  mes, tipo activo, refrescos       │ (stateless, dentro de HomeContent)
                                                    └─ DashboardCalculator (dominio puro) │
                                                                                          ├─ toca categoría → MainViewModel.openMovementCreate(categoría)
                                                                                          └─ "Gestionar"    → MainViewModel.openCategories()
```

1. **Cálculo en el dominio.** Un calculador puro (`domain/dashboard`) recibe los movimientos del mes, los del
   mes anterior, las categorías, el tipo activo, la moneda y "hoy", y devuelve: totales de gastos e ingresos,
   disponible (RF-02), porcentaje gastos/ingresos (RF-05), variación frente al mes anterior con el tramo de
   días correcto (RF-06) y la lista de categorías del tipo activo con su monto, en el orden de la gestión y
   las archivadas con movimientos al final (RF-07 a RF-09). Al ser puro, cada regla se prueba con tests JVM.
2. **Presentación.** `DashboardViewModel` mantiene el mes (independiente del de Movimientos, RF-01) y el tipo
   activo (RF-03), combina los flujos y llama al calculador. Refresca el mes mostrado, el anterior y las
   categorías con el mismo esquema que `MovementsViewModel`.
3. **Interfaz.** `DashboardContent` (stateless) dibuja el selector de mes, la tarjeta de desglose con el
   gráfico circular (Canvas de Compose, sin librería), el indicador de comparación y la cuadrícula. Inicio
   pasa a ser un contenedor desplazable con el dashboard y, debajo, "Medios de pago" (RF-12).
4. **Color de categoría.** Campo opcional `color` en la categoría (dominio, red y caché) con un catálogo
   cerrado en la app (`CategoryColors`), respaldo por posición (RF-14) y selector en la edición (RF-15).
5. **Alta desde una categoría.** `MovementRoute.Create` lleva la categoría y el tipo opcionales; el
   formulario arranca con ellos y la fecha de hoy (RF-11).

## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

No aplica: el proyecto tiene un único módulo `app/` y no se modulariza.

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son propuestas.
Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `domain/categories/Category.kt` | Modificar | `color: String?` (clave de la paleta; nula o desconocida usa el respaldo). | RF-14 |
| `domain/categories/CategoriesRepository.kt` | Modificar | `createCategory(…, color: String?)`; `updateCategory(…, color: ColorUpdate)` con `ColorUpdate` (`Unchanged`/`Remove`/`Set`) igual que `IconUpdate`. | RF-15 |
| `data/categories/CategoriesRemoteDataSource.kt` | Modificar | Leer `color` del DTO; enviar `color` en el alta (si hay) y `newColor` (o `null`) en la edición. | RF-14, RF-15 |
| `data/categories/CategoriesLocalDataSource.kt` | Modificar | `color` opcional en el JSON con valor por defecto `null`; `schemaVersion` sigue en 1. | RF-14 |
| `data/categories/CategoriesRepositoryImpl.kt` | Modificar | Pasar el color y aplicarlo a memoria y caché tras el éxito, como el icono. | RF-15 |
| `ui/categories/CategoryColors.kt` | Crear | Catálogo de colores por clave (nombre accesible, color claro y oscuro) y `colorFor(category, posición)` con el respaldo por posición. | RF-14 |
| `ui/theme/Color.kt` | Modificar | Valores claro/oscuro de la paleta de categoría (ver "Datos y contratos"). | RF-14 |
| `ui/categories/CategoryEditViewModel.kt`, `CategoryEditUiState.kt` | Modificar | Color en el borrador, `setColor`, `hasChanges` y envío en alta y edición. | RF-15, CA-14 |
| `ui/categories/CategoryEditContent.kt` | Modificar | Cuadrícula con los 16 colores a la vista (círculo y nombre; cuatro columnas en un teléfono, menos con texto grande) sobre la cuadrícula de iconos, como la sección "Icono y tonalidad" del mockup 006; el icono de vista previa usa el color. Solo lectura para Integrante y archivadas. | RF-15, CA-14 |
| `ui/categories/CategoriesContent.kt` | Modificar | Icono de cada categoría en su color. | RF-14, CA-13 |
| `domain/dashboard/DashboardCalculator.kt` (+ modelo `DashboardSummary`) | Crear | Reglas de negocio puras del dashboard (sin Android). | RF-02, RF-04 a RF-09, RF-13 |
| `ui/dashboard/DashboardViewModel.kt`, `DashboardUiState.kt` | Crear | Mes, tipo activo, refrescos, estado `Loading`/`Error`/`Content`. | RF-01, RF-03, RF-13, CA-15 a CA-17 |
| `ui/dashboard/DashboardScreen.kt` | Crear | Entrada con estado: crea el ViewModel y conecta `ON_START`. | RF-01 |
| `ui/dashboard/DashboardContent.kt`, `DonutChart.kt`, `DashboardTexts.kt` | Crear | Interfaz stateless, gráfico circular con Canvas y textos (porcentajes, variación, descripción accesible del gráfico). | RF-01 a RF-11 |
| `ui/main/HomeContent.kt` | Modificar | Contenedor desplazable: hueco del dashboard + sección "Medios de pago"; se quita la fila "Categorías" y el texto provisional. Espacio inferior para que el "+" no tape la última fila. | RF-10, RF-12, CA-10 |
| `ui/main/MovementRoute.kt` | Modificar | `Create` pasa de `data object` a `data class Create(val categoryId: String? = null, val type: MovementType? = null)`. | RF-11 |
| `ui/main/MainViewModel.kt`, `MainUiState.kt` | Modificar | `openMovementCreate(categoryId, type)`; `activeFamilyInstance` que cambia cuando cambia la familia activa, para la clave del dashboard. | RF-11, CA-18 |
| `ui/main/MainScreen.kt` | Modificar | Hueco `homeDashboardContent` en la sobrecarga stateless; la entrada con estado crea `DashboardScreen` con clave `main-<generación>-dashboard-<familia>-<activeFamilyInstance>`; pasa la categoría inicial al formulario. | RF-01, RF-11, CA-17, CA-18 |
| `ui/movements/MovementFormViewModel.kt`, `MovementFormScreen.kt` | Modificar | Parámetros `initialType` e `initialCategoryId`; los campos y `hasChanges` del alta parten de ellos. | RF-11, CA-11 |
| `app/src/main/res/values/strings.xml` | Modificar | Textos del dashboard, nombres de los colores y descripción accesible del gráfico. | RF-01 a RF-15 |
| `docs/DESIGN.md` | Modificar | Paleta de categoría con claves, nombres y valores claro/oscuro. | RF-14 |
| `AGENTS.md`, `docs/ROADMAP.md` | Modificar | Arquitectura del dashboard y del color de categoría; estado de la 008. | — |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Modelos y contratos de entrada y salida:** sin endpoints nuevos para el dashboard. Categorías según el
  contrato §3.4.2 actualizado: `color` (1 a 40 caracteres) en `POST`, `newColor` (cadena o `null`) en
  `PATCH`, `color` (cadena o `null`) en `GET`. `FINANCIAL_TRACKING.INVALID_CATEGORY_COLOR` (400) no tiene
  tratamiento propio: cae en el error genérico al guardar, que es lo que pide la spec.
- **Identificadores, relaciones y restricciones:** movimientos ↔ categorías por `categoryId`. Paleta de 16
  colores con claves estables en minúsculas (lo que guarda el backend), nombre visible y anunciado, y valor
  para modo claro y oscuro. Parte de los acentos de `docs/DESIGN.md` y del mockup 006; Ámbar y Arena se
  oscurecieron respecto de `#D9822B` y `#C4A482`, que no llegaban a 3:1 sobre blanco. Contraste calculado
  (WCAG 2.x) contra la superficie de la tarjeta, blanco en claro y `#263331` en oscuro; todos superan el 3:1
  que exige WCAG 1.4.11:

  | # | Clave | Nombre | Claro | Contraste | Oscuro | Contraste |
  | :-: | --- | --- | --- | :-: | --- | :-: |
  | 1 | `sage` | Salvia | `#386B5A` | 6,1 | `#7FB8A4` | 5,8 |
  | 2 | `amber` | Ámbar | `#B0691C` | 4,3 | `#E8A25C` | 6,1 |
  | 3 | `slate` | Pizarra | `#4B6B94` | 5,5 | `#93B0D6` | 5,9 |
  | 4 | `coral` | Coral | `#C06352` | 4,1 | `#EE9A8A` | 6,0 |
  | 5 | `plum` | Ciruela | `#7A5877` | 6,0 | `#C59DC1` | 5,6 |
  | 6 | `olive` | Oliva | `#66752B` | 5,1 | `#B3C27A` | 6,8 |
  | 7 | `ocean` | Océano | `#2F6F9A` | 5,4 | `#80BCE3` | 6,4 |
  | 8 | `rose` | Rosa | `#B8506A` | 4,8 | `#EE93AA` | 5,9 |
  | 9 | `sand` | Arena | `#93734F` | 4,4 | `#D9BC98` | 7,3 |
  | 10 | `indigo` | Índigo | `#555FA3` | 5,9 | `#A3ACE6` | 6,0 |
  | 11 | `brick` | Ladrillo | `#9E4B3C` | 6,0 | `#E39383` | 5,5 |
  | 12 | `teal` | Turquesa | `#2D7366` | 5,6 | `#7CC4B5` | 6,5 |
  | 13 | `lavender` | Lavanda | `#7A68A6` | 4,8 | `#BCADE6` | 6,4 |
  | 14 | `mustard` | Mostaza | `#8C7A1E` | 4,3 | `#D8C66E` | 7,6 |
  | 15 | `mauve` | Malva | `#9B6A78` | 4,4 | `#D6A7B4` | 6,3 |
  | 16 | `graphite` | Grafito | `#5E6B70` | 5,5 | `#A9B6BB` | 6,3 |

  El orden de la tabla es el del selector y el del respaldo por posición: alterna tonos cálidos, fríos y
  neutros para que categorías vecinas en el gráfico no queden con colores parecidos. El fondo suave de cada
  tarjeta y del icono (como en los mockups) se deriva del mismo color con transparencia, sin valores
  adicionales. Los valores definitivos se registran en `docs/DESIGN.md`.
- **Origen de los datos mostrados y transformaciones:**
  - Totales: suma de `amount` por `type` de todos los movimientos del mes, también los de categorías que la
    app no encuentre en la lista; disponible = ingresos − gastos (RF-02).
  - Porcentaje gastos/ingresos y variación: con `BigDecimal` a partir de las unidades menores, redondeo
    `HALF_UP` a un decimal; sin ingresos no hay porcentaje (RF-05). Variación nula si la base es 0 o los
    datos del mes anterior son nulos (RF-06). Una variación que redondea a 0,0 % se muestra neutra
    (ni verde ni rojo).
  - Tramo de la comparación: si el mes mostrado es el en curso, se cuentan del mes actual los días ≤ hoy y del
    anterior los días ≤ `min(hoy, longitud del mes anterior)`; si hoy supera la longitud del mes anterior, el
    mes anterior completo (RF-06, CA-08). En un mes pasado, ambos meses completos.
  - Categorías del tipo activo: las activas en el orden de la lista del repositorio (el del backend, el mismo
    de la gestión) y después las archivadas con monto > 0 en ese mismo orden (RF-08, RF-09). El contador
    cuenta las que se muestran. El gráfico usa solo las de monto > 0, en ese orden (RF-04).
  - Color de una categoría: su clave si el catálogo la conoce; si no, el color de la paleta en la posición
    `índice mod 16`, donde el índice es la posición de la categoría entre las de su tipo en la lista del
    repositorio. Así una misma categoría tiene el mismo color en el dashboard y en la gestión (RF-14).
- **Persistencia, consultas y actualizaciones:** solo cambia la caché de categorías (`color` opcional). El
  mes y el tipo activo viven en memoria en el ViewModel. El dashboard dispara `refresh` del mes mostrado,
  del anterior y de las categorías; refrescar el mes anterior también lo deja en la caché de movimientos.
- **Convivencia entre datos locales y remotos:** la de la 007 y la 005 sin cambios (`CachePolicy`: descarte a
  los 7 días o de otro usuario, aviso desde 24 h). El aviso del dashboard depende del mes mostrado
  (`MovementsData.isStale`).
- **Compatibilidad y migraciones de datos existentes:** una caché de categorías guardada por la versión
  anterior se lee sin color (valor por defecto `null`), como se hizo con `Family.currency` en `header_cache`;
  no se sube `schemaVersion`. Las categorías existentes sin color usan el respaldo por posición. Sin
  migraciones de backend en la app.

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de MOBILE_GUIDELINES.md. -->

- **Gestión del estado de interfaz y navegación:** `DashboardUiState` lleva siempre `month`, `canGoNext`,
  `isCurrentMonth` y `activeType` (el selector se ve también al cargar o fallar) y un `content` sellado:
  `Loading`, `Error` (mes sin datos y refresco fallido, o monedas mezcladas) o `Content` (resumen calculado,
  `isStale`, `isRefreshing`). "Mes actual" vuelve a `dates.currentMonth()` (RF-01). Tocar una categoría
  activa llama a `MainViewModel.openMovementCreate(categoryId, type)`; las archivadas no tienen acción
  (RF-11). "Gestionar" llama a `openCategories()` (RF-10). Al cerrar el formulario se vuelve a Inicio con el
  mismo `DashboardViewModel`.
- **Conservación y restauración del estado:** el `DashboardViewModel` vive en el `ViewModelStore` de la
  actividad con la clave `main-<generación>-dashboard-<familia>-<activeFamilyInstance>`: sobrevive a girar la
  pantalla, a pasar a segundo plano y a cambiar de pestaña (CA-17). `activeFamilyInstance` lo incrementa
  `MainViewModel` cada vez que cambia la familia activa, así que volver a una familia anterior también
  empieza en el mes en curso con gastos (CA-18). Tras terminar el proceso no se guarda nada: mes en curso y
  gastos (CA-17). Es el mismo mecanismo que `managementRouteInstance` para los formularios.
- **Ejecución, concurrencia y cancelación de operaciones:** refrescos en `viewModelScope`, uno por mes a la
  vez (`refreshJobs`, como en `MovementsViewModel`); al cambiar de mes se refrescan el nuevo mes y su
  anterior. `flatMapLatest` por mes evita mezclar datos de dos meses. Sin capturar `CancellationException`.
  Las mutaciones de categoría siguen las reglas de la 005 (una a la vez, `isBusy`).
- **Errores, reintentos y prevención de duplicados:** mes sin caché y refresco fallido → `Error` con
  "Reintentar" (CA-16); el fallo del mes anterior solo oculta el indicador (RF-06); el fallo de categorías no
  impide ver los totales. `MovementFailure.NotAllowed` llama a `onInsufficientRole()` como en Movimientos.
  Tocar varias veces una categoría no abre dos formularios: `openRoute` ya ignora una ruta sobre otra.
  Guardar un color sin conexión devuelve `Offline` sin llamar al backend (regla existente de la 005).
- **Otras consideraciones mobile aplicables y su solución:**
  - Accesibilidad: el gráfico es una sola unidad con descripción textual ("Gastos 1.120.450: Comestibles
    38 %, …"); las tarjetas Gastos/Ingresos se anuncian con rol de pestaña y estado seleccionado; la variación
    incluye signo y flecha, y su descripción dice "subió"/"bajó"; las tarjetas de categoría juntan icono,
    nombre, monto y "Archivada" en una sola descripción; cada color del selector se anuncia por su nombre y
    como seleccionado; áreas táctiles de al menos 48 dp.
  - Texto grande y anchos: la cuadrícula usa columnas adaptativas por ancho mínimo (3 en un teléfono normal,
    menos con texto grande), nombres en dos líneas con elipsis y montos que se reducen sin cortarse; todo
    Inicio es desplazable.
  - Rendimiento: el cálculo es lineal en los movimientos de dos meses y se hace en `combine` fuera del hilo de
    la interfaz si hiciera falta (`flowOn(Dispatchers.Default)` solo si las pruebas lo justifican).
  - Privacidad: sin montos ni nombres en logs.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

- Sin dependencias nuevas: el gráfico circular se dibuja con `Canvas` de Compose (`drawArc`), ya incluido en
  `androidx-compose-ui`; los iconos de flecha salen de `material-icons-extended`, ya configurada.
- Sin permisos ni cambios de configuración. Las reglas de copia de seguridad no cambian (`categories_cache` y
  `movements_cache` ya están excluidas).
- Backend: requiere el `color` de categoría desplegado en el entorno de pruebas (BG-01, informado como
  implementado).

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, UI o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

Tests nuevos propuestos: `DashboardCalculatorTest` (JVM), `DashboardViewModelTest` (JVM, con `Fakes.kt` y
`MovementDates` con reloj fijo), `CategoryColorsTest` (JVM), `DashboardContentTest` (`androidTest`). Se
amplían `CategoriesRemoteDataSourceTest`, `CategoriesLocalDataSourceTest`, `CategoriesRepositoryImplTest`,
`CategoryEditViewModelTest`, `CategoryEditContentTest`, `MovementFormViewModelTest`, `MainViewModelTest` y
`MainScreenTest`.

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | `DashboardViewModelTest` (anterior, siguiente bloqueado, "Mes actual"); `DashboardContentTest` (botones) | Reloj fijo | Tests verdes |
| CA-02 | `DashboardViewModelTest` + manual (cambiar mes en Inicio y en Movimientos) | Emulador | Tests verdes; pasos manuales en `TASKS.md` |
| CA-03 | `DashboardCalculatorTest` (totales y disponible negativo) | Datos de la spec | Tests verdes |
| CA-04 | `DashboardViewModelTest` (cambio de tipo); `DashboardContentTest` (título, estado seleccionado en semántica) | — | Tests verdes |
| CA-05 | `DashboardCalculatorTest` (segmentos solo con monto > 0, en orden); `DashboardContentTest` (descripción del gráfico); manual visual | Emulador | Tests verdes; captura claro/oscuro |
| CA-06 | `DashboardCalculatorTest` (60,6 %, 100 %, sin ingresos) | Datos de la spec | Tests verdes |
| CA-07 | `DashboardCalculatorTest` (−4,8 %, base 0, mes anterior nulo); `DashboardViewModelTest` (anterior sin caché y fallido → sin indicador) | Falsos | Tests verdes |
| CA-08 | `DashboardCalculatorTest` (hoy día 10; hoy 31 tras un mes de 30; fin de febrero) | Reloj fijo | Tests verdes |
| CA-09 | `DashboardCalculatorTest` (orden, $0, archivada con y sin movimientos, contador); `DashboardContentTest` (atenuada, "Archivada") | — | Tests verdes |
| CA-10 | `MainScreenTest` (sin sección "Categorías"; "Gestionar" de ambas secciones) | `TestMainHost` | Tests verdes |
| CA-11 | `MainViewModelTest` (`openMovementCreate` con categoría); `MovementFormViewModelTest` (tipo, categoría y fecha de hoy; cerrar sin cambios no pide confirmación); `DashboardContentTest` (archivada sin acción) | — | Tests verdes |
| CA-12 | `DashboardViewModelTest` (el falso emite el mes cambiado → el resumen cambia) + manual (alta, edición, borrado y comparación con Movimientos) | Emulador con backend local | Tests verdes; pasos manuales |
| CA-13 | `CategoryColorsTest` (clave conocida, nula, desconocida, respaldo estable por posición); manual en claro y oscuro | Emulador | Tests verdes; capturas |
| CA-14 | `CategoryEditViewModelTest` (elegir, guardar, `Offline`, Integrante, descarte); `CategoriesRemoteDataSourceTest` (`color`/`newColor`/`null`); `CategoriesRepositoryImplTest` (aplica el color); `CategoryEditContentTest` (selector) | Ktor mock | Tests verdes; manual contra backend |
| CA-15 | `DashboardCalculatorTest` + `DashboardContentTest` (mes vacío) | — | Tests verdes |
| CA-16 | `DashboardViewModelTest` (caché sin red, `isStale`, sin caché → `Error`, reintento); manual en modo avión | Emulador | Tests verdes; pasos manuales |
| CA-17 | `DashboardViewModelTest` (estado en el ViewModel) + manual (girar, segundo plano, cambiar de pestaña, terminar el proceso con `adb shell am kill`) | Emulador | Pasos manuales |
| CA-18 | `MainViewModelTest` (`activeFamilyInstance` cambia con la familia, también al volver a la anterior) + manual | Cuenta con dos familias | Tests verdes; manual si hay dos familias |

**Comprobaciones de regresión:** tests existentes de `MainScreenTest`, `MainViewModelTest`,
`MovementFormViewModelTest`, `MovementsViewModelTest`, `CategoriesContentTest`, `CategoryEditViewModelTest` y
`CategoriesLocalDataSourceTest` (una caché antigua sin `color` se sigue leyendo). Manual: el "+" de Inicio y
de Movimientos sigue abriendo un alta vacía; "Medios de pago" sigue funcionando.

**Comandos verificados para compilar y ejecutar tests:** los de `AGENTS.md`: `./gradlew :app:assembleDebug`,
`./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug` y
`./gradlew :app:connectedDebugAndroidTest`.

**Pruebas en dispositivo, emulador o simulador:** emulador con el backend local (`DEV_TARGET` configurado):
CA-02, CA-05, CA-12, CA-13, CA-14 contra el backend, CA-16 en modo avión, CA-17 con giro, segundo plano y
proceso terminado, modo oscuro y texto grande (200 %). No hace falta hardware real.

**Limitaciones del entorno:** CA-18 de extremo a extremo y el rol Integrante de CA-14 requieren una segunda
familia o cuenta; si no la hay, quedan cubiertos solo por tests y se registran como no verificados. CA-08 en
un día real 31 solo se prueba con reloj fijo. TalkBack con voz queda pendiente si no se puede usar en el
emulador.

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. **Color de categoría en datos:** `Category.color`, `ColorUpdate`, red y caché, con sus tests. Comprobación:
   tests de `data/categories` verdes, caché antigua legible.
2. **Paleta:** valores claro/oscuro en `ui/theme/Color.kt`, `CategoryColors` con respaldo y `docs/DESIGN.md`.
   Comprobación: `CategoryColorsTest` y contraste revisado.
3. **Color en la gestión:** selector en la edición e icono coloreado en el listado. Comprobación: tests de
   `ui/categories` verdes y prueba manual contra el backend (CA-14).
4. **Cálculo del dashboard:** `DashboardCalculator` con `DashboardCalculatorTest`. Sin dependencias de UI.
5. **`DashboardViewModel`** con sus tests (depende de 4).
6. **Interfaz del dashboard:** `DashboardContent`, gráfico y textos, con `DashboardContentTest` (depende de 2
   y 5).
7. **Integración en Inicio:** `HomeContent`, `MainScreen`, `MainViewModel` (`activeFamilyInstance`,
   `openMovementCreate` con categoría), `MovementRoute.Create` y formulario con categoría inicial; tests de
   `ui/main` y del formulario (depende de 6).
8. **Cierre:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` y
   `connectedDebugAndroidTest`, pruebas manuales, `AGENTS.md`, `ROADMAP.md` y evidencias en `TASKS.md`.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - *Totales y cuadrícula que no suman igual:* un movimiento cuya categoría no está en la lista (lista de
    categorías desactualizada) cuenta en los totales pero no aparece en la cuadrícula. Es coherente con la
    regla del backend para `/dashboard` y no puede pasar con datos al día (una categoría con movimientos no
    se puede eliminar). Se cubre con un test y no se muestra nada especial.
  - *Una petición más al cambiar de mes:* se pide también el mes anterior. Es un listado de un mes sin
    paginación (BG-01 de la 007) y queda en caché para la comparación siguiente.
  - *Cambio de `MovementRoute.Create` a clase:* afecta a las comparaciones `route == MovementRoute.Create` de
    `MainScreen` y `MainViewModel` y a sus tests; se cambian en la etapa 7 y la regresión del "+" lo cubre.
  - *Contraste y distinción de la paleta:* el contraste de los 16 colores ya está calculado (todos ≥ 4:1). Con
    16 tonos algunos pares se parecen (p. ej. Salvia y Turquesa); el orden del respaldo los separa y el
    gráfico nunca depende solo del color (nombres y montos en las tarjetas, descripción textual). En la etapa
    2 se revisan en el emulador en claro y oscuro y se ajusta un valor si dos colores vecinos no se
    distinguen.
  - *Clave del dashboard por instancia de familia:* deja ViewModels antiguos en el `ViewModelStore` hasta
    terminar la sesión, igual que ya ocurre con los formularios de movimiento; su coste es despreciable.
- **Decisiones pendientes:** Ninguna. La paleta de 16 colores quedó confirmada el 2026-11-19.
