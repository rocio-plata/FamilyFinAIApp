# TASKS: Movimientos

**SPEC:** `docs/features/007-financial-items/SPEC.md` (Aprobada)
**PLAN:** `docs/features/007-financial-items/PLAN.md` (Aprobado)

<!-- Tareas de máx. 20-30 min, en orden de dependencia. Marcar [x] solo tras
ejecutar la validación y registrar en "Evidencia" el resultado real. Si una
tarea queda bloqueada, anotar el motivo en "Evidencia". -->

Rutas relativas a `app/src/main/java/com/rocioplata/familyfinaiapp/` salvo
indicación. Los tests JVM van en `app/src/test/…` y los de UI en
`app/src/androidTest/…`, en el mismo paquete que lo que prueban.

## Dominio

- [ ] **T-01 · Dinero**
  - **Cubre:** RF-05, RF-17 (CA-06, CA-26) · **Depende de:** —
  - **Alcance:** `domain/money/Money.kt` (`Money(amountMinor: Long, currency)`, decimales de la moneda con `java.util.Currency`, suma y resta) y `MoneyInput.kt`: filtra lo que se puede escribir (solo dígitos, un separador y los decimales de la moneda; 12 dígitos enteros como máximo) y convierte el texto a unidad menor y al revés. Sin `Double`.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.domain.money.*"` con `MoneyTest` y `MoneyInputTest`: CLP sin decimales, USD y EUR con dos, separador coma y punto, ceros a la izquierda, vacío, cero, tope de 12 dígitos, ida y vuelta texto ↔ unidad menor, suma de varios montos.
  - **Hecho cuando:** los tests pasan y el paquete no usa `Double` ni `Float`.
  - **Evidencia:** —
- [ ] **T-02 · Modelo, fallos y contratos**
  - **Cubre:** RF-02, RF-08, RF-10, RF-14, RF-15 · **Depende de:** T-01
  - **Alcance:** `domain/movements/Movement.kt` (`Movement`, `MovementType`, `MovementsData` con lista ordenada + `isStale` o sin datos), `MovementFailure.kt` (los fallos de la tabla del plan, `MovementResult` y el resultado de edición: éxito, fallo o parcial), `MovementsRepository.kt` (flujo por familia y mes, flujo de un movimiento, `refresh`, crear, editar con solo los cambios, reclasificar, eliminar, último medio de pago usado y `clear()`), `domain/members/FamilyMember.kt` y `FamilyMembersRepository.kt` (flujo por familia, `refresh`, `clear()`).
  - **Validación:** `./gradlew :app:assembleDebug` y `grep` de imports `android`/`androidx`/`io.ktor`/`kotlinx.serialization` en `domain/movements`, `domain/members` y `domain/money`.
  - **Hecho cuando:** compila y el dominio no depende de Android, Compose, DTOs ni datos.
  - **Evidencia:** —
- [ ] **T-03 · Validación y fechas**
  - **Cubre:** RF-05, RF-16 (CA-06, CA-09) y BG-09 · **Depende de:** T-02
  - **Alcance:** `domain/movements/MovementValidation.kt`: monto mayor que cero, concepto de 1 a 100 con recorte, observaciones hasta 500 con recorte (vacías = sin observaciones), día no futuro; expone los límites. `MovementDates.kt`: día → instante a enviar (ahora si es hoy, 12:00 locales si es pasado), instante → día en la zona del dispositivo, y límites de un mes como instantes; reloj y zona inyectables.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.domain.movements.*"` con `MovementValidationTest` y `MovementDatesTest`: cada regla y sus límites; hoy nunca produce un instante futuro; día pasado a las 12:00 locales; conversión con zonas fijas (Santiago, UTC, Madrid, Tokio) y cerca de la medianoche; límites del mes con meses de 28 a 31 días y cambio de hora.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Moneda de la familia

- [ ] **T-04 · Moneda en la cabecera**
  - **Cubre:** RF-05 (CA-25) y BG-10 · **Depende de:** —
  - **Alcance:** `domain/header/Family.kt` añade `currency: String?`; `data/header/HeaderRemoteDataSource.kt` lee `defaultCurrency`; `HeaderLocalDataSource.kt` la guarda como campo opcional sin cambiar `schemaVersion`. Se actualizan las construcciones de `Family` en el código y en los tests existentes.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.header.*"` (tests existentes más: moneda leída, respuesta sin moneda, copia anterior sin el campo sigue leyéndose) y la suite JVM completa para la regresión.
  - **Hecho cuando:** los tests pasan y ninguna pantalla existente cambia.
  - **Evidencia:** —

## Datos de miembros

- [ ] **T-05 · Miembros de la familia**
  - **Cubre:** RF-13, RF-15 (CA-24) · **Depende de:** T-02
  - **Alcance:** `data/members/FamilyMembersRemoteDataSource.kt` (`GET /families/:id/members`: `userId`, `displayName`, `role`; el correo no se lee), `FamilyMembersLocalDataSource.kt` (caché `members_cache`, una clave por familia, `schemaVersion = 1`, `userId` y `savedAt`) y `FamilyMembersRepositoryImpl.kt` con el patrón de medios de pago: flujo, `refresh`, reglas de `CachePolicy` y `clear()` con época.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.members.*"`: lista con y sin nombre, `403`, red, JSON ilegible; caché de otro usuario, caducada, antigua y versión desconocida; refresco que actualiza el flujo; fallo que conserva lo anterior; `clear()` durante un refresco.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Datos de movimientos

- [ ] **T-06 · Data source remoto**
  - **Cubre:** RF-01, RF-05, RF-09 a RF-11, RF-14 (CA-22) y BG-01, BG-02, BG-04, BG-07 · **Depende de:** T-03
  - **Alcance:** `data/movements/MovementsRemoteDataSource.kt`: listado con `from` y `to` juntos; alta siempre con `paymentMethodId` y `currency`; edición con solo los campos recibidos (`note: null` borra); reclasificación; eliminación sin `Content-Type`. Convierte `occurredOn` a día y `paymentMethodId` nulo. Traduce los errores de la tabla del plan con ambos prefijos; nunca lanza por red o servidor y relanza la cancelación.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.movements.MovementsRemoteDataSourceTest"` con `ktor-client-mock`: URL, método, parámetros y cuerpo de cada operación; cada código de error con `FINANCIAL_TRACKING.` y con `FINANCIAL.`; `403`, `404`, `500`, `IOException`, JSON inesperado; respuesta sin `paymentMethodId`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-07 · Caché por mes y último medio de pago**
  - **Cubre:** RF-07, RF-13 (CA-08, CA-16) · **Depende de:** T-02
  - **Alcance:** `data/movements/MovementsLocalDataSource.kt`: caché `movements_cache`, una clave por familia y mes con `schemaVersion = 1`, `userId` y `savedAt`; el último medio de pago usado, con una clave por usuario y familia; borrado completo.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.movements.MovementsLocalDataSourceTest"`: ida y vuelta de un mes; meses y familias que no se mezclan; versión desconocida y JSON ilegible = sin datos; último medio por usuario y familia; borrado.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-08 · Repositorio: lectura y refresco**
  - **Cubre:** RF-01, RF-02, RF-08, RF-13 (CA-01, CA-02, CA-16) y BG-06 · **Depende de:** T-06, T-07
  - **Alcance:** `data/movements/MovementsRepositoryImpl.kt`: flujo por familia y mes que carga la caché válida, `refresh` con un refresco a la vez por familia y mes, orden por día descendente y `createdAt` descendente, reglas de `CachePolicy`, flujo de un movimiento buscado en los meses cargados y `clear()` con época.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.movements.MovementsRepositoryImplTest"`: rango pedido para un mes; orden; caché reciente, antigua, caducada y de otro usuario; fallo que conserva lo anterior; dos meses independientes; movimiento por id presente y ausente; dos refrescos simultáneos → una llamada; `clear()` durante un refresco.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-09 · Repositorio: cambios**
  - **Cubre:** RF-05, RF-07, RF-09 a RF-11, RF-14 (CA-05, CA-08, CA-11 a CA-14, CA-17) · **Depende de:** T-08
  - **Alcance:** En `MovementsRepositoryImpl`: `Offline` sin llamar al backend; alta (completa `recordedBy` con el usuario de la sesión, la inserta en su mes si está cargado y recuerda el medio de pago); edición con datos y categoría en ese orden y resultado éxito, fallo o parcial; eliminación; aplicación de cada cambio a memoria y caché de los meses cargados, incluido el cambio de mes; último medio de pago usado (solo si sigue activo lo decide quien lo consume).
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.data.movements.MovementsRepositoryImplTest"`: sin conexión no hay llamada; alta visible en su mes y no en otro; alta de un mes no cargado; edición solo de datos, solo de categoría y de ambos; parcial cuando falla la categoría; sin intento de categoría si fallan los datos; cambio de fecha a otro mes cargado y no cargado; eliminación; fallo que no cambia nada; `savedAt` conservado; último medio recordado por usuario y familia.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-10 · Cableado, backups y fin de sesión**
  - **Cubre:** RF-07, RF-13 · **Depende de:** T-05, T-09
  - **Alcance:** `AppContainer.kt`: DataStores `movements_cache` y `members_cache`, repositorios nuevos y su `clear()` en `onSessionEnded`. `res/xml/backup_rules.xml` y `data_extraction_rules.xml` excluyen ambos archivos.
  - **Validación:** `./gradlew :app:assembleDebug` y `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.session.*"` con `SessionEndDuringMovementsRefreshTest` y `SessionEndDuringMembersRefreshTest` (terminar la sesión no espera al refresco y no deja datos), más los existentes.
  - **Hecho cuando:** compila y los tests pasan.
  - **Evidencia:** —

## Navegación

- [ ] **T-11 · Pestaña seleccionada en el ViewModel**
  - **Cubre:** CA-20 y RF-08 de la 001 · **Depende de:** —
  - **Alcance:** `ui/main/MainViewModel.kt` y `MainUiState.kt` guardan la pestaña seleccionada; `MainScreen.kt` deja de usar `remember`; atrás en otra pestaña sigue volviendo a Inicio. `TestMainHost` y los tests que comprobaban la regla anterior se actualizan a la nueva (la spec 001 ya está modificada).
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.main.*"` y `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.rocioplata.familyfinaiapp.ui.main`: la pestaña se conserva al recrear (`StateRestorationTester`); atrás vuelve a Inicio; el resto de `MainScreenTest` y `MainScreenBehaviorTest` sigue pasando.
  - **Hecho cuando:** los tests pasan en JVM y en emulador.
  - **Evidencia:** —
- [ ] **T-12 · Rutas de movimientos y botón "+"**
  - **Cubre:** RF-04, RF-08, RF-09 (CA-04) · **Depende de:** T-11
  - **Alcance:** `ui/main/MovementRoute.kt` (`Create`, `Detail(id)`, `Edit(id)` como `ManagementRoute`); `MainViewModel` las abre y cierra (desde las pestañas solo `Create` y `Detail`; `Edit` solo desde `Detail`; atrás en `Edit` vuelve a `Detail`; al cerrar se vuelve a la pestaña de origen); `MainScreen` muestra "+" en Inicio y Movimientos y lo cablea; pantallas provisionales para las tres rutas y para la pestaña Movimientos.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.main.MainViewModelTest"` (cada transición, rutas no permitidas, familia que desaparece, cambio de familia bloqueado) y tests de UI de `ui.main`: "+" visible y funcional en Inicio y Movimientos, ausente en las otras tres pestañas.
  - **Hecho cuando:** los tests pasan en JVM y en emulador.
  - **Evidencia:** —

## Listado

- [ ] **T-13 · Textos y formatos**
  - **Cubre:** RF-02, RF-15, RF-17 (CA-02, CA-24) · **Depende de:** T-02, T-04
  - **Alcance:** `ui/movements/MoneyFormat.kt` (monto con moneda y signo según el tipo, con la configuración regional), formato de día y de mes, `MemberLabel.kt` (resuelve `recordedBy`: "Tú", nombre, "Ex-miembro", "Miembro de la familia" o no disponible; iniciales; rol) y `MovementTexts.kt` (texto de cada `MovementFailure`). Cadenas `movements_*` solo de lo que se usa.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.movements.*"` con `MoneyFormatTest`, `MemberLabelTest` y `MovementTextsTest`: CLP y USD, gasto con signo negativo e ingreso con positivo, cada caso de quien registró (incluida la lista no disponible), iniciales de uno y dos nombres, cada fallo con su texto.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-14 · Estado y ViewModel del listado**
  - **Cubre:** RF-01 a RF-03, RF-13, RF-15, RF-17 (CA-01 a CA-03, CA-16, CA-21, CA-26) · **Depende de:** T-09, T-05, T-13
  - **Alcance:** `ui/movements/MovementsUiState.kt` y `MovementsViewModel.kt` (clave `sesión-familia`): mes mostrado (en curso al abrir; anterior y siguiente, sin pasar del actual), filtro de tipo que se mantiene al cambiar de mes, combinación de movimientos con categorías, medios de pago, miembros y sesión en filas ya resueltas, grupos por día con subtotal de lo visible, resumen del mes con todos, estados de carga, error, vacío y datos antiguos; refresca al abrir, al cambiar de mes, en `onStart()` y al recuperar conectividad.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.movements.MovementsViewModelTest"`: mes inicial y navegación; filtro y su persistencia; grupos y orden; subtotales con cada filtro; resumen estable con el filtro; resumen y subtotales tras alta, edición y eliminación; carga, error con reintento, vacío (mes sin movimientos y filtro sin resultados), datos antiguos; categoría, medio o miembro no encontrados; refrescos.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-15 · Pantalla del listado**
  - **Cubre:** RF-01 a RF-03, RF-15, RF-17 (CA-01 a CA-03, CA-24, CA-26) · **Depende de:** T-12, T-14
  - **Alcance:** `ui/movements/MovementsScreen.kt` (entrada con estado) y `MovementsContent.kt` (sin estado), según `007-financial-item-list.png` con las exclusiones acordadas: tarjeta de resumen, flechas de mes, filtro de tipo, grupos por día con subtotal, filas (icono, concepto, categoría y etiqueta, quién registró, monto con signo), vacío, carga, error con "Reintentar" y `StaleDataBanner`. `LazyColumn` con claves estables. Sustituye la pantalla provisional de la pestaña.
  - **Validación:** `MovementsContentTest` (UI): resumen; flecha siguiente deshabilitada en el mes actual; filtro; grupos en orden con subtotal; fila con todos sus datos y descripción accesible que incluye el tipo; "Tú" y "Ex-miembro"; sin correo; vacío, carga, error y aviso; conceptos largos sin romper la fila; áreas de 48 dp; recreación con `StateRestorationTester`.
  - **Hecho cuando:** los tests pasan en emulador.
  - **Evidencia:** —

## Alta

- [ ] **T-16 · Formulario en el ViewModel (alta)**
  - **Cubre:** RF-05 a RF-07, RF-14, RF-16 (CA-05 a CA-09, CA-17 a CA-19, CA-22, CA-23) · **Depende de:** T-09, T-13
  - **Alcance:** `ui/movements/MovementFormUiState.kt` y `MovementFormViewModel.kt`: tipo (Gasto de entrada), monto con las reglas de `MoneyInput` y la moneda de la familia, concepto, categorías activas del tipo, etiquetas activas de la categoría (una o ninguna), medio de pago propuesto con el último usado si sigue activo, fecha con "Hoy", "Ayer" y otra no futura, observaciones; cambiar el tipo o la categoría quita lo dependiente; guardar valida y muestra errores por campo sin llamar al backend; éxito cierra; cada fallo de la tabla del plan; una sola operación a la vez; cierre con confirmación de descarte si hay cambios; avisos cuando no hay categorías del tipo, medios activos o moneda.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.movements.MovementFormViewModelTest"`: valores iniciales; cada validación sin llamada; límites; decimales según la moneda; opciones por tipo y limpieza de dependientes; último medio propuesto, ausente y archivado; accesos rápidos de fecha y rechazo de futuro; alta correcta con los datos enviados; cada fallo con el texto conservado y las opciones refrescadas; sin conexión; dos pulsaciones → una llamada; descarte confirmado, cancelado y cierre directo; sin categorías, sin medios y sin moneda.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-17 · Pantalla del formulario**
  - **Cubre:** RF-05 a RF-07, RF-16 (CA-06, CA-07, CA-09, CA-19, CA-20, CA-23, CA-25) · **Depende de:** T-12, T-16
  - **Alcance:** `ui/movements/MovementFormScreen.kt` y `MovementFormContent.kt`, según `007-new-financial-item.png` con las exclusiones acordadas: "X", familia y moneda, selector Gasto / Ingreso, monto grande, concepto, cuadrícula de categorías con icono y nombre, chips de etiquetas, selector de medio de pago, fecha con accesos rápidos y selector de Material 3 sin días futuros, observaciones y "Guardar movimiento" con progreso. Desplazable y con `imePadding`. Diálogo de descarte. Sustituye la pantalla provisional de `Create`.
  - **Validación:** `MovementFormContentTest` (UI): elementos del formulario con familia y moneda; cambio de tipo; elegir categoría y etiqueta, y quitar la etiqueta; avisos junto a cada campo; límites de concepto y observaciones; accesos rápidos de fecha; guardado con progreso y acciones deshabilitadas; descarte con "X" y con atrás; sin categorías y sin medios; recreación con `StateRestorationTester` con el formulario a medio rellenar.
  - **Hecho cuando:** los tests pasan en emulador.
  - **Evidencia:** —

## Detalle y eliminación

- [ ] **T-18 · Detalle en el ViewModel**
  - **Cubre:** RF-08, RF-11, RF-14, RF-15 (CA-10, CA-14, CA-17, CA-18, CA-22, CA-24) · **Depende de:** T-09, T-13
  - **Alcance:** `ui/movements/MovementDetailViewModel.kt` y su estado: movimiento resuelto con categoría, etiqueta, medio de pago (indicando lo archivado) y quién lo registró con su rol; pedir, confirmar y cancelar la eliminación; éxito cierra; `NotFound` avisa, cierra y refresca; sin conexión y demás fallos avisan sin cambios; una sola operación a la vez; si el movimiento desaparece, el detalle se cierra.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.movements.MovementDetailViewModelTest"`: datos resueltos, también con elementos archivados o no encontrados; cada caso de quien registró; cancelar no llama; confirmar llama una vez; dos confirmaciones → una llamada; cada fallo; el movimiento desaparece.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-19 · Pantalla del detalle**
  - **Cubre:** RF-08, RF-11, RF-15 (CA-10, CA-14, CA-24) · **Depende de:** T-12, T-18
  - **Alcance:** `ui/movements/MovementDetailScreen.kt` y `MovementDetailContent.kt`, según `007-financial-item-detail.png` con las exclusiones acordadas: flecha, monto con signo y moneda, concepto, medio de pago, categoría, etiqueta, fecha, quién lo registró (iniciales, nombre, rol; sin correo), observaciones, "Editar movimiento" y "Eliminar movimiento" con confirmación, aviso de fallos y progreso. Sustituye la pantalla provisional de `Detail` y se abre al tocar una fila.
  - **Validación:** `MovementDetailContentTest` (UI): todos los datos; archivados indicados; sin etiqueta y sin observaciones; "Tú", otro miembro con rol, "Ex-miembro" sin rol y dato no disponible; nunca el correo; confirmar y cancelar la eliminación; acciones deshabilitadas en curso; aviso de fallo.
  - **Hecho cuando:** los tests pasan en emulador.
  - **Evidencia:** —

## Edición

- [ ] **T-20 · Formulario en el ViewModel (edición)**
  - **Cubre:** RF-09, RF-10 (CA-11 a CA-13, CA-22) · **Depende de:** T-16, T-18
  - **Alcance:** En `MovementFormViewModel`: modo edición que carga el movimiento, con el tipo fijo; categorías activas del mismo tipo más la actual aunque esté archivada (igual con la etiqueta y el medio de pago, que se conservan mientras no se cambien); al guardar envía solo lo modificado (la fecha, solo si cambió el día); sin cambios cierra sin llamar; resultado parcial: informa de lo guardado, sigue abierto y un nuevo guardado solo envía lo pendiente; `NotFound` cierra y avisa; el descarte compara con el movimiento original.
  - **Validación:** `./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.ui.movements.MovementFormViewModelTest"`: carga de datos; tipo no modificable; sin cambios no llama; cada campo por separado envía solo ese campo; fecha sin cambio no se envía; borrar las observaciones; medio archivado conservado y sustituido solo por activos; categoría archivada conservada; cambio de categoría del mismo tipo y de etiqueta; datos y categoría a la vez; parcial y reintento de lo pendiente; cada fallo; descarte.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —
- [ ] **T-21 · Formulario en modo edición**
  - **Cubre:** RF-09, RF-10 (CA-11 a CA-13) · **Depende de:** T-17, T-19, T-20
  - **Alcance:** `MovementFormContent` en modo edición: título "Editar movimiento", selector de tipo deshabilitado, elementos archivados indicados, aviso de guardado parcial. Sustituye la pantalla provisional de `Edit`; "Editar movimiento" del detalle la abre y, al guardar o cerrar, se vuelve al detalle actualizado.
  - **Validación:** `MovementFormContentTest` (UI): datos cargados; tipo deshabilitado; archivados indicados; aviso de parcial; descarte. Tests de UI de `ui.main`: detalle → editar → atrás vuelve al detalle.
  - **Hecho cuando:** los tests pasan en emulador.
  - **Evidencia:** —

## Validación

- [ ] **T-22 · Comprobaciones automáticas completas**
  - **Cubre:** todos · **Depende de:** T-10, T-15, T-21
  - **Alcance:** suite completa, incluida la regresión de cabecera, categorías, medios de pago, sesión, `MainScreenTest`, `MainScreenBehaviorTest` y `AppRootTest`. Lint sin avisos nuevos: cada cadena `movements_*` se usa o se elimina, sin `tools:ignore`; la línea base (24 avisos) no se toca.
  - **Validación:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` y `./gradlew :app:connectedDebugAndroidTest`; lectura de `app/build/reports/lint-results-debug.xml`.
  - **Hecho cuando:** todo pasa o cada fallo queda registrado con su causa, y lint no supera la línea base.
  - **Evidencia:** —
- [ ] **T-23 · Pruebas manuales contra el backend**
  - **Cubre:** CA-01 a CA-05, CA-09 a CA-11, CA-13, CA-14, CA-16, CA-17, CA-20, CA-21, CA-26 · **Depende de:** T-22
  - **Alcance:** emulador con backend local, solo con movimientos de prueba creados para ello y eliminados al terminar: alta de un gasto y de un ingreso, validaciones, cambio de mes, filtro, resumen y subtotales, detalle, edición de datos y de categoría, cambio de fecha a otro mes, eliminación, dos familias, modo avión en la consulta (mes visto y no visto) y en cada acción, segundo plano y recreación con el formulario abierto, teclado sin tapar el campo ni "Guardar", tamaño de fuente máximo y TalkBack. Comprobar el prefijo real de los códigos de error y el día guardado según la zona horaria (BG-07, BG-09). CA-15 y los casos de otro miembro y "Ex-miembro" de CA-24, solo si hay una segunda cuenta en la familia; si no, se anotan como no verificados.
  - **Validación:** pasos manuales registrados con el resultado observado y capturas.
  - **Hecho cuando:** cada escenario tiene resultado o motivo de bloqueo.
  - **Evidencia:** —
- [ ] **T-24 · Documentación y revisión final**
  - **Cubre:** CA-01 a CA-26 · **Depende de:** T-23
  - **Alcance:** `AGENTS.md` (capas de movimientos, miembros y dinero; moneda de la familia; pestaña en el ViewModel; rutas de movimientos; cachés nuevas), `docs/ROADMAP.md` (estado y notas), `BACKEND_GAPS.md` (lo observado en ejecución) y revisión de coherencia entre SPEC, PLAN y TASKS.
  - **Validación:** revisión documental: cada criterio con evidencia o marcado como no verificado; rutas y nombres citados existen.
  - **Hecho cuando:** documentos coherentes con lo implementado.
  - **Evidencia:** —

## Cobertura de criterios

| Criterio | Tareas |
| --- | --- |
| CA-01 | T-08, T-14, T-15, T-23 |
| CA-02 | T-08, T-13, T-14, T-15, T-23 |
| CA-03 | T-14, T-15, T-23 |
| CA-04 | T-12, T-23 |
| CA-05 | T-09, T-16, T-23 |
| CA-06 | T-01, T-03, T-16, T-17 |
| CA-07 | T-16, T-17 |
| CA-08 | T-07, T-09, T-16 |
| CA-09 | T-03, T-16, T-17, T-23 |
| CA-10 | T-18, T-19, T-23 |
| CA-11 | T-09, T-20, T-21, T-23 |
| CA-12 | T-09, T-20, T-21 |
| CA-13 | T-09, T-20, T-21, T-23 |
| CA-14 | T-09, T-18, T-19, T-23 |
| CA-15 | T-23 |
| CA-16 | T-07, T-08, T-14, T-23 |
| CA-17 | T-09, T-16, T-18, T-23 |
| CA-18 | T-16, T-18 |
| CA-19 | T-16, T-17, T-20 |
| CA-20 | T-11, T-15, T-17, T-23 |
| CA-21 | T-12, T-14, T-23 |
| CA-22 | T-06, T-16, T-18, T-20 |
| CA-23 | T-16, T-17 |
| CA-24 | T-05, T-13, T-15, T-18, T-19 |
| CA-25 | T-04, T-17 |
| CA-26 | T-01, T-14, T-15, T-23 |
