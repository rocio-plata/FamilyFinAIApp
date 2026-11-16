# PLAN: Movimientos

**SPEC de referencia:** `docs/features/007-financial-items/SPEC.md`
**Versión de la spec revisada:** Aprobada el 2026-11-15 (17 requisitos, RF-01 a RF-17; 26 criterios, CA-01 a CA-26)
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
| `MainViewModel` / `MainUiState` / `ManagementRoute` | `ui/main/` | Familia activa en memoria y una ruta a pantalla completa (`managementRoute`: `CategoriesRoute`, `PaymentMethodsRoute`) ligada a la familia, que oculta cabecera y pestañas e impide cambiar de familia. Se amplía con las rutas de movimientos y con la pestaña seleccionada. |
| `MainScreen` / `MainScreenContent` | `ui/main/MainScreen.kt` | La pestaña seleccionada es un `remember` local (se pierde al recrear, RF-08 de la 001). `HomeFab` solo se muestra en Inicio y su `onClick` está vacío. Las pestañas que no son Inicio muestran `TabPlaceholder`. |
| `Family` / `HeaderRemoteDataSource` / `HeaderLocalDataSource` | `domain/header/`, `data/header/` | `Family(id, name, role)`. `GET /me/families` ya devuelve `defaultCurrency`, que la app no lee (BG-10). Caché `header_cache` con `schemaVersion = 1`. |
| `CategoriesRepository` | `domain/categories/`, `data/categories/` | `categories(familyId): Flow<CategoriesData>` con tipo, icono, estado y tags (con estado y orden) y `refresh`. Fuente de la cuadrícula del formulario y de los nombres e iconos del listado. Se reutiliza sin cambios. |
| `PaymentMethodsRepository` | `domain/paymentmethods/`, `data/paymentmethods/` | `paymentMethods(familyId): Flow<PaymentMethodsData>` (activos y archivados) y `refresh`. Fuente del selector y de los nombres. Se reutiliza sin cambios. |
| `PaymentMethodsRepositoryImpl` + data sources | `data/paymentmethods/` | Patrón de referencia: `RemoteResult`, fallos tipados, caché por familia en DataStore con `schemaVersion`, `userId` y `savedAt`, candado de escritura, `clear()` con contador de época, `Offline` sin llamar al backend. |
| `CachePolicy` | `data/cache/CachePolicy.kt` | `isStale` (24 h) e `isExpired` (7 días), comunes a todas las cachés. |
| `SessionProvider` / `SessionManager` | `session/` | `current().userId`: valida las cachés y decide "Tú" (RF-15). `onSessionEnded` en `AppContainer` vacía las cachés. |
| `ConnectivityObserver` | `data/network/` | `isOnline`: refresco al recuperar red y `Offline` antes de enviar (RF-14). |
| `CategoryIcons` | `ui/categories/CategoryIcons.kt` | `displayOf(key)`: icono de una categoría o el genérico. |
| `avatarInitial` | `domain/header/AvatarInitial.kt` | Primera letra del nombre; base para las iniciales de quien registró (RF-15). |
| `StaleDataBanner` | `ui/main/StaleDataBanner.kt` | Aviso de datos antiguos, sin estado (RF-13). |
| `Income` / `Expense` y sus contenedores | `ui/theme/Color.kt` | Colores semánticos de ingreso y gasto, ya en el tema. |
| `AppContainer` | `AppContainer.kt` | DI manual: cliente de API con `SessionAuth`, DataStores y repositorios. |
| `backup_rules.xml` / `data_extraction_rules.xml` | `app/src/main/res/xml/` | Excluyen cada caché de las copias de seguridad. |
| `TestMainHost` | `androidTest/.../ui/main/TestMainHost.kt` | Reproduce lo mínimo de `MainViewModel` sobre la sobrecarga sin estado de `MainScreen`. |

No existe nada de movimientos, de miembros de la familia ni de dinero en el
código. minSdk 34: `java.time`, `java.util.Currency` y `java.text.NumberFormat`
están disponibles sin dependencias.

**Convenciones y patrón de referencia:** las de `AGENTS.md` y la funcionalidad
006: capas `domain` / `data` / `ui` por funcionalidad, repositorio con `Flow` y
resultados tipados, ViewModel con `StateFlow`, pantalla con entrada con estado
y contenido sin estado, DI manual, tests JVM con `ktor-client-mock` y de UI con
un host mínimo.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de datos y eventos hasta la interfaz. Usa un diagrama si aporta claridad. -->

Tres piezas nuevas (movimientos, miembros y dinero) con el patrón de la 006, y
tres cambios a lo existente (moneda de la familia, pestaña seleccionada y
rutas a pantalla completa).

```
Pestaña Movimientos ── MovementsViewModel ──┐
Detalle ───────────── MovementDetailViewModel ─┤── MovementsRepository ── remoto (items) + caché por mes
Formulario ────────── MovementFormViewModel ──┘      │
        │  categorías, medios de pago, miembros      └─ recuerda el último medio de pago
        └── CategoriesRepository · PaymentMethodsRepository · FamilyMembersRepository
```

- **Movimientos por mes.** El repositorio expone
  `movements(familyId, month): Flow<MovementsData>` y `refresh(familyId, month)`.
  Pide siempre `from` y `to` juntos (BG-01, BG-02) con los límites del mes en
  la zona horaria del dispositivo, y ordena él mismo: fecha descendente y,
  dentro del día, `createdAt` descendente (RF-02). Crear, editar, reclasificar
  y eliminar son `suspend`, devuelven éxito o un `MovementFailure` y, si tienen
  éxito, aplican el cambio a los meses que estén en memoria y en caché, de modo
  que listado, resumen y detalle se actualizan sin recargar.
- **Detalle sin endpoint propio (BG-06).** El detalle observa
  `movement(familyId, movementId)`, que busca el movimiento en los meses
  cargados. Siempre se abre desde el listado, así que su mes está en memoria;
  si desaparece (eliminado o refresco que ya no lo trae), el detalle se cierra.
- **Edición en un formulario (RF-09, RF-10).** El ViewModel compara lo editado
  con el movimiento original y llama solo a lo que cambió: `PATCH …/items/:id`
  con los campos modificados y `PATCH …/category` si cambió la categoría o la
  etiqueta. Primero los datos y después la categoría. El resultado distingue
  éxito, fallo y parcial (una parte aplicada y la otra no) para poder informar
  de qué se guardó.
- **Fecha, solo el día (BG-09).** En dominio la fecha es un `LocalDate`. Al
  enviar: si es hoy, el instante actual; si es un día pasado, las 12:00 de ese
  día en la zona del dispositivo. Nunca es un instante futuro. Al leer, el
  instante se convierte al día en la zona del dispositivo. Al editar, la fecha
  solo se envía si el día cambió, para no alterar el instante guardado.
- **Dinero.** `Money(amountMinor: Long, currency: String)` en dominio; nunca
  `Double`. El campo de monto solo admite dígitos y, si la moneda tiene
  decimales, un separador y hasta sus dígitos fraccionarios
  (`Currency.getDefaultFractionDigits()`); el formato de salida usa
  `NumberFormat` con la configuración regional. La parte entera del monto
  admite hasta 12 dígitos. Resumen y subtotales se suman en `Long`.
- **Quién registró (RF-15).** `FamilyMembersRepository` descarga
  `GET /families/:id/members` (`userId`, `displayName`, `role`; el correo se
  ignora), con su caché. La interfaz resuelve `recordedBy` contra esa lista y
  contra el `userId` de la sesión.
- **Resumen y subtotales (RF-17).** Los calcula `MovementsViewModel` con los
  movimientos del mes: el resumen con todos; los grupos por día, con los que
  pasan el filtro de tipo.
- **Navegación.** El formulario y el detalle ocupan toda la pantalla, así que
  se añaden como rutas de `managementRoute`
  (`MovementRoute.Create`, `Detail(id)`, `Edit(id)`). La pestaña seleccionada
  pasa de `remember` a `MainViewModel` para que sobreviva a la recreación
  (cambio acordado de RF-08 de la 001) y para volver a la pestaña de origen al
  cerrar el formulario. El botón "+" se muestra en Inicio y en Movimientos.
- **Selector de mes.** Flechas anterior y siguiente con el nombre del mes; la
  de siguiente se deshabilita en el mes en curso (decisión confirmada).

**Sin extracción de código común**, como en la 006: el envío HTTP y la lectura
de `error` se repiten en los data sources nuevos. Extraerlos sería un refactor
de 005 y 006 ajeno a este alcance.

## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

| Módulo | Existe / nuevo | Responsabilidad y cambios | Dependencias afectadas |
| --- | --- | --- | --- |
| No aplica | — | El proyecto tiene un único módulo `app/`; se trabaja en sus paquetes por capa y funcionalidad. | — |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son propuestas.
Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `domain/money/Money.kt`, `MoneyInput.kt` | Crear | `Money` en unidad menor; conversión entre texto del campo y unidad menor según los decimales de la moneda; suma | RF-05, RF-17 |
| `domain/movements/Movement.kt` | Crear | `Movement` (tipo, monto, concepto, categoría, etiqueta, medio de pago, día, observaciones, `recordedBy`, `createdAt`), `MovementsData` (lista ordenada + `isStale`), `YearMonth` como mes | RF-02, RF-08 |
| `domain/movements/MovementFailure.kt` | Crear | Fallos tipados y `MovementResult`; resultado de edición con éxito, fallo o parcial | RF-10, RF-14 |
| `domain/movements/MovementValidation.kt` | Crear | Reglas de monto, concepto (1 a 100), observaciones (hasta 500) y fecha no futura | RF-05, RF-16 |
| `domain/movements/MovementsRepository.kt` | Crear | Contrato: flujo por mes, flujo de un movimiento, refresco, crear, editar, reclasificar, eliminar, último medio de pago usado y `clear()` | RF-01 a RF-14 |
| `domain/members/FamilyMember.kt`, `FamilyMembersRepository.kt` | Crear | `FamilyMember(userId, displayName?, role)`; flujo por familia, `refresh` y `clear()` | RF-15 |
| `domain/header/Family.kt` | Modificar | Añade `currency: String?` | RF-05 |
| `data/header/HeaderRemoteDataSource.kt`, `HeaderLocalDataSource.kt` | Modificar | Leen `defaultCurrency`; en la caché es un campo opcional (sin cambiar `schemaVersion`: una copia anterior queda sin moneda hasta el siguiente refresco) | RF-05 |
| `data/movements/MovementsRemoteDataSource.kt` | Crear | Llamadas de §3.4.3, conversión de fechas y traducción de errores con ambos prefijos (BG-07) | RF-01 a RF-14 |
| `data/movements/MovementsLocalDataSource.kt` | Crear | Caché `movements_cache`: un JSON por familia y mes; preferencia `último medio de pago` por usuario y familia | RF-07, RF-13 |
| `data/movements/MovementsRepositoryImpl.kt` | Crear | Orquesta remoto, caché y memoria por mes; aplica los cambios a los meses cargados | RF-01 a RF-14 |
| `data/members/FamilyMembersRemoteDataSource.kt`, `FamilyMembersLocalDataSource.kt`, `FamilyMembersRepositoryImpl.kt` | Crear | Lista de miembros con caché `members_cache` por familia | RF-13, RF-15 |
| `AppContainer.kt` | Modificar | DataStores y repositorios nuevos; `onSessionEnded` los vacía | RF-07, RF-13 |
| `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Modificar | Excluyen `movements_cache` y `members_cache` | RF-13 |
| `ui/main/ManagementRoute.kt`, `MovementRoute.kt` (nuevo) | Modificar / crear | Rutas `Create`, `Detail(id)` y `Edit(id)` | RF-04, RF-08, RF-09 |
| `ui/main/MainViewModel.kt`, `MainUiState.kt` | Modificar | Pestaña seleccionada en el estado; abrir y cerrar las rutas de movimientos (atrás desde `Edit` vuelve a `Detail`; desde `Create` y `Detail`, a las pestañas) | RF-04, RF-08, CA-20 |
| `ui/main/MainScreen.kt`, `HomeContent.kt` | Modificar | "+" en Inicio y Movimientos; la pestaña Movimientos muestra el listado; cablea las rutas nuevas | RF-01, RF-04 |
| `ui/movements/MovementsViewModel.kt`, `MovementsUiState.kt` | Crear | Mes, filtro de tipo, grupos por día con subtotal, resumen, refresco y estados | RF-01 a RF-03, RF-13, RF-15, RF-17 |
| `ui/movements/MovementsScreen.kt`, `MovementsContent.kt` | Crear | Listado: resumen, selector de mes, filtro, grupos por día, filas, vacío, carga, error y aviso | RF-01 a RF-03, RF-17 |
| `ui/movements/MovementDetailViewModel.kt`, `MovementDetailScreen.kt`, `MovementDetailContent.kt` | Crear | Detalle, eliminar con confirmación y avisos | RF-08, RF-11, RF-15 |
| `ui/movements/MovementFormViewModel.kt`, `MovementFormUiState.kt` | Crear | Estado del alta y la edición: campos, errores por campo, opciones, guardado, descarte | RF-05 a RF-07, RF-09, RF-10, RF-16 |
| `ui/movements/MovementFormScreen.kt`, `MovementFormContent.kt` | Crear | Formulario del mockup: tipo, monto, concepto, cuadrícula, etiquetas, medio de pago, fecha y observaciones | RF-05 a RF-07, RF-16 |
| `ui/movements/MovementTexts.kt`, `MoneyFormat.kt`, `MemberLabel.kt` | Crear | Traducción de fallos, formato de dinero y fechas, y resolución de quien registró | RF-02, RF-15 |
| `res/values/strings.xml` | Modificar | Textos `movements_*` | Todos |
| `docs/features/001-scaffold/SPEC.md` | Modificar | RF-08 y CA-05: la pestaña se conserva al recrear | CA-20 |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Modelos y contratos de entrada y salida:** contrato §3.4.3.
  `GET …/items?from&to` → lista con `id`, `recordedBy`, `paymentMethodId`
  (puede ser `null`, BG-04), `type`, `amount {amount, currency}`, `categoryId`,
  `tagId`, `title`, `note`, `occurredOn` y `createdAt`. `POST …/items` envía
  `amount`, `currency`, `categoryId`, `paymentMethodId`, `tagId?`, `title`,
  `note?` y `occurredOn` (siempre con medio de pago: RF-07). `PATCH …/items/:id`
  envía solo lo modificado (`note: null` borra las observaciones).
  `PATCH …/category` envía `newCategoryId` y `newTagId` (o `null`).
  `DELETE …/items/:id` → `204`. `GET …/members` → `userId`, `displayName`,
  `role`. La respuesta del alta no trae `recordedBy` ni `createdAt`: se
  completan con el usuario de la sesión y el instante local.
- **Identificadores, relaciones y restricciones:** UUID como `String`. Un
  movimiento referencia categoría, etiqueta y medio de pago por id; nombres e
  iconos se resuelven en la interfaz con los repositorios de 005 y 006, que
  incluyen los archivados (CA-10). Un id que no se encuentra se muestra como
  no disponible, sin romper la fila.
- **Origen de los datos mostrados y transformaciones:** el listado combina
  movimientos del mes, categorías, medios de pago, miembros y sesión. Fechas:
  ver "Fecha, solo el día". Mes pedido: desde el primer instante del día 1
  hasta el último del mes, en la zona del dispositivo, enviados en UTC.
- **Persistencia, consultas y actualizaciones:** DataStore Preferences.
  `movements_cache`: una clave por familia y mes con `schemaVersion = 1`,
  `userId`, `savedAt` y la lista. `members_cache`: una clave por familia. El
  último medio de pago usado se guarda en `movements_cache` con una clave por
  usuario y familia. Tras cada cambio con éxito se actualizan memoria y caché
  de los meses cargados, conservando su `savedAt`; si una edición mueve el
  movimiento a un mes no cargado, sale del mes actual y aparecerá al consultar
  el otro. Todo se borra al terminar la sesión.
- **Convivencia entre datos locales y remotos:** el backend manda; la caché
  solo sirve para leer (RF-13). No se guarda ningún cambio pendiente (RF-14).
  Regla de `CachePolicy`: descarte a los 7 días o si es de otro usuario,
  `isStale` desde 24 h.
- **Compatibilidad y migraciones de datos existentes:** cachés nuevas con
  `schemaVersion = 1`. En `header_cache` la moneda es un campo opcional con
  valor por defecto, compatible con las copias ya guardadas.

Traducción de errores (`error` del cuerpo). `*.X` significa
`FINANCIAL_TRACKING.X` o `FINANCIAL.X` (BG-07):

| Respuesta | `MovementFailure` | Efecto en la interfaz |
| --- | --- | --- |
| `*.FUTURE_TRANSACTION_DATE` | `FutureDate` | Aviso junto a la fecha (no debería ocurrir: la app lo impide) |
| `*.CATEGORY_NOT_ACTIVE`, `*.CATEGORY_NOT_FOUND` | `CategoryUnavailable` | Aviso, se quita la categoría elegida y se refrescan las categorías (CA-22) |
| `*.TAG_NOT_ACTIVE`, `*.TAG_DOES_NOT_BELONG_TO_CATEGORY` | `TagUnavailable` | Aviso, se quita la etiqueta y se refrescan las categorías (CA-22) |
| `*.PAYMENT_METHOD_NOT_ACTIVE`, `*.PAYMENT_METHOD_NOT_FOUND`, `*.NO_DEFAULT_PAYMENT_METHOD_SET` | `PaymentMethodUnavailable` | Aviso, se quita el medio elegido y se refrescan los medios (CA-22) |
| `*.CANNOT_RECLASSIFY_ACROSS_TYPES` | `TypeMismatch` | Aviso y refresco de categorías (la app solo ofrece el mismo tipo) |
| `SHARED.CURRENCY_MISMATCH` | `CurrencyMismatch` | Aviso y refresco de la cabecera (la moneda de la familia cambió) |
| `*.FINANCIAL_ITEM_NOT_FOUND` o `404` en un movimiento | `NotFound` | Aviso, cierre del formulario o del detalle y refresco del mes (CA-22) |
| `400` de validación u otros de título u observaciones | `InvalidData` | Mensaje genérico de datos no válidos |
| `403` (`FAMILY.NOT_A_MEMBER`, `*INSUFFICIENT_ROLE`) | `NotAllowed` | Aviso y `MainViewModel.onInsufficientRole()` |
| `401` tras la renovación | `Unauthorized` | Lo resuelve la sesión (003) |
| `IOException`, incluida la espera excesiva | `Network` | Error con reintento |
| Sin conexión antes de enviar | `Offline` | "Requiere conexión" (RF-14) |
| Resto | `Unknown` | Error genérico |

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de MOBILE_GUIDELINES.md. -->

- **Gestión del estado de interfaz y navegación:** `MovementsUiState`:
  `Loading`, `Error` y `Content` (mes, puede avanzar, filtro, resumen, grupos
  por día con subtotal, vacío, `isStale`, refrescando). El detalle: movimiento
  resuelto, confirmación de eliminar, ocupado y aviso. El formulario: modo
  (alta o edición), campos, errores por campo, opciones, guardando, descarte y
  aviso. `MainViewModel` guarda la pestaña y la ruta; `BackHandler`, la "X" y
  la flecha pasan por el cierre de la ruta.
- **Conservación y restauración del estado:** todo vive en ViewModels, que
  sobreviven a la recreación y al segundo plano (CA-20): pestaña, mes, filtro,
  ruta abierta y campos del formulario. `MovementsViewModel` se crea con clave
  `sesión-familia`; los del detalle y el formulario, con la ruta. No se usa
  `SavedStateHandle`: tras terminar el proceso la app abre en Inicio, como
  pide la spec.
- **Ejecución, concurrencia y cancelación de operaciones:** todo en
  `viewModelScope`. Un refresco a la vez por familia y mes; cambiar de mes no
  cancela el anterior, pero la interfaz solo observa el mes mostrado. Una sola
  operación de cambio a la vez por ViewModel, con el indicador fijado antes de
  lanzar la corrutina (CA-18). El repositorio serializa las escrituras de caché
  sin mantener el candado durante la red; `clear()` usa el esquema de época
  para no esperar a un refresco en curso.
- **Errores, reintentos y prevención de duplicados:** tabla anterior. Ningún
  fallo borra lo escrito. Un alta que falla por red no se reintenta sola: no
  se sabe si el backend la registró, así que se informa y se refresca el mes
  al recuperar la conexión para que el usuario lo compruebe antes de repetir.
  Edición parcial: solo puede darse cuando cambian a la vez los datos y la
  categoría. Si los datos se guardan y el cambio de categoría falla, se
  informa de lo guardado, el formulario sigue abierto y un nuevo "Guardar"
  solo envía lo pendiente. Si fallan los datos, no se intenta la categoría.
- **Otras consideraciones mobile aplicables y su solución:** formulario
  desplazable con `imePadding` y el botón de guardar al final del contenido;
  teclado numérico o decimal según la moneda; límites en los campos; `LazyColumn`
  para el listado con claves estables; cuadrícula de categorías que muestra
  todas las activas del tipo y crece en filas; selector de fecha de Material 3
  con los días futuros deshabilitados; gasto e ingreso con signo además del
  color; áreas táctiles ≥48 dp; descripciones para lector de pantalla en filas,
  resumen y flechas de mes; sin montos ni conceptos en logs; colores solo del
  tema.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

- Sin dependencias nuevas: Ktor, kotlinx.serialization, DataStore, Compose y
  Material 3 ya están en `gradle/libs.versions.toml`; fechas y dinero usan
  `java.time`, `java.util.Currency` y `java.text.NumberFormat` (minSdk 34).
- Sin permisos nuevos.
- Dos DataStore nuevos (`movements_cache`, `members_cache`), excluidos de las
  copias de seguridad.
- Entorno de pruebas manuales: backend local y credenciales `DEV_*` de
  `local.properties`, como en la 006.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, UI o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | JVM: `MovementsViewModelTest` (mes inicial, anterior, sin avanzar del actual), `MovementsRepositoryImplTest` (rango pedido). UI: `MovementsContentTest` | JVM y emulador | Resultados de tests |
| CA-02 | JVM: orden y grupos en el ViewModel. UI: filas, signo y descripción del tipo | JVM y emulador | Tests |
| CA-03 | JVM: filtro en el ViewModel y persistencia al cambiar de mes. UI: chips | JVM y emulador | Tests |
| CA-04 | JVM: `MainViewModelTest` (abrir alta). UI: `MainScreenTest` ("+" en Inicio y Movimientos, ausente en el resto) | JVM y emulador | Tests |
| CA-05 | JVM: `MovementFormViewModelTest`, repositorio (aparece en su mes). Manual contra backend | JVM, emulador y backend local | Tests + pasos manuales |
| CA-06 | JVM: `MovementValidationTest`, `MoneyInputTest`, ViewModel (sin llamada). UI: avisos y límites | JVM y emulador | Tests |
| CA-07 | JVM: opciones por tipo y limpieza de dependientes. UI: cuadrícula y etiquetas | JVM y emulador | Tests |
| CA-08 | JVM: repositorio y `MovementsLocalDataSourceTest` (último medio, archivado, por usuario y familia) | JVM | Tests |
| CA-09 | JVM: accesos rápidos y rechazo de futuro en el ViewModel. UI: chips y selector | JVM y emulador | Tests |
| CA-10 | JVM: `MovementDetailViewModelTest`. UI: `MovementDetailContentTest` (archivados indicados) | JVM y emulador | Tests |
| CA-11 | JVM: solo se envía lo modificado; cambio de mes. Manual | JVM, emulador y backend local | Tests + pasos manuales |
| CA-12 | JVM: edición con medio archivado sin enviarlo; al cambiar solo activos | JVM | Tests |
| CA-13 | JVM: categorías del mismo tipo, dos llamadas y resultado parcial. UI: aviso de parcial | JVM y emulador | Tests |
| CA-14 | JVM: eliminar, cancelar y cierre del detalle. UI: diálogo. Manual | JVM, emulador y backend local | Tests + pasos manuales |
| CA-15 | Revisión: ninguna pantalla recibe el rol. Manual solo si se dispone de una cuenta Integrante | Emulador y backend | Revisión + manual o no verificado |
| CA-16 | JVM: repositorio y caché (por mes, antigua, sin copia). Manual en modo avión | JVM y emulador | Tests + pasos manuales |
| CA-17 | JVM: `Offline` sin llamada y datos conservados. Manual en modo avión | JVM y emulador | Tests + pasos manuales |
| CA-18 | JVM: dos pulsaciones → una llamada en formulario y detalle | JVM | Tests |
| CA-19 | JVM: descarte en el ViewModel. UI: diálogo con atrás y "X" | JVM y emulador | Tests |
| CA-20 | UI: `StateRestorationTester` con formulario, mes y filtro; `MainScreenBehaviorTest` (pestaña conservada). Manual: segundo plano | Emulador | Tests + pasos manuales |
| CA-21 | JVM: `MainViewModelTest` y clave por familia. Manual con dos familias | JVM y emulador | Tests + pasos manuales |
| CA-22 | JVM: cada fallo de la tabla en formulario y detalle | JVM | Tests |
| CA-23 | JVM y UI: formulario sin categorías del tipo o sin medios | JVM y emulador | Tests |
| CA-24 | JVM: `MemberLabelTest` (Tú, nombre, ex-miembro, sin nombre, sin lista). UI: fila y detalle, sin correo | JVM y emulador | Tests |
| CA-25 | UI: formulario a pantalla completa con familia, moneda y "X" | Emulador | Tests |
| CA-26 | JVM: resumen, subtotales con filtro y tras cada cambio. UI: tarjeta y cabeceras | JVM y emulador | Tests |

**Comprobaciones de regresión:** suite JVM e instrumentada completas:
cabecera (moneda nueva y caché anterior), categorías, medios de pago, sesión
(`SessionEndDuring…`), `MainViewModelTest`, `MainScreenTest`,
`MainScreenBehaviorTest` y `AppRootTest`. Los tests que comprueban CA-05 de la
001 se actualizan a la regla nueva. Lint sin superar la línea base (24).

**Comandos verificados para compilar y ejecutar tests:**
`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`,
`./gradlew :app:testDebugUnitTest --tests "…"` y
`./gradlew :app:connectedDebugAndroidTest` (con
`-Pandroid.testInstrumentationRunnerArguments.package=…` para un paquete),
usados en la 006.

**Pruebas en dispositivo, emulador o simulador:** AVD `Medium_Phone_API_37.0`
con backend local: alta de gasto e ingreso, validaciones, edición (también de
categoría), eliminación, cambio de mes, filtro, resumen, dos familias, modo
avión en consulta y en cada acción, segundo plano con el formulario abierto,
teclado sin tapar el campo ni "Guardar", tamaño de fuente máximo y TalkBack.
Solo se crean, editan y eliminan movimientos de prueba propios. No hace falta
hardware real.

**Limitaciones del entorno:** CA-15 y el caso de otro miembro de CA-24
necesitan una segunda cuenta en la misma familia; "Ex-miembro" necesita un
miembro que haya salido. Si no se pueden preparar, quedan cubiertos por tests
y anotados como no verificados de extremo a extremo. La zona horaria (BG-09)
se cubre con tests JVM con relojes y zonas fijos, no en el emulador.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. Dominio: dinero, movimiento, validación, fallos y contratos de los
   repositorios. Comprobación: tests JVM.
2. Moneda de la familia en la cabecera. Comprobación: tests de cabecera y
   regresión.
3. Datos de miembros y de movimientos (remoto, caché, repositorio) y DI.
   Comprobación: tests JVM con `ktor-client-mock` y de fin de sesión.
4. Navegación: pestaña en `MainViewModel`, rutas de movimientos y "+".
   Actualiza RF-08 de la 001. Comprobación: `MainViewModelTest` y tests de UI
   de `ui.main`.
5. Listado: ViewModel y pantalla (mes, filtro, resumen, grupos, estados).
   Comprobación: tests JVM y de UI.
6. Formulario de alta: ViewModel y pantalla. Comprobación: tests JVM y de UI.
7. Detalle y eliminación. Comprobación: tests JVM y de UI.
8. Edición con el mismo formulario, incluida la categoría y el resultado
   parcial. Comprobación: tests JVM y de UI.
9. Validación completa (suite y lint), pruebas manuales contra el backend y
   documentación (`AGENTS.md`, `ROADMAP.md`, `BACKEND_GAPS.md`).

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - *Zona horaria (BG-09).* Un movimiento de "hoy" registrado cerca de la
    medianoche puede verse en el día vecino desde un dispositivo en otra zona
    horaria. Se acepta: la familia suele compartir zona; se cubre con tests de
    conversión con zonas fijas.
  - *Mes con muchos movimientos (BG-01).* Sin paginación, un mes se descarga
    entero. Se acota por mes y la lista es perezosa; si en las pruebas resulta
    lento, se anota como brecha a pedir.
  - *Alta duplicada por red.* Si la respuesta de un alta se pierde, el usuario
    podría repetirla. Medida: no reintentar sola, avisar y refrescar el mes.
  - *Edición parcial.* Dos llamadas no son atómicas. Medida: resultado parcial
    explícito y reintento solo de lo pendiente (RF-10).
  - *Moneda ausente en una copia anterior de la cabecera.* Sin moneda no se
    puede guardar: el formulario lo indica y pide conexión, que de todos modos
    hace falta para guardar.
  - *Cambio de RF-08 de la 001.* Afecta a tests existentes de `ui.main`; se
    actualizan en la etapa 4 junto con la spec 001.
- **Decisiones pendientes:** Ninguna. Confirmadas con la persona: la pestaña
  se conserva al recrear (cambia RF-08 de la 001) y el mes se cambia con
  flechas. Aprobadas con el plan: el instante que se envía como
  fecha (ahora para hoy, 12:00 locales para días pasados), el tope de 12
  dígitos en la parte entera del monto y que una edición envíe primero los
  datos y después la categoría.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
