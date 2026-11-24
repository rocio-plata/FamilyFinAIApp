# TASKS: Gestión de familias

**SPEC:** `docs/features/009-family-management/SPEC.md` (Aprobada)
**PLAN:** `docs/features/009-family-management/PLAN.md` (Aprobado)

<!-- Tareas de máx. 20-30 min, en orden de dependencia. Marcar [x] solo tras
ejecutar la validación y registrar en "Evidencia" el resultado real. Si una
tarea queda bloqueada, anotar el motivo en "Evidencia". -->

Rutas relativas a `app/src/main/java/com/rocioplata/familyfinaiapp/` salvo
indicación. Los tests JVM van en `app/src/test/…` y los de UI en
`app/src/androidTest/…`, en el mismo paquete que lo que prueban.

**Dependencia externa:** RF-12 para el Integrante (CA-14) está bloqueado por
BG-01 (`BACKEND_GAPS.md`). No impide implementar ninguna tarea, pero sí darlo por
verificado.

## Cabecera

- [x] **T-01 · Número de miembros de la familia**
  - **Cubre:** RF-01 (CA-01) · **Depende de:** —
  - **Alcance:** `domain/header/Family.kt`: `memberCount: Int? = null`. `HeaderRemoteDataSource` lo lee de `GET /me/families`; `HeaderLocalDataSource` lo guarda como campo opcional sin cambiar `schemaVersion`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.header.*"`: leído, ausente, ida y vuelta en caché y copia anterior sin el campo. `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan y la app compila sin cambios visibles.
  - **Evidencia:** —
- [x] **T-02 · Cambios locales de las familias y del contador**
  - **Cubre:** RF-01, RF-02, RF-05, RF-09, RF-12, RF-13, RF-19, RF-20 · **Depende de:** T-01
  - **Alcance:** `HeaderRepository` y su implementación: añadir una familia al final (o actualizarla si ya está), quitarla, fijar el orden, actualizar rol, moneda o número de miembros, y fijar el contador de invitaciones; en memoria y caché, conservando `savedAt` y comprobando que la sesión sigue siendo la misma. Versión por recurso: un refresco que empezó antes de un cambio descarta ese recurso. Adaptar el falso de `MainViewModelTest`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.header.HeaderRepositoryImplTest"`: cada operación (memoria, caché, `savedAt`); sin datos cargados; un refresco en curso no deshace un alta, una baja ni el contador, y sí actualiza los demás recursos; `clear()` durante un cambio. Regresión: `session.SessionEndDuringHeaderRefreshTest` y `ui.main.MainViewModelTest`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [x] **T-03 · Aviso de las familias vigentes**
  - **Cubre:** RF-23 (CA-27) · **Depende de:** T-02
  - **Alcance:** `HeaderRepositoryImpl` recibe un callback con los identificadores vigentes, llamado tras cada lista recibida del backend o cambio local aplicado, fuera del candado de escritura; nunca por un fallo, por la caché ni tras `clear()`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.header.HeaderRepositoryImplTest"`: se llama con éxito y con cambio local; no con fallo, caché, lista descartada por versión o época ni `clear()`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Datos de familias perdidas

- [x] **T-04 · Olvidar familias en categorías y medios de pago**
  - **Cubre:** RF-23 (CA-27) · **Depende de:** —
  - **Alcance:** Interfaz, repositorio y origen local de `categories` y `paymentmethods`: olvidar las familias que no estén en un conjunto (memoria y claves en disco), con época por familia para que una descarga o un cambio en curso no vuelva a guardarla, sin esperar a la red ni afectar a las demás.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.categories.*"` y `…data.paymentmethods.*`: borra la perdida de memoria y disco, conserva las demás, sin nada que borrar, descarga en curso de una perdida y de una vigente, conjunto vacío, volver a guardar si se recupera. Regresión: `SessionEndDuringCategoriesRefreshTest` y `SessionEndDuringPaymentMethodsRefreshTest`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [x] **T-05 · Olvidar familias en movimientos y miembros**
  - **Cubre:** RF-23 (CA-27) · **Depende de:** —
  - **Alcance:** Lo mismo en `movements` (todos los meses de la familia y su `last_payment_method_<usuario>_<familia>`) y `members`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.movements.*"` y `…data.members.*`: los casos de T-04, varios meses, último medio de pago de la perdida y de otra. Regresión: `SessionEndDuringMovementsRefreshTest` y `SessionEndDuringMembersRefreshTest`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [x] **T-06 · Cableado del borrado**
  - **Cubre:** RF-23 (CA-27) · **Depende de:** T-03, T-04, T-05
  - **Alcance:** `AppContainer.kt`: conectar el callback de la cabecera a los cuatro repositorios.
  - **Validación:** test JVM nuevo en `session/`: con los repositorios reales, una lista sin una familia borra lo suyo, y terminar la sesión durante ese borrado no se bloquea. `./gradlew :app:assembleDebug :app:testDebugUnitTest` (suite JVM completa).
  - **Hecho cuando:** la suite pasa.
  - **Evidencia:** —

## Dominio

- [x] **T-07 · Modelos, fallos y contratos**
  - **Cubre:** RF-02, RF-05, RF-09 a RF-22 · **Depende de:** T-01
  - **Alcance:** `domain/family/`: `SentInvitation`, `ReceivedInvitation` (`toString()` sin correo ni nombres), `FamilySettings`, `FamilyAccessFailure` y `FamilyAccessResult`, `FamilyManagementRepository` e `InvitationsRepository`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.domain.family.*"` (vigente o vencida justo antes, en y después de su fecha; `toString`). `grep` sin imports de `android`, `androidx`, `io.ktor` ni `kotlinx.serialization`.
  - **Hecho cuando:** los tests pasan y el dominio es puro.
  - **Evidencia:** `./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; suite JVM completa: 1356 tests, 0 fallos, 0 errores, 0 omitidos (suma de `app/build/test-results/testDebugUnitTest`, de esta ejecución). `domain/family/`: `SentInvitation` (vencida a partir de un instante después de `expiresAt`, como el backend) y `ReceivedInvitation`, ambas con `toString()` sin correo ni nombres; `FamilySettings` con `CurrencyLockReason`; `FamilyAccessFailure` y `FamilyAccessResult`; las interfaces `FamilyManagementRepository` e `InvitationsRepository`. `FamilyInvitationTest` 2 tests (justo antes, en y después de la fecha; `toString`). `grep -rnE "import (android|androidx|io\.ktor|kotlinx\.serialization)"` sobre `domain/family` → sin resultados (el dominio es puro).
- [x] **T-08 · Validaciones de nombre y correo**
  - **Cubre:** RF-04, RF-14, RF-17 (CA-05, CA-19) · **Depende de:** T-07
  - **Alcance:** `FamilyNameValidation` (recorte, vacío, 60, emojis por rangos de puntos de código, repetido sin distinguir mayúsculas) e `InvitationValidation` (recorte, vacío, formato con `AuthValidation`, 255, invitación vigente al mismo correo sin distinguir mayúsculas).
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.domain.family.*"`: límites 60/61 y 255/256; tildes, ñ y signos permitidos; emoji simple, compuesto, bandera y tecla rechazados; repetido con otras mayúsculas y espacios; invitación vencida permitida.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** Mismo resultado de la suite completa. `FamilyNameRulesTest` 10 y `InvitationValidationTest` 8 tests: límites 60/61 y 255/256, espacios extremos que no cuentan, tildes, ñ, signos y © ® ™ permitidos; rechazados emoji simple, compuesto, bandera, tecla, con tono de piel y símbolo del bloque de símbolos varios; repetido sin distinguir mayúsculas ni espacios; invitación vencida permitida y la que vence justo ahora todavía bloquea; orden de los errores. Los objetos se llaman `FamilyNameRules` e `InvitationValidation`, y los resultados `FamilyNameValidation` e `InvitationEmailValidation` (mismo patrón que `PaymentMethodValidation`).

## Datos de gestión e invitaciones

- [x] **T-09 · Origen remoto: familias y miembros**
  - **Cubre:** RF-02, RF-05, RF-09 a RF-13 · **Depende de:** T-07
  - **Alcance:** `data/family/FamilyAccessRemoteDataSource.kt`: crear, reordenar, ajustes, moneda, rol, quitar o salir, y la traducción de errores del plan. Un motivo de bloqueo desconocido se conserva como bloqueo sin motivo.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.family.FamilyAccessRemoteDataSourceTest"` (`ktor-client-mock`): método, ruta, cuerpo y estado de cada llamada; cada fila de la tabla de errores; JSON y rol desconocidos; red; cancelación relanzada.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `FamilyAccessRemoteDataSourceTest` 37 tests (`ktor-client-mock`): método, ruta, cuerpo y Bearer de cada llamada; ajustes desbloqueada, bloqueada por movimientos y presupuestos y con un motivo desconocido (sigue bloqueada, sin motivos conocidos); moneda desconocida, rol desconocido y JSON ilegible → `InvalidData`; cada fila de la tabla de errores, probada en las 12 operaciones; `401` → `Unauthorized`; sin código o `500` → `Unknown`; un `200` donde se espera `204` no es éxito; red y espera excesiva → `Network`; cancelación relanzada en todas; identificadores codificados en la ruta. `FamilyAccessRemoteDataSource` (`data/family/`).
- [x] **T-10 · Origen remoto: invitaciones**
  - **Cubre:** RF-14 a RF-21 · **Depende de:** T-09
  - **Alcance:** Listar enviadas, invitar, revocar, listar recibidas, aceptar y rechazar (las dos últimas sin cuerpo ni `Content-Type`).
  - **Validación:** el mismo test: cada llamada y sus errores, nombre de quien invita nulo, fecha ilegible → `InvalidData`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** Mismo test y mismo resultado: enviadas (orden conservado, solo `PENDING`, vencidas devueltas, rol o fecha ilegibles → `InvalidData`), invitar (cuerpo `email` y `role`, `201`), revocar, recibidas (quien invita sin nombre o sin datos → nulo, vacías, fecha o rol ilegibles → `InvalidData`), aceptar y rechazar sin cuerpo ni `Content-Type` (se comprueba en la petición). Una sola clase para T-09 y T-10, como decía el plan.
- [x] **T-11 · Miembros: correos y cambios**
  - **Cubre:** RF-07, RF-09, RF-10 (CA-08, CA-10, CA-12) · **Depende de:** T-05
  - **Alcance:** `FamilyMembersRepository`: refresco que además devuelve los correos sin guardarlos en memoria ni en disco, y aplicar un rol o una baja a memoria y caché; un refresco en curso no deshace el cambio.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.members.*"`: correos presentes, ausentes y nulos; lo guardado no contiene ningún correo; rol y baja aplicados con `savedAt` intacto; refresco en curso.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `FamilyMembersRemoteDataSourceTest` 16 (4 nuevos: correos recortados y sin vacíos, Integrante sin correos, mismos fallos que sin correos, el texto del resultado sin correos), `FamilyMembersRepositoryImplTest` 38 (10 nuevos: correos devueltos y lista actualizada; **lo guardado en disco contiene el nombre y ningún correo ni `@`**, leyendo los archivos del DataStore; Integrante sin correos; fallo sin cambiar lo guardado; rol y baja aplicados con `savedAt` intacto, también cuando los datos ya eran antiguos; sin lista cargada o sin sesión no hacen nada; un refresco en curso no deshace una baja ni un rol y uno posterior al cambio manda el backend; con correos y un cambio local en curso devuelve los correos sin pisar la lista). `FamilyMembersRepository` gana `refreshWithEmails`, `applyRole` y `applyRemoval`; versión de cambios local en el repositorio, como en la cabecera. Los correos solo existen en `MembersWithEmails`, cuyo `toString()` no los incluye.
- [x] **T-12 · Repositorio de gestión**
  - **Cubre:** RF-02, RF-05, RF-09 a RF-17, RF-25 (CA-06, CA-11, CA-14, CA-16) · **Depende de:** T-02, T-09, T-10, T-11
  - **Alcance:** `FamilyManagementRepositoryImpl` y su alta en `AppContainer`: sin conexión devuelve `Offline` sin llamar; con éxito aplica a cabecera y miembros (alta al final, orden, rol propio, número de miembros, moneda, salida).
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.family.FamilyManagementRepositoryImplTest"`: cada operación con éxito, con fallo (nada cambia) y sin conexión (cero llamadas). `./gradlew :app:assembleDebug`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `FamilyManagementRepositoryImplTest` 22 tests con la cabecera y los miembros falsos: crear (al final, Propietaria, un miembro), reordenar, ajustes sin tocar nada, moneda, rol de otro y rol propio (este también cambia la cabecera), quitar (baja el número de miembros, nunca por debajo de uno, ni lo inventa si no se conoce), salir (borra la familia de la cabecera; único Propietario, rol insuficiente y sin sesión sin cambios), invitaciones sin tocar cabecera ni miembros; cada operación con fallo no cambia nada; sin conexión: las 10 devuelven `Offline` con 0 llamadas. `AppContainer` gana `familyManagementRepository` (`assembleDebug` incluido en la ejecución anterior). Salir usa `DELETE …/members/<propio>` (BG-01).
- [x] **T-13 · Repositorio de invitaciones recibidas**
  - **Cubre:** RF-18 a RF-21, RF-25 (CA-23, CA-24) · **Depende de:** T-02, T-10
  - **Alcance:** `InvitationsRepositoryImpl` y su alta en `AppContainer`: listar (fija el contador), aceptar (añade la familia de la invitación, baja el contador y refresca la cabecera) y rechazar (baja el contador).
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.family.InvitationsRepositoryImplTest"`: éxito, fallo, sin conexión; aceptar con el refresco fallido deja la familia en la lista sin moneda.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `InvitationsRepositoryImplTest` 11 tests: recibidas fija el contador con la lista (también a cero); aceptar añade la familia con nombre y rol de la invitación y sin moneda ni miembros, baja el contador (sin inventarlo si no se conoce y sin bajar de cero) y refresca, con la familia ya en la lista cuando se refresca; con fallo no cambia nada ni refresca; rechazar baja el contador sin refrescar; sin conexión: 0 llamadas. `AppContainer` gana `invitationsRepository`. Los tests con concurrencia de miembros y de estos repositorios se repitieron 3 veces con `--rerun` sin fallos. No se ejecutó `lintDebug` ni la suite instrumentada en esta sección.

## Navegación y selector

- [ ] **T-14 · Rutas nuevas y avisos en `MainViewModel`**
  - **Cubre:** RF-03 a RF-05, RF-18, RF-19, RF-22 (CA-04, CA-06, CA-22, CA-23, CA-26) · **Depende de:** T-02
  - **Alcance:** `FamilyRoute` (`Manage`, `Create`) e `InvitationsRoute`; familia opcional en la ruta abierta; aperturas, vuelta atrás, familia creada (activa, Inicio, `Manage`), cambiar a una familia y aviso «Ya no perteneces a …» cuando desaparece la de un `Manage` abierto. `MainScreen` y `TestMainHost` con pantallas provisionales.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.main.MainViewModelTest"`: abrir y volver desde cada pestaña; sin familias; no se abre una gestión sobre otra; creada; cambiar; desaparece con y sin `Manage`. `./gradlew :app:assembleDebugAndroidTest`.
  - **Hecho cuando:** los tests pasan y los de UI compilan.
  - **Evidencia:** —
- [ ] **T-15 · Selector siempre abierto y reordenado en `MainViewModel`**
  - **Cubre:** RF-01, RF-02 (CA-01 a CA-03, CA-30) · **Depende de:** T-12, T-14
  - **Alcance:** Eliminar `hasSelector`; orden provisional al soltar, activa fijada antes de mover, uno a la vez, fallo → orden anterior y error; `InvalidOrder` refresca la cabecera.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.main.MainViewModelTest"`: una familia abre el selector; éxito, fallo, `InvalidOrder`, sin conexión, dos seguidos, activa implícita conservada.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-16 · Cálculo de arrastre compartido**
  - **Cubre:** RF-02 · **Depende de:** —
  - **Alcance:** Mover `dragTargetIndex` y `dragShift` sin cambios de `TagList.kt` a `ui/common/DragReorder.kt`, con su `TagDragTest`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.common.*"` y `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`.
  - **Hecho cuando:** los tests pasan y `TagList` no cambia de comportamiento.
  - **Evidencia:** —
- [ ] **T-17 · Hoja del selector**
  - **Cubre:** RF-01 a RF-03 (CA-01 a CA-04, CA-32) · **Depende de:** T-15, T-16
  - **Alcance:** `FamilySelectorSheet.kt` según `009-selector-familia.png`: rol, número de miembros, asa con subir/bajar, gestión solo en la activa, «Crear nueva familia» y error en línea. Probar primero en el emulador que el arrastre no cierra la hoja.
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rocioplata.familyfinaiapp.ui.main.FamilySelectorContentTest` (nuevo): datos, una y varias familias, arrastre, subir/bajar, gestión, error, descripciones, 48 dp y fuente al 200 %.
  - **Hecho cuando:** los tests pasan y hay captura.
  - **Evidencia:** —
- [ ] **T-18 · Cabecera, pestañas sin familias y avisos**
  - **Cubre:** RF-01, RF-18, RF-22, RF-24 (CA-01, CA-22, CA-28) · **Depende de:** T-14, T-15, T-17
  - **Alcance:** `MainHeader` (selector y campana pulsables), `NoFamiliesContent`, `SnackbarHost` en `MainScreen`, sin «+» cuando no hay familias.
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rocioplata.familyfinaiapp.ui.main.MainScreenTest` y `…MainScreenBehaviorTest`: `singleFamilyHasNoSelector` se sustituye por la regla nueva; campana con y sin invitaciones; sin familias en las cinco pestañas; aviso.
  - **Hecho cuando:** los tests pasan y hay captura.
  - **Evidencia:** —

## Crear familia

- [ ] **T-19 · ViewModel de crear familia**
  - **Cubre:** RF-04, RF-05, RF-25 (CA-05, CA-06, CA-29 a CA-31) · **Depende de:** T-08, T-12, T-14
  - **Alcance:** `ui/family/FamilyCreateViewModel.kt` y estado: texto, errores, una operación, descarte, y comprobación previa al reintento tras `Network`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.family.FamilyCreateViewModelTest"`: cada nombre inválido sin llamar; éxito; `Offline`; `Network` y reintento ya aplicado, no aplicado y sin poder comprobarlo; doble pulsación; descarte.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-20 · Pantalla de crear familia**
  - **Cubre:** RF-04, RF-05 (CA-05, CA-31, CA-32) · **Depende de:** T-18, T-19
  - **Alcance:** `FamilyCreateScreen` y contenido según `009-nueva-familia.png` (solo el nombre, contador, textos informativos) y conexión en `MainScreen`.
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rocioplata.familyfinaiapp.ui.family.FamilyCreateContentTest`: contador y límite, errores, progreso, descarte, `StateRestorationTester`, fuente al 200 %.
  - **Hecho cuando:** los tests pasan y hay captura.
  - **Evidencia:** —

## Mi familia

- [ ] **T-21 · ViewModel: lectura, correos y solo lectura**
  - **Cubre:** RF-06 a RF-08, RF-25 (CA-07 a CA-09, CA-29) · **Depende de:** T-11, T-12
  - **Alcance:** `FamilyManagementViewModel` y estado: familia en vivo desde la cabecera, miembros ordenados, correos solo para el Propietario con conexión, secciones independientes, refresco al abrir, en `ON_START` y al recuperar red.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.family.FamilyManagementViewModelTest"`: orden, «Tú», sin nombre, correos en cada caso, copia antigua, error por sección, un Integrante no pide ajustes ni invitaciones, cambio de rol en vivo.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-22 · ViewModel: roles, quitar y salir**
  - **Cubre:** RF-09 a RF-12, RF-22 (CA-10 a CA-15, CA-26, CA-30) · **Depende de:** T-21
  - **Alcance:** Acción pendiente con confirmación, una a la vez; último Propietario; «Salir» con su motivo; fallos y refrescos de la tabla del plan.
  - **Validación:** el mismo test: cada acción confirmada y cancelada; rol propio; `LastOwner`, `MemberNotFound`, `InsufficientRole`, `NotAMember`, `Network`, `Offline`; cada motivo de «Salir»; doble pulsación.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-23 · Pantalla «Mi familia»**
  - **Cubre:** RF-03, RF-06 a RF-12 (CA-04, CA-07 a CA-15, CA-32) · **Depende de:** T-18, T-22
  - **Alcance:** `FamilyManagementScreen`, contenido, `FamilyTexts` y conexión en `MainScreen`, según `009-gestion-familia.png`: datos, miembros con sus acciones, explicación de roles, salir, estados y avisos.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.family.FamilyTextsTest"` y `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rocioplata.familyfinaiapp.ui.family.FamilyManagementContentTest`: Propietario e Integrante, correos, confirmaciones, «Salir» deshabilitado con su motivo, secciones en carga y error, descripciones, 48 dp, fuente al 200 %.
  - **Hecho cuando:** los tests pasan y hay captura.
  - **Evidencia:** —

## Moneda

- [ ] **T-24 · Cambio de moneda**
  - **Cubre:** RF-13 (CA-16, CA-17, CA-29, CA-30) · **Depende de:** T-23
  - **Alcance:** Ajustes y cambio en el ViewModel; sección de moneda en la pantalla (CLP, USD, EUR, o bloqueo con su motivo); claves de los ViewModels de Inicio y Movimientos con la moneda.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.family.FamilyManagementViewModelTest"` (cada motivo, motivo desconocido, `CurrencyLocked`, error de ajustes, éxito) y `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rocioplata.familyfinaiapp.ui.family.FamilyManagementContentTest`. Manual: familia nueva a USD y recorrer Inicio, Movimientos y el alta.
  - **Hecho cuando:** los tests pasan y el paso manual está registrado.
  - **Evidencia:** —

## Invitaciones enviadas

- [ ] **T-25 · ViewModel de invitaciones enviadas**
  - **Cubre:** RF-14 a RF-17 (CA-18 a CA-21, CA-29 a CA-31) · **Depende de:** T-08, T-23
  - **Alcance:** Lista con orden y ocultado de vencidas (reloj inyectado), formulario con rol, revocar, quitar y volver a invitar; comprobación previa al reintento tras `Network`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.family.FamilyManagementViewModelTest"`: orden, vencida oculta por otra vigente o por un miembro (con correos no disponibles no se oculta por miembro), cada error junto al campo, reintento, descarte, revocar confirmado y cancelado, `InvitationNotPending`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-26 · Interfaz de invitaciones enviadas**
  - **Cubre:** RF-14 a RF-17 (CA-18 a CA-21, CA-31, CA-32) · **Depende de:** T-25
  - **Alcance:** Sección en «Mi familia» con cantidad, «Vencida», menú por fila y diálogo «Invitar miembro» con desplazamiento, teclado y textos de aviso.
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rocioplata.familyfinaiapp.ui.family.FamilyManagementContentTest` y `…InviteMemberDialogTest` (nuevo): vigente, vencida, vacío, error, formulario, descarte, `StateRestorationTester`, fuente al 200 %.
  - **Hecho cuando:** los tests pasan y hay captura.
  - **Evidencia:** —

## Invitaciones recibidas

- [ ] **T-27 · ViewModel de invitaciones recibidas**
  - **Cubre:** RF-18 a RF-21, RF-25 (CA-22 a CA-25, CA-29, CA-30) · **Depende de:** T-13, T-14
  - **Alcance:** `InvitationsViewModel` y estado: carga, vacío, error, aceptar, confirmar rechazo, aviso con «Cambiar a esta familia», una operación a la vez.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.family.InvitationsViewModelTest"`: éxito, cada fallo con su refresco, `Network`, `Offline`, cancelar, doble pulsación, evento consumido.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** `./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; suite JVM completa: 1252 tests, 0 fallos, 0 errores, 0 omitidos (suma de `app/build/test-results/testDebugUnitTest`, de esta ejecución). `AppContainer`: `headerRepository` recibe `onFamiliesKnown`, que llama a `retainFamilies` de los cuatro repositorios (se resuelven al llamarse, sin ciclo de construcción). Test nuevo `session/FamilyDataRetentionWiringTest` (3 tests, con los repositorios y orígenes reales y la sesión real): una lista sin una familia borra lo suyo de los cuatro y conserva lo de las demás; con todas vigentes no se borra nada; terminar la sesión después lo vacía todo sin bloquearse (límite de 5 s en tiempo real). Regresión: los cinco `SessionEndDuring…RefreshTest` y `SessionManagerTest` siguen pasando. Los tests de repositorios y del cableado se repitieron 3 veces con `--rerun` sin fallos. No se ejecutó `lintDebug` ni la suite instrumentada; `AppContainer` no tiene test propio (necesita `Context`): su cableado se comprueba con el test equivalente y se verificará a mano en la T-30.
- [ ] **T-28 · Pantalla de invitaciones**
  - **Cubre:** RF-18 a RF-21 (CA-22 a CA-25, CA-32) · **Depende de:** T-18, T-27
  - **Alcance:** `InvitationsScreen`, contenido y conexión en `MainScreen`, según `009-notificaciones-familias.png` (sin código ni datos que el backend no da).
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rocioplata.familyfinaiapp.ui.family.InvitationsContentTest`: tarjeta completa, quien invita sin nombre, vacío, error, confirmación, aviso, descripciones, 48 dp, fuente al 200 %.
  - **Hecho cuando:** los tests pasan y hay captura.
  - **Evidencia:** El mismo `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.*"` → BUILD SUCCESSFUL; `MovementsLocalDataSourceTest` 17 (4 nuevos: borra meses y último medio de la perdida de todos los usuarios y conserva las demás, conjunto vacío, identificadores UUID reales sin confundir familia y usuario, claves ajenas), `MovementsRepositoryImplTest` 57 (8 nuevos: los 7 comunes más el borrado de varios meses, de `movement(...)` y del último medio de pago), `FamilyMembersLocalDataSourceTest` 12 (3 nuevos) y `FamilyMembersRepositoryImplTest` 28 (7 nuevos), 0 fallos. Misma interfaz y mismo esquema que en T-04. La familia se lee de la clave de movimientos (`movements_<familia>_<yyyy-MM>` y `last_payment_method_<usuario>_<familia>`), por lo que los identificadores no pueden llevar `_`; son UUID y hay un test con identificadores reales.

## Cierre

- [ ] **T-29 · Comprobaciones automáticas completas**
  - **Cubre:** Regresión y CA-30 a CA-32 · **Depende de:** T-06, T-20, T-24, T-26, T-28
  - **Alcance:** Suite JVM e instrumentada completas y lint; corregir lo que falle. No dejar la app desinstalada del emulador de la persona.
  - **Validación:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` y `./gradlew :app:connectedDebugAndroidTest`.
  - **Hecho cuando:** todo pasa sin avisos nuevos de lint, o cada fallo previo está identificado.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.*"` → BUILD SUCCESSFUL; en `data.categories` y `data.paymentmethods` (leído de `app/build/test-results/testDebugUnitTest`, de esta ejecución): `CategoriesLocalDataSourceTest` 18 y `PaymentMethodsLocalDataSourceTest` 16 (3 nuevos cada uno: borra las que no están y conserva las demás, conjunto vacío, no toca claves que no son de una familia), `CategoriesRepositoryImplTest` 54 y `PaymentMethodsRepositoryImplTest` 39 (7 nuevos cada uno: borra de memoria y disco la perdida y conserva las demás, conjunto vacío, todas vigentes sin cambios, descarga en curso de una perdida que no se guarda y sin esperar a la red, descarga en curso de una vigente que sí se guarda, familia recuperada que vuelve a guardarse, `clear` que olvida el conjunto); 0 fallos. Comprobación de que los tests detectan el fallo: quitando temporalmente la protección en `PaymentMethodsRepositoryImpl` fallaron 2 tests; ya restaurada. `CategoriesRepository` y `PaymentMethodsRepository` ganan `retainFamilies(familyIds: Set<String>)`; los orígenes locales borran las claves `<prefijo><familia>` que no estén en el conjunto. **Desviación del plan:** en vez de una época por familia, cada repositorio recuerda el último conjunto de familias vigentes (`knownFamilyIds`, nulo hasta que se conoce; `clear()` lo reinicia), porque la época por familia no cubre la primera descarga de una familia sin nada guardado; el PLAN lo recoge. Los siete fakes de tests de ViewModels implementan el método sin efecto.
- [ ] **T-30 · Pruebas manuales con dos cuentas**
  - **Cubre:** CA-01 a CA-29, CA-31 · **Depende de:** T-29
  - **Alcance:** Con el backend local y dos cuentas de prueba: pasos manuales del plan, códigos reales del backend (anotados en `BACKEND_GAPS.md`), modo avión y segundo plano; inspección de `members_cache` y de los almacenes tras perder una familia. Borrar los datos de prueba al terminar.
  - **Validación:** tabla por criterio con resultado y captura. CA-14 como Integrante requiere BG-01; CA-21 y el caso vencida de CA-25, solo si se ajusta la fecha en la base local.
  - **Hecho cuando:** cada criterio está verificado o marcado como no verificado con su motivo.
  - **Evidencia:** `./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; suite JVM completa: 1207 tests, 0 fallos, 0 errores, 0 omitidos (suma de `app/build/test-results/testDebugUnitTest`, de esta ejecución). `HeaderRepositoryImplTest` 36 (8 nuevos para el aviso: lo recibe con un refresco correcto y con cada cambio local; no lo recibe sin lista cargada ni sin sesión, con fallo de red, con la caché, con el contador, cuando fallan solo las familias, cuando la lista se descarta por un cambio local, tras un `clear` durante el refresco, ni tras `clear`; y un aviso suspendido no retiene el candado). Repetido `HeaderRepositoryImplTest` 3 veces más con `--rerun` (sin fallos en ninguna) para descartar carreras. `HeaderRepositoryImpl` recibe `onFamiliesKnown: suspend (Set<String>) -> Unit = {}` antes de `clock`, así que las llamadas existentes no cambian; se invoca fuera de `storageLock`. Todavía no está conectado a ningún repositorio (T-06). No se ejecutó `lintDebug` ni la suite instrumentada en esta sección.
- [ ] **T-31 · Accesibilidad manual**
  - **Cubre:** CA-32 · **Depende de:** T-29
  - **Alcance:** TalkBack y fuente máxima en selector, crear, «Mi familia», invitar e invitaciones; modo oscuro.
  - **Validación:** lista de pantallas con resultado.
  - **Hecho cuando:** el resultado está registrado, incluido lo que el entorno no permita.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "…data.header.*" --tests "…ui.main.MainViewModelTest" --tests "…session.SessionEndDuringHeaderRefreshTest"` → BUILD SUCCESSFUL; `HeaderRepositoryImplTest` 28 (14 nuevos: alta al final con `savedAt` conservado y sigue marcada como antigua, alta de una existente sin duplicar, baja, orden con desconocidas y no mencionadas, actualización de rol, moneda y miembros, sin lista cargada, contador sin lista de familias, sin sesión, tras `clear`, y un refresco en curso que no deshace un alta, una baja ni el contador pero sí actualiza los demás recursos; uno posterior a un cambio manda el backend). `MainViewModelTest` 68 y `SessionEndDuringHeaderRefreshTest` 1, sin cambios de comportamiento (el falso implementa las operaciones nuevas sin efecto). `HeaderRepository` gana `addFamily`, `removeFamily`, `setFamilyOrder`, `updateFamily` y `setPendingInvitations`; `HeaderRepositoryImpl` aplica cada cambio bajo `storageLock` a memoria y caché y mantiene una versión por recurso (`familiesVersion`, `invitationsVersion`) que el refresco compara al guardar. Sin lista de familias cargada o sin sesión, los cambios de familias no hacen nada; el contador se fija igualmente (es un dato nuevo).
- [ ] **T-32 · Documentación final**
  - **Cubre:** — · **Depende de:** T-30, T-31
  - **Alcance:** `AGENTS.md` (capas, rutas, borrado de familias perdidas, versión por recurso, claves con moneda), `docs/ROADMAP.md`, `README.md` y `BACKEND_GAPS.md`. `CLAUDE.md` no se toca. El contrato solo si el backend publica BG-01.
  - **Validación:** revisar referencias y que cada CA apunte a su evidencia.
  - **Hecho cuando:** los documentos coinciden con lo implementado.
  - **Evidencia:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.header.*" :app:assembleDebug` → BUILD SUCCESSFUL; `HeaderRemoteDataSourceTest` 13 (1 nuevo: `memberCount` ausente o nulo; el de éxito ahora lee 3 y 2), `HeaderLocalDataSourceTest` 11 (2 nuevos: ida y vuelta con `memberCount` y copia anterior sin el campo), 0 fallos (leído de `app/build/test-results/testDebugUnitTest`, de esta ejecución). `Family.memberCount: Int? = null`; el remoto lo lee de `GET /me/families` y la caché lo guarda como campo opcional sin cambiar `schemaVersion = 1`. Sin cambios visibles.
