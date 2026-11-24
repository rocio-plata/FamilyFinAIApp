# PLAN: Gestión de familias

**SPEC de referencia:** `docs/features/009-family-management/SPEC.md`
**Versión de la spec revisada:** aprobada el 2026-11-22 (aún sin commit)
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
| `AppContainer` | `AppContainer.kt` | DI manual. Se añaden los dos repositorios nuevos y la conexión que borra las cachés de familias perdidas (RF-23). `onSessionEnded` no cambia: lo nuevo no guarda nada en disco. |
| `MainViewModel` / `MainUiState` / `ManagementRoute` | `ui/main/` | Familia activa en memoria (`activeFamilyId` nulo = la primera), selector, pestaña y una única ruta de gestión ligada a la familia con la que se abrió. Se amplía con rutas sin familia, reordenado, avisos y cambio de familia desde una invitación. |
| `MainHeader`, `FamilySelectorSheet`, `MainScreen` | `ui/main/` | La cabecera solo es pulsable si `hasSelector` (más de una familia); la campana no es pulsable; `MainScreen` no tiene `SnackbarHost`; con `FamiliesState.NoData` las pestañas se dibujan sin familia. |
| Cabecera | `domain/header/`, `data/header/` | `HeaderRepository.header` es la única fuente de las familias, su rol y moneda, y del contador de invitaciones. `refresh()` sustituye la lista al terminar: hoy no hay cambios locales que pueda pisar, a partir de ahora sí. `Family` no tiene número de miembros, que el backend ya envía (`memberCount`). |
| Miembros | `domain/members/`, `data/members/` | Lista por familia con caché `members_cache`; no lee el correo. Solo lee. Se amplía para aplicar cambios y para pedir los correos sin guardarlos. |
| Cachés por familia | `data/categories/`, `data/paymentmethods/`, `data/movements/`, `data/members/` | Cada repositorio tiene memoria por familia, candado propio, `clear()` con contador de época y un origen local con claves `<prefijo><familia>` (movimientos: `movements_<familia>_<mes>` y `last_payment_method_<usuario>_<familia>`). Se les añade olvidar las familias que ya no están (RF-23). |
| Pantallas con moneda | `ui/dashboard/`, `ui/movements/` | Reciben `currency` al crear su ViewModel, con una clave que no la incluye: un cambio de moneda no les llegaría (RF-13). |
| Reordenado de tags | `ui/categories/TagList.kt` | Arrastre propio sin librería, con `dragTargetIndex` y `dragShift` (internas, probadas en `TagDragTest`) y acciones de accesibilidad subir/bajar. Patrón y cálculo se reutilizan (RF-02). |
| Medios de pago | `ui/paymentmethods/` | Patrón de referencia de ViewModel con formulario en diálogo, acción pendiente de confirmar, aviso y una sola operación a la vez. |
| `initialsOf`, `FamilyRole.labelRes` | `ui/movements/MemberLabel.kt` | Iniciales y etiqueta de rol; se reutilizan. |
| `AuthValidation.validateEmail` | `domain/auth/AuthValidation.kt` | Formato del correo; se reutiliza (RF-14). |
| Plantillas de error | `data/*/…RemoteDataSource.kt` | Cada origen remoto traduce `error` a su fallo tipado, nunca lanza por red y relanza la cancelación. Mismo patrón. |
| Dependencias | `gradle/libs.versions.toml` | Compose + Material 3, iconos extendidos, Ktor, serialization, DataStore. Sin librería de navegación. Basta con lo que hay. |
| Tests de referencia | `app/src/test/…/ui/main/MainViewModelTest.kt`, `data/header/HeaderRepositoryImplTest.kt`, `data/members/*Test.kt`, `session/SessionEndDuring*Test.kt`, `ui/movements/Fakes.kt`; `app/src/androidTest/…/ui/main/{TestMainHost,MainScreenTest,MainScreenBehaviorTest}.kt` | Se amplían. `MainScreenTest.singleFamilyHasNoSelector` y el caso de `MainViewModelTest` que espera `hasSelector` falso comprueban la regla que la 009 cambia. |
| Contrato y backend | `docs/backend/api-contract.md` §3.3, §4, §9; `familyFinAI-backend/src/contexts/family-access` (`main`, `5c1860d`, leído; no probado en ejecución) | Ver `BACKEND_GAPS.md`. `GET …/members` solo incluye `email` si quien pide es `OWNER`; `POST /invitations/:id/accept` responde `204` sin datos de la familia. |

**Convenciones y patrón de referencia:** los de `AGENTS.md`; patrón de la 006
(repositorio con resultado tipado, ViewModel con formulario y confirmaciones,
entrada con estado + contenido sin estado).

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de datos y eventos hasta la interfaz. Usa un diagrama si aporta claridad. -->

**Capas nuevas** (paquete `family` en cada capa):

```
ui/family      FamilyManagementScreen ─ FamilyManagementViewModel   («Mi familia», invitar)
               FamilyCreateScreen     ─ FamilyCreateViewModel
               InvitationsScreen      ─ InvitationsViewModel        (recibidas)
domain/family  FamilyManagementRepository, InvitationsRepository (interfaces), modelos,
               FamilyAccessFailure, FamilyNameValidation, InvitationValidation
data/family    FamilyManagementRepositoryImpl ┐
               InvitationsRepositoryImpl      ┴─ FamilyAccessRemoteDataSource (apiClient)
                        │ aplican el resultado a
                        ├─ HeaderRepository        (familias, rol, moneda, contador)
                        └─ FamilyMembersRepository (lista de miembros)
```

Ninguno de los dos repositorios guarda datos propios: las invitaciones y el
estado de la moneda viven en el ViewModel de su pantalla (RF-25). La cabecera y
los miembros siguen siendo las únicas fuentes de lo que ya mostraban.

**Cambios locales en la cabecera.** `HeaderRepository` gana operaciones para
aplicar un cambio ya confirmado por el backend, en memoria y caché y conservando
`savedAt`: añadir una familia al final, quitarla, fijar el orden, actualizar una
(rol, moneda, número de miembros) y fijar el contador de invitaciones. Un
refresco que empezó antes de un cambio traería datos anteriores y lo desharía
(por ejemplo, una familia recién creada desaparecería y se cerraría su
pantalla). Se evita con una versión por recurso (familias e invitaciones): el
refresco anota la versión al empezar y descarta ese recurso si cambió, igual que
ya hace con `clearEpoch`.

**Aceptar una invitación.** Como la respuesta no trae la familia, se añade con lo
que dice la invitación (nombre y rol, sin moneda ni número de miembros) y se
refresca la cabecera. Si el refresco falla, la familia está en la lista y, por
la regla existente, no se puede registrar un movimiento hasta conocer su moneda.

**Navegación.** Sigue sin librería. Rutas nuevas de `ManagementRoute`:

| Ruta | Se abre desde | Ligada a una familia | Atrás |
| --- | --- | --- | --- |
| `FamilyRoute.Manage` | Icono de la activa en el selector; tras crear | Sí | Pestaña de origen |
| `FamilyRoute.Create` | «Crear nueva familia»; estado sin familias | No | Pestaña de origen |
| `InvitationsRoute` | Campana; estado sin familias | No | Pestaña de origen |

La familia de la ruta abierta pasa a ser opcional: sin ella no se cierra cuando
cambian las familias y se muestra aunque no haya ninguna (RF-24). Al crear,
`MainViewModel` activa la familia nueva, selecciona Inicio y sustituye la ruta
por `Manage` (RF-05). «Cambiar a esta familia» cierra la ruta, activa la familia
y selecciona Inicio (RF-19). Si la familia de un `Manage` abierto desaparece, se
cierra y se avisa con «Ya no perteneces a …» (RF-22); el mismo aviso sirve al
salir (RF-12).

Invitar es un diálogo sobre «Mi familia» (como el formulario de medios de pago),
con desplazamiento para texto grande.

**Reordenar.** `MainViewModel` mantiene un orden provisional mientras se guarda:
se muestra al soltar y, si falla, se descarta y el selector muestra el error en
línea (un snackbar quedaría detrás de la hoja). Si nadie eligió familia, la
activa es «la primera»: antes de reordenar se fija la activa actual para que no
cambie (RF-02).

**Correos (RF-07).** `FamilyMember` sigue sin correo. «Mi familia» pide los
miembros con una variante del refresco que actualiza la lista como siempre y
devuelve además los correos, que solo quedan en el estado del ViewModel. Se
muestran si el usuario es Propietario y hay conexión.

**Moneda (RF-13).** El cambio actualiza `Family.currency` en la cabecera, y las
claves de los ViewModels de Inicio y Movimientos pasan a incluir la moneda, de
modo que se crean de nuevo con la correcta. La clave del formulario de
movimiento no cambia: no puede estar abierto a la vez que «Mi familia».

**Datos de familias perdidas (RF-23).** Cuando la lista de familias cambia con
éxito (refresco o cambio local), `HeaderRepositoryImpl` notifica las vigentes
con un callback, como `SessionManager.onSessionEnded`; `AppContainer` lo conecta
a los cuatro repositorios con caché por familia, que borran de memoria y disco
las que no estén. Se compara contra lo que haya en disco, así que también limpia
lo que quedara de una ejecución anterior. Cada repositorio recuerda el último
conjunto de familias vigentes que recibió y no guarda la descarga en curso de
una que no esté en él, sin afectar a las demás; `clear()` lo olvida. Se eligió
esto y no una época por familia porque esa no cubre la primera descarga de una
familia sin nada guardado, que no estaba en memoria ni en disco cuando se
olvidó.

## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

| Módulo | Existe / nuevo | Responsabilidad y cambios | Dependencias afectadas |
| --- | --- | --- | --- |
| No aplica: módulo único `app/` | Existente | Paquetes nuevos `family` por capa | Ninguna |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son propuestas.
Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `domain/family/FamilyInvitation.kt` | Crear | `SentInvitation` (id, correo, rol, vencimiento; vigente o vencida según el reloj) y `ReceivedInvitation` (id, familia, quién invita, rol, vencimiento). `toString()` sin correo ni nombres | RF-15, RF-16, RF-18 |
| `domain/family/FamilySettings.kt` | Crear | Moneda, bloqueada y motivos (`Movements`, `Budgets`) | RF-13 |
| `domain/family/FamilyAccessFailure.kt` | Crear | Fallos tipados y `FamilyAccessResult` | RF-11, RF-17, RF-21, RF-22, RF-25 |
| `domain/family/FamilyNameValidation.kt` | Crear | Recorte, vacío, 60, emojis (por rangos de puntos de código) y repetido entre las familias del usuario | RF-04 |
| `domain/family/InvitationValidation.kt` | Crear | Vacío, formato (`AuthValidation`), 255, ya invitado con invitación vigente | RF-14, RF-17 |
| `domain/family/FamilyManagementRepository.kt` | Crear | Crear, reordenar, ajustes, cambiar moneda, cambiar rol, quitar miembro, salir, invitaciones enviadas (listar, invitar, revocar) | RF-02, RF-05, RF-09 a RF-17 |
| `domain/family/InvitationsRepository.kt` | Crear | Recibidas, aceptar, rechazar | RF-18 a RF-21 |
| `data/family/FamilyAccessRemoteDataSource.kt` | Crear | Llamadas del contrato y traducción de errores | Los anteriores |
| `data/family/FamilyManagementRepositoryImpl.kt`, `InvitationsRepositoryImpl.kt` | Crear | Conexión, sesión y aplicación del resultado a cabecera y miembros | RF-22, RF-25 |
| `domain/header/Family.kt` | Modificar | `memberCount: Int? = null` | RF-01 |
| `domain/header/HeaderRepository.kt`, `data/header/HeaderRepositoryImpl.kt` | Modificar | Cambios locales, versión por recurso y callback de familias vigentes | RF-01, RF-02, RF-05, RF-19, RF-23 |
| `data/header/HeaderRemoteDataSource.kt`, `HeaderLocalDataSource.kt` | Modificar | Leer y guardar `memberCount` como campo opcional | RF-01 |
| `domain/members/FamilyMembersRepository.kt`, `data/members/*` | Modificar | Refresco que devuelve correos sin guardarlos; aplicar rol y baja; olvidar familias | RF-07, RF-09, RF-10, RF-23 |
| `domain/` y `data/` de `categories`, `paymentmethods`, `movements` | Modificar | Olvidar las familias que no están (interfaz, repositorio y origen local); sin otros cambios | RF-23 |
| `AppContainer.kt` | Modificar | Repositorios nuevos y conexión del callback | RF-23 |
| `ui/main/ManagementRoute.kt`, `FamilyRoute.kt` (nuevo) | Modificar / crear | `FamilyRoute` e `InvitationsRoute` | RF-03, RF-04, RF-18 |
| `ui/main/MainViewModel.kt`, `MainUiState.kt` | Modificar | Rutas sin familia, aperturas, creación, cambio de familia, reordenado, avisos; se elimina `hasSelector` | RF-01 a RF-05, RF-19, RF-22, RF-24 |
| `ui/main/FamilySelectorSheet.kt` | Modificar | Rol, número de miembros, asa y subir/bajar, gestión de la activa, «Crear nueva familia», error de orden | RF-01 a RF-03 |
| `ui/main/MainHeader.kt` | Modificar | Selector siempre pulsable y campana pulsable (≥48 dp) | RF-01, RF-18 |
| `ui/main/MainScreen.kt` | Modificar | Pantallas nuevas (también sin familia activa), `SnackbarHost`, claves con moneda, estado sin familias | RF-03, RF-13, RF-22, RF-24 |
| `ui/main/NoFamiliesContent.kt` | Crear | Mensaje con «Crear familia» y «Ver invitaciones»; sin «+» | RF-24 |
| `ui/common/DragReorder.kt` | Crear | `dragTargetIndex` y `dragShift`, movidas sin cambios desde `TagList.kt` para usarlas en las dos listas; `TagDragTest` se mueve con ellas | RF-02 |
| `ui/family/*` | Crear | Los tres ViewModels con su estado, entrada y contenido; diálogo de invitación; `FamilyTexts` (fallos, fechas, roles) | RF-04 a RF-21 |
| `res/values/strings.xml` | Modificar | Textos y plurales | Todos |
| `docs/backend/`, `AGENTS.md`, `docs/ROADMAP.md`, `README.md` | Modificar | Arquitectura, estado; el contrato solo si el backend publica BG-01 | — |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Modelos y contratos de entrada y salida:** los de §3.3 y, donde el contrato
  no detalla, los leídos en el backend (BG-06):

| Operación | Petición | Éxito |
| --- | --- | --- |
| Crear | `POST /families` `{name}` | `201` `{id, name, defaultCurrency}` |
| Reordenar | `PUT /me/families/order` `{orderedFamilyIds}` | `204` |
| Ajustes | `GET /families/:id/settings` | `200` `{defaultCurrency, currencyLocked, currencyLockReasons}` |
| Moneda | `PATCH /families/:id/settings/currency` `{newCurrency}` | `204` |
| Miembros | `GET /families/:id/members` | `200` lista; `email` opcional |
| Rol | `PATCH /families/:id/members/:userId/role` `{newRole}` | `204` |
| Quitar / salir | `DELETE /families/:id/members/:userId` (salir: el propio) | `204` |
| Enviadas | `GET /families/:id/invitations` (por defecto, pendientes) | `200` lista |
| Invitar | `POST /families/:id/invitations` `{email, role}` | `201` invitación |
| Revocar / quitar vencida | `DELETE /invitations/:id` | `204` |
| Recibidas | `GET /me/invitations` | `200` lista |
| Aceptar / rechazar | `POST /invitations/:id/accept` · `/decline` (sin cuerpo ni `Content-Type`) | `204` |

- **Identificadores, relaciones y restricciones:** familias, miembros e
  invitaciones por su `id`. Un rol, fecha o motivo de bloqueo desconocido
  invalida la respuesta (`InvalidData`), salvo un motivo de bloqueo nuevo, que
  se trata como bloqueo sin motivo conocido (texto genérico): nunca como
  «desbloqueada».
- **Origen de los datos mostrados y transformaciones:** nombre, rol, moneda y
  número de miembros, de la cabecera (el número, de la lista de miembros si está
  cargada). Los órdenes de miembros e invitaciones y el ocultado de vencidas
  (RF-16) se calculan en el ViewModel con el reloj inyectado.
- **Persistencia, consultas y actualizaciones:** sin almacenes nuevos. Se
  persiste solo lo que ya se persistía, más `memberCount` en `header_cache`.
- **Convivencia entre datos locales y remotos:** el backend manda; un cambio se
  aplica tras su confirmación. Contador de la campana: se fija con la lista
  recibida al abrir «Invitaciones» y tras cada respuesta.
- **Compatibilidad y migraciones de datos existentes:** `memberCount` opcional
  sin cambiar `schemaVersion` (como se hizo con `currency`): una copia antigua
  se lee sin él y el selector lo omite hasta el siguiente refresco.

Traducción de errores (`error` del cuerpo; `message` no se lee):

| Respuesta | `FamilyAccessFailure` | Efecto en la interfaz |
| --- | --- | --- |
| `FAMILY_ACCESS.INVALID_FAMILY_NAME` | `InvalidName` | Junto al campo |
| `FAMILY_ACCESS.INVALID_FAMILY_ORDER` | `InvalidOrder` | Orden anterior, aviso y refresco de la cabecera |
| `FAMILY_ACCESS.ALREADY_MEMBER` | `AlreadyMember` | Junto al campo del correo |
| `SHARED.INVALID_EMAIL` | `InvalidEmail` | Junto al campo del correo |
| `FAMILY_ACCESS.CANNOT_REMOVE_LAST_OWNER` | `LastOwner` | Aviso; nada cambia (RF-11) |
| `FAMILY_ACCESS.MEMBER_NOT_FOUND` | `MemberNotFound` | Aviso y refresco de miembros |
| `FAMILY_ACCESS.INVITATION_NOT_FOUND` / `_EXPIRED` / `_NOT_PENDING`, `FAMILY.INVITATION_NOT_FOR_USER` | `InvitationNotFound` / `InvitationExpired` / `InvitationNotPending` / `InvitationNotForUser` | Motivo y refresco de la lista (RF-21) |
| `FAMILY.CURRENCY_LOCKED` | `CurrencyLocked` | Aviso y refresco de ajustes (CA-17) |
| `FAMILY.INSUFFICIENT_ROLE` | `InsufficientRole` | Aviso y `onInsufficientRole()`: el rol nuevo deja la pantalla en solo lectura |
| `FAMILY.NOT_A_MEMBER`, `FAMILY_ACCESS.FAMILY_NOT_FOUND` | `NotAMember` | `onInsufficientRole()`: la familia desaparece, se cierra y se avisa |
| `401` tras la renovación | `Unauthorized` | Lo resuelve la sesión (003) |
| `IOException`, incluida la espera excesiva | `Network` | Error con reintento |
| Sin conexión antes de enviar | `Offline` | «Requiere conexión» (RF-25) |
| Cuerpo inesperado / resto (incluido el `500` de BG-04) | `InvalidData` / `Unknown` | Error genérico; en invitaciones, además, refresco |

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de MOBILE_GUIDELINES.md. -->

- **Gestión del estado de interfaz y navegación:** cada pantalla con su
  `StateFlow<UiState>`. «Mi familia» tiene tres secciones independientes
  (miembros, moneda, invitaciones enviadas), cada una con carga, contenido o
  error; moneda editable e invitaciones solo se piden si el usuario es
  Propietario, según el rol de la cabecera en cada momento (RF-08). «Salir»
  deshabilitado con su motivo: única familia, o único Propietario según la lista
  de miembros; un Propietario sin lista cargada lo ve deshabilitado con
  «Requiere conexión».
- **Conservación y restauración del estado:** rutas, formularios, texto y
  confirmación pendiente viven en ViewModels (CA-31). Claves:
  `main-<generación>-family-<familia>` para «Mi familia»; `Create` e
  `InvitationsRoute` por apertura (`managementRouteInstance`). Sin
  `SavedStateHandle`: tras terminar el proceso se abre Inicio.
- **Ejecución, concurrencia y cancelación de operaciones:** `viewModelScope`;
  una sola operación a la vez por pantalla, con el indicador fijado antes de
  lanzar la corrutina (CA-30). Los candados de cabecera y cachés no se mantienen
  durante la red. El callback de RF-23 se ejecuta fuera del candado de la
  cabecera y solo usa el de cada repositorio, que tampoco espera a la red: no
  puede reproducir el interbloqueo corregido en la 003.
- **Errores, reintentos y prevención de duplicados:** tabla anterior. Un alta
  (familia o invitación) que termina en `Network` puede haberse aplicado: el
  formulario queda pendiente de comprobar y el siguiente intento consulta antes
  (cabecera o invitaciones). Si ya está (familia con ese nombre, que la
  validación hacía único; invitación vigente a ese correo), se trata como éxito;
  si la consulta falla, no se envía. Tras un `Network` en salir, quitar, revocar,
  aceptar o rechazar, se refrescan los datos y no se reintenta solo.
- **Otras consideraciones mobile aplicables y su solución:** fechas con la zona
  y configuración regional del dispositivo; elipsis y filas que crecen con texto
  grande; acciones por miembro con `contentDescription` que incluye su nombre;
  roles y «Vencida» como texto; `imePadding` en los formularios; sin correos ni
  nombres en logs. Colores solo del tema; la disposición sigue los mockups 009 y
  el resto, `docs/DESIGN.md`.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

- Sin dependencias, permisos ni almacenes nuevos. Las exclusiones de copia de
  seguridad no cambian.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, UI o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

Unitarios (JVM) en `app/src/test/…`, de UI en `app/src/androidTest/…` sobre
contenido sin estado. Abreviaturas: **FM** `FamilyManagementViewModelTest`,
**FC** `FamilyCreateViewModelTest`, **IV** `InvitationsViewModelTest`, **RD**
`FamilyAccessRemoteDataSourceTest`, **FR** / **IR**
`FamilyManagementRepositoryImplTest` / `InvitationsRepositoryImplTest` (nuevos);
**UI-M / UI-C / UI-I / UI-S** tests de contenido de «Mi familia», crear,
invitaciones y selector (nuevos); **MV** `MainViewModelTest`, **HR**
`HeaderRepositoryImplTest`, **MS** `MainScreenTest` (existentes, se amplían).

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | MV, MS (sustituye `singleFamilyHasNoSelector`), UI-S; `HeaderRemote/LocalDataSourceTest` (`memberCount`, copia antigua) | JVM / emulador | Tests + captura |
| CA-02 | MV (orden, activa fijada), HR, FR; UI-S (arrastre, subir/bajar, sin asa con una). Manual: reabrir la app | JVM / emulador; cuenta con 2+ familias | Tests + pasos manuales |
| CA-03 | MV (fallo, `InvalidOrder`), UI-S (error en línea) | JVM / emulador | Tests |
| CA-04 | MV, MS, UI-S | JVM / emulador | Tests |
| CA-05 | `FamilyNameValidationTest`, FC; UI-C (contador, límite, aviso) | JVM / emulador | Tests |
| CA-06 | FC, FR, HR (añadir al final), MV (activa, Inicio, `Manage`). Manual | JVM; backend local | Tests + captura |
| CA-07 | FM (orden, «Tú», sin nombre), UI-M | JVM / emulador | Tests |
| CA-08 | FM (Propietario con y sin red, Integrante), `FamilyMembersRepositoryImplTest` y `…LocalDataSourceTest` (lo guardado no contiene el correo). Manual: inspeccionar `members_cache` | JVM; emulador | Tests + inspección |
| CA-09 | FM, UI-M (sin acciones y sin pedir invitaciones ni ajustes). Manual con segunda cuenta | JVM / emulador; dos cuentas | Tests + captura |
| CA-10 | RD, FR, FM; UI-M (confirmar y cancelar). Manual | JVM / emulador; dos cuentas | Tests + captura |
| CA-11 | FR (rol en cabecera), FM (solo lectura). Manual | JVM; dos cuentas | Tests + captura |
| CA-12 | RD, FR, FM; UI-M. Manual: «Ex-miembro» en Movimientos | JVM / emulador; dos cuentas | Tests + captura |
| CA-13 | FM (no ofrecido; `LastOwner`), UI-M | JVM / emulador | Tests |
| CA-14 | FR (quita de cabecera), FM, MV (favorita y aviso). Manual: Propietario con otro Propietario; Integrante cuando exista BG-01 | JVM; dos cuentas | Tests + captura |
| CA-15 | FM, UI-M (cada motivo) | JVM / emulador | Tests |
| CA-16 | RD, FR, HR (moneda), MV/MS (claves). Manual: familia nueva a USD y recorrer pantallas | JVM; backend local | Tests + captura |
| CA-17 | FM (motivos y `CurrencyLocked`), UI-M. Manual con movimientos | JVM / emulador | Tests + captura |
| CA-18 | RD, FR, FM. Manual: correo con y sin cuenta | JVM; dos cuentas | Tests + captura |
| CA-19 | `InvitationValidationTest`, FM; UI-M | JVM / emulador | Tests |
| CA-20 | RD, FR, FM; UI-M. Manual en ambas cuentas | JVM / emulador; dos cuentas | Tests + captura |
| CA-21 | FM (vencida, ocultado, reinvitar, quitar) con reloj fijo; UI-M | JVM / emulador | Tests |
| CA-22 | MV, MS (campana), IV; UI-I (lista y vacío) | JVM / emulador | Tests + captura |
| CA-23 | IR (añade, contador, refresco con y sin éxito), IV, MV (cambiar). Manual | JVM; dos cuentas | Tests + captura |
| CA-24 | IR, IV; UI-I. Manual | JVM / emulador; dos cuentas | Tests + captura |
| CA-25 | RD, IV (cada código). Manual: revocada y ya respondida | JVM; dos cuentas | Tests + captura |
| CA-26 | FM, MV (cierre y aviso). Manual con dos sesiones | JVM; dos cuentas | Tests + captura |
| CA-27 | Tests de los cuatro repositorios y orígenes locales (borra la perdida, conserva las demás, descarga en curso), HR (callback). Manual: inspeccionar DataStore | JVM; emulador | Tests + inspección |
| CA-28 | MV (`NoData`, rutas sin familia), MS. Manual: quitar a una cuenta de su única familia | JVM / emulador; dos cuentas | Tests + captura |
| CA-29 | FM, IV, FC, MV (`Offline`), FR/IR (sin llamar al backend). Manual: modo avión | JVM; emulador | Tests + pasos manuales |
| CA-30 | FM, FC, IV, MV (dos pulsaciones, una llamada) | JVM | Tests |
| CA-31 | FC, FM (descarte); UI-C, UI-M con `StateRestorationTester`. Manual: segundo plano | JVM / emulador | Tests + pasos manuales |
| CA-32 | UI (descripciones, 48 dp, fuente al 200 %). Manual: TalkBack | Emulador | Tests + pasos manuales |

**Comprobaciones de regresión:** toda la suite JVM y la instrumentada de
`ui/main`, `ui/categories` (reordenado de tags), `ui/paymentmethods`,
`ui/movements` y `ui/dashboard`; los `SessionEndDuring…RefreshTest`. Test nuevo:
un refresco en curso no deshace un cambio local de la cabecera, y terminar la
sesión durante el borrado de familias perdidas no se bloquea.

**Comandos verificados para compilar y ejecutar tests:** los de `AGENTS.md`; no
se ejecutaron al redactar este plan.

**Pruebas en dispositivo, emulador o simulador:** emulador con el backend local
(`scripts/dev-reverse.sh`) y dos cuentas de prueba, para lo que basta un emulador
alternando sesión o dos a la vez. No hace falta hardware real. De paso se podrán
comprobar casos pendientes de otras funcionalidades que necesitaban una segunda
cuenta (006 CA-06; 007 CA-15 y CA-24), sin que formen parte de esta.

**Limitaciones del entorno:** CA-14 como Integrante queda bloqueado hasta que el
backend resuelva BG-01. Una invitación vencida real tarda 7 días: CA-21 y el
caso vencida de CA-25 se cubren con tests; a mano solo si la persona ajusta la
fecha en su base local. El bloqueo por presupuestos (CA-17) no se puede provocar
desde la app y se cubre con tests. TalkBack con voz puede no estar disponible.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. **Cabecera:** `memberCount`, cambios locales, versión por recurso y callback.
   Comprobación: HR y orígenes de datos.
2. **Familias perdidas (RF-23):** olvidar en los cuatro repositorios y conexión
   en `AppContainer`. Comprobación: CA-27 (unitario) y regresión de sesión.
3. **Dominio y datos de `family`:** modelos, validaciones, origen remoto y los
   dos repositorios; correos y cambios en miembros. Comprobación: RD, FR, IR,
   validaciones, CA-08 (unitario). Depende de 1.
4. **Selector y navegación:** rutas, selector siempre abierto, reordenado,
   campana, avisos y estado sin familias, con pantallas provisionales.
   Comprobación: CA-01 a CA-04, CA-28. Depende de 3.
5. **Crear familia.** Comprobación: CA-05, CA-06. Depende de 4.
6. **«Mi familia»:** lectura, roles, quitar, salir. Comprobación: CA-07 a CA-15.
7. **Moneda.** Comprobación: CA-16, CA-17. Depende de 6.
8. **Invitaciones enviadas.** Comprobación: CA-18 a CA-21. Depende de 6.
9. **Invitaciones recibidas.** Comprobación: CA-22 a CA-25. Depende de 4.
10. **Cierre:** sin conexión, accesibilidad, suite completa, `lintDebug`, pruebas
    manuales con dos cuentas (CA-26 a CA-32) y documentación.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - *BG-01.* «Salir» se implementa para ambos roles sobre
    `DELETE …/members/<propio>`, que funcionaba para el Propietario. El backend
    lo permite también al Integrante desde el 2026-11-24 (misma ruta, `e8271db`).
    RF-12 no se dará por cumplido hasta comprobarlo.
  - *Refresco contra cambio local.* Es el riesgo principal: hay refrescos al
    iniciar, en `ON_START` y al recuperar red. Medida: versión por recurso con
    tests propios antes de construir las pantallas (etapa 1).
  - *Borrado de datos.* Un error en RF-23 borraría cachés válidas. Medida: solo
    actúa con una lista de familias recibida del backend o aplicada tras su
    confirmación, nunca por un fallo; son copias que se vuelven a descargar.
  - *Arrastrar dentro de una hoja inferior.* El gesto puede competir con el de
    cerrar la hoja. Medida: el arrastre empieza solo en el asa y se prueba en el
    emulador al inicio de la etapa 4; subir/bajar existe siempre.
  - *Clave con moneda.* Inicio y Movimientos pueden reiniciar mes y filtro una
    vez si la moneda pasa de desconocida a conocida. Aceptado: con la moneda
    anterior mostrarían datos incompletos.
  - *Miembros cambiados por otro Propietario.* La lista puede estar atrasada al
    decidir si es el único Propietario. Medida: el backend decide y su rechazo se
    informa (RF-11).
  - *Contrato leído, no probado.* Los códigos y `settings` se leyeron en el
    código del backend. Medida: comprobarlos en la primera prueba manual de cada
    etapa y anotarlo en `BACKEND_GAPS.md`.
- **Decisiones pendientes:** Ninguna.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
