# SPEC: Movimientos

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

<!-- PARA LA PERSONA
Copia esta plantilla como SPEC.md en una carpeta de la funcionalidad.
Pide al agente que la complete contigo usando MOBILE_GUIDELINES.md.
SPEC.md define qué debe cumplirse; PLAN.md desarrolla cómo implementarlo;
TASKS.md organiza los pasos de ejecución.
-->

<!-- PARA EL AGENTE
- Lee las instrucciones del proyecto y MOBILE_GUIDELINES.md. Inspecciona el
  repositorio para comprobar el comportamiento actual. Si falta la guía, pide su ubicación.
- Completa esta spec con la persona: investiga lo comprobable y consulta las
  decisiones pendientes. Haz pocas preguntas por vez y actualiza las respuestas.
- No inventes requisitos ni exclusiones. Distingue propuestas de decisiones
  confirmadas y marca como PENDIENTE lo que aún no esté resuelto.
- Aplica las consideraciones mobile relevantes sin ampliar el alcance automáticamente.
- No incluyas diseño de clases, tablas, componentes, archivos o algoritmos:
  esos detalles pertenecen a PLAN.md. Sí registra restricciones explícitas del pedido.
- Mantén el documento breve y proporcional a la funcionalidad. Conserva los comentarios.
- Un documento completo no está aprobado automáticamente. Solicita aprobación
  antes de marcarlo como Aprobada. No implementes durante esta etapa.
-->

## Qué construimos y para quién

<!-- Qué necesidad resolvemos, quién tiene esa necesidad y qué podrá hacer.
Describe el objetivo en lenguaje de producto. -->

Los miembros de una familia necesitan registrar lo que gastan y lo que
ingresan, y revisar después lo registrado. Esta funcionalidad permite a
cualquier miembro registrar a mano un movimiento de la familia activa (un gasto
o un ingreso), ver los movimientos de la familia, consultar el detalle de uno y
corregirlo. Es el valor central de la app y la base del dashboard, los
presupuestos, los reportes y el registro con IA (specs futuras).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

La app tiene acceso (003), cabecera con familia activa (002), gestión de
categorías (005) y de medios de pago (006). La pestaña "Movimientos" existe
pero solo muestra un texto provisional, y el botón flotante "+" de Inicio no
hace nada. No hay forma de registrar ni de ver movimientos. El backend ya
ofrece crear, listar, editar, reclasificar y eliminar movimientos (contrato
§3.4.3).

Debe conservarse: la cabecera, las cinco pestañas, las secciones "Medios de
pago" y "Categorías" de Inicio con su gestión, la familia activa elegida en la
cabecera y el comportamiento de sesión de la 003.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

**Listado**

- **RF-01:** La pestaña "Movimientos" muestra los movimientos de la familia
  activa de un mes, con un selector para cambiar de mes; al abrirla muestra el
  mes en curso. No se pueden elegir meses futuros.
- **RF-02:** Los movimientos se muestran del más reciente al más antiguo,
  agrupados por día. Cada uno muestra su concepto, su categoría (y su
  etiqueta, si la tiene), el icono de la categoría, quién lo registró (RF-15)
  y el monto con la moneda de la familia, distinguiendo gasto de ingreso sin
  depender solo del color.
- **RF-17:** Sobre el listado se muestra el resumen del mes mostrado: balance
  (ingresos menos gastos), total de ingresos y total de gastos. La cabecera de
  cada día muestra el subtotal de ese día. El resumen del mes cuenta siempre
  todos los movimientos del mes; el subtotal de cada día, los movimientos
  visibles con el filtro de tipo aplicado.
- **RF-03:** El listado se puede filtrar por tipo: todos (de entrada), solo
  gastos o solo ingresos. El filtro se aplica al mes mostrado y se mantiene al
  cambiar de mes.

**Alta**

- **RF-04:** El botón "+" abre el formulario "Nuevo movimiento" de la familia
  activa. Está disponible en Inicio y en la pestaña "Movimientos", y no en las
  demás pestañas.
- **RF-05:** El formulario ocupa toda la pantalla, con una "X" para cerrarlo,
  y muestra el nombre de la familia y su moneda. Pide: tipo (Gasto, elegido de
  entrada, o Ingreso), monto, concepto (el título del movimiento), categoría,
  etiqueta (opcional), medio de pago, fecha y observaciones (opcional). Al
  guardarse con éxito se cierra y el movimiento aparece en el listado si
  pertenece al mes mostrado.
- **RF-06:** La categoría se elige en una cuadrícula con el icono y el nombre
  de las categorías activas del tipo elegido. Al elegir una, se muestran sus
  etiquetas activas y se puede elegir como máximo una, o ninguna. Cambiar el
  tipo quita la categoría y la etiqueta elegidas; cambiar la categoría quita
  la etiqueta.
- **RF-16:** La fecha viene con el día de hoy y ofrece los accesos rápidos
  "Hoy" y "Ayer", además de "Otra fecha" para elegir cualquier día no futuro.
- **RF-07:** El medio de pago es obligatorio y se elige entre los medios
  activos de la familia. El formulario propone de entrada el último que ese
  usuario usó en ese dispositivo para esa familia, si sigue activo; si no hay
  ninguno (primera vez o medio archivado), hay que elegirlo.

**Detalle, edición y eliminación**

- **RF-08:** Tocar un movimiento abre su detalle con todos sus datos y las
  acciones "Editar movimiento" y "Eliminar movimiento".
- **RF-15:** El listado y el detalle muestran quién registró el movimiento,
  con sus iniciales y su nombre, y la marca "Tú" si fue el propio usuario; el
  detalle muestra además su rol en la familia. Si quien lo registró ya no
  pertenece a la familia se muestra "Ex-miembro" (sin rol), y si su nombre
  viene vacío, un texto genérico ("Miembro de la familia"). No se muestra su
  correo.
- **RF-09:** Desde el detalle, "Editar" abre el mismo formulario del alta con
  los datos del movimiento. Se pueden cambiar el monto, el concepto, las
  observaciones, la fecha, el medio de pago, la categoría y la etiqueta. El
  tipo no se puede cambiar. Si el medio de pago del movimiento fue archivado
  después, se puede guardar el resto sin cambiarlo; solo si se cambia hay que
  elegir uno activo.
- **RF-10:** Al editar, la categoría solo puede cambiarse por otra activa del
  mismo tipo, y la etiqueta por una activa de la categoría elegida. Un
  movimiento con categoría o etiqueta archivadas las conserva mientras no se
  cambien. Si al guardar se aplica solo una parte de los cambios (los datos
  del movimiento o su categoría), se informa de qué se guardó y qué no, y el
  formulario sigue abierto con lo pendiente.
- **RF-11:** Desde el detalle se puede eliminar el movimiento, con
  confirmación.

**Permisos**

- **RF-12:** Cualquier miembro de la familia (Propietario o Integrante) puede
  realizar todas las acciones de esta spec sobre cualquier movimiento de la
  familia, lo haya registrado él o no.

**Datos sin conexión**

- **RF-13:** Se guarda en el dispositivo el último listado descargado de cada
  mes consultado de cada familia, para poder verlo sin conexión, con el mismo
  aviso de datos posiblemente desactualizados que categorías y medios de pago.
  Un mes nunca consultado no está disponible sin conexión. También se guarda
  la última lista de miembros descargada de la familia, para mostrar quién
  registró cada movimiento (RF-15).
- **RF-14:** Registrar, editar y eliminar requieren conexión: sin
  ella la acción no se aplica, se informa con un mensaje claro y lo escrito se
  conserva en pantalla.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Acordado:

- Filtrar el listado por categoría, etiqueta o medio de pago y buscar por texto
  (quedan para una spec posterior).
- Abrir el alta desde una categoría del resumen de Inicio: se valorará en la
  spec de Dashboard.
- Registrar la hora del movimiento.
- Tres elementos del mockup que dependen de funcionalidades que aún no
  existen: el texto del presupuesto junto a la familia, el avatar de perfil y
  la frase sobre la sincronización con el balance mensual. Tampoco se elige
  más de una etiqueta por movimiento (el backend admite una).
- Del mockup del listado: la búsqueda por texto, los filtros por categoría,
  "Familia" e IA, la hora de cada movimiento, la comparación con el mes
  anterior, la tarjeta del asistente de IA, las marcas "Compartido" y de
  verificado, y el conmutador "Con movimientos / Sin resultados".
- Del mockup del detalle: el estado "Registrado · Sincronizado", compartir, el
  avatar de perfil, la hora, la descripción bajo el medio de pago, más de una
  etiqueta y el correo de quien registró el movimiento.
- Registro por texto libre y por foto de recibo con IA (specs 12 y 13).
- Dashboard, presupuestos y reportes (specs 8, 10 y 11), salvo el resumen
  del mes del listado (RF-17).
- Elegir o ver el medio de pago predeterminado (spec de Perfil; BG-08).
- Hacer cambios sin conexión para enviarlos después.
- Cambiar la moneda de la familia.

No lo permite el backend (no es una decisión de esta spec; ver "Restricciones
del pedido"): movimientos con fecha futura, en una moneda distinta de la de la
familia, recurrentes o divididos en varias categorías.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. Alta: el usuario toca "+", elige el tipo, escribe el monto y el concepto,
   elige categoría (y etiqueta), medio de pago y fecha, y guarda; vuelve a
   donde estaba y el movimiento aparece en el listado.
2. Consulta: en la pestaña "Movimientos" ve los del mes en curso y puede
   cambiar de mes.
3. Detalle: toca un movimiento y ve todos sus datos.
4. Corrección: desde el detalle toca "Editar", cambia lo que necesite en el
   mismo formulario (también la categoría y la etiqueta) y guarda; o lo
   elimina tras confirmar. El listado queda actualizado.

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Movimiento: tipo (gasto o ingreso), monto, concepto, categoría, etiqueta (opcional),
  medio de pago, fecha y observaciones (opcional). Pertenece a la familia.
- Términos: "concepto" es el título del movimiento, "etiqueta" es el tag de
  la categoría y "observaciones" es la nota, como en la interfaz de la 005 y
  el mockup.
- Tipo: lo determina la categoría. El selector de tipo del formulario solo
  decide qué categorías se ofrecen.
- Monto: obligatorio, mayor que cero, en la moneda de la familia, que se
  muestra y no se puede cambiar. Con monedas sin decimales (CLP) no se admiten
  decimales; con USD y EUR, hasta dos.
- Concepto: obligatorio, de 1 a 100 caracteres. Observaciones: opcional, hasta
  500.
  Los espacios al inicio y al final se eliminan.
- Categoría: obligatoria y activa. Tag: opcional, activo y de esa categoría.
- Medio de pago: obligatorio y activo. El último usado se recuerda por
  usuario, familia y dispositivo, y se olvida al terminar la sesión.
- Fecha: obligatoria; se registra solo el día, sin hora. De entrada es el día
  de hoy según el dispositivo, y no puede ser futura.
- Reclasificar solo es posible hacia una categoría del mismo tipo: un gasto no
  puede pasar a ser ingreso ni al revés.
- Un movimiento conserva su categoría, su etiqueta y su medio de pago aunque se
  archiven después: el listado y el detalle los siguen mostrando.
- Quién lo registró: se obtiene de la lista de miembros de la familia. Si esa
  lista no se puede obtener (sin conexión y sin copia, o error), el listado y
  el detalle muestran el resto de datos; el detalle indica que ese dato no
  está disponible y las filas del listado lo omiten.
- Resumen y subtotales: se calculan con los movimientos del mes mostrado, con
  el mismo formato de dinero que los montos; un balance o subtotal negativo se
  distingue también sin el color.
- Orden del listado: por fecha, del más reciente al más antiguo; dentro de un
  mismo día, el registrado más recientemente primero.
- Copia local: por familia, usuario y mes, con la regla común de la app (aviso
  a partir de 24 h, descarte a los 7 días o si es de otro usuario, borrado al
  terminar la sesión).
- Tras cada cambio con éxito, el listado refleja el resultado sin que el
  usuario tenga que actualizar manualmente.
- Mensajes de error: según los códigos del contrato, aceptando el prefijo que
  emite realmente el backend (BG-07).

## Comportamiento mobile y casos alternativos

<!-- Adapta la tabla usando MOBILE_GUIDELINES.md. Añade escenarios relevantes.
Marca No aplica con su motivo cuando corresponda. No presupongas soporte offline
ni conservación de todo el estado. Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Carga o acción en curso | Al abrir el listado o cambiar de mes sin datos: indicador de carga. Durante un guardado o una eliminación se muestra progreso y la acción queda deshabilitada; pulsaciones repetidas no duplican la operación (un alta repetida crearía dos movimientos). |
| Sin datos | Mes sin movimientos: mensaje de vacío con invitación a registrar uno. Familia sin categorías activas del tipo elegido o sin medios de pago activos: el formulario lo explica e indica dónde crearlos. |
| Entrada inválida | Monto vacío o cero, concepto vacío, categoría o medio de pago sin elegir: al guardar se avisa junto a cada campo y no se guarda. No se puede escribir más allá de los límites ni elegir una fecha futura. |
| Error o espera excesiva | Listado sin datos: error con "Reintentar". En una acción: mensaje de error, se conserva lo escrito y se puede reintentar. Si la categoría, la etiqueta o el medio de pago elegidos ya no están disponibles, se informa y se actualizan las opciones. Si el movimiento ya no existe, se informa y se vuelve al listado actualizado. |
| Sin conexión o conexión interrumpida | Las acciones de cambio informan de que requieren conexión (RF-14). Consulta: los meses ya consultados se muestran desde la copia local, con aviso si es antigua; un mes sin copia muestra un error con "Reintentar". Al recuperar la conexión, los datos se actualizan. |
| Cancelar o volver atrás | Salir del formulario con cambios sin guardar pide confirmación; sin cambios se sale directamente. Cancelar la confirmación de eliminar no cambia nada. Atrás desde el detalle vuelve al listado. |
| Pasar a segundo plano y regresar | Se conserva la pantalla y lo escrito; el listado se actualiza al volver. |
| Recrear la pantalla | Se conservan el mes mostrado, la pantalla abierta (listado, detalle o formulario) y lo escrito y elegido en el formulario. |
| Reabrir después de terminarse el proceso | La app abre en Inicio; no se conservan cambios sin guardar. |
| Cambio de familia activa | El listado muestra los movimientos de la nueva familia. No es posible cambiar de familia con el formulario o el detalle abiertos. |
| Cambios hechos por otro miembro | Se ven al actualizarse el listado (al abrirlo, al volver a la app y al recuperar conexión); no hay actualización en tiempo real. |
| Otros puntos aplicables de la guía | Teclado numérico para el monto; el teclado no tapa el campo activo ni la acción de guardar. Montos y fechas con el formato regional del dispositivo. Gasto e ingreso se distinguen también por texto o signo, no solo por color. Áreas táctiles de al menos 48 dp, conceptos largos truncados sin romper la fila, también con texto grande. Lista desplazable y fluida con un mes de muchos movimientos. Textos traducibles. Sin montos ni conceptos en logs. |

**Puntos de la guía no aplicables y motivo:** permisos y capacidades del
dispositivo (no se piden; la cámara es de la spec de recibos), trabajo en
segundo plano (no hay tareas diferidas), orientación (solo vertical, definido
en 001).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Solo se construye la app (frontend), según `docs/backend/api-contract.md`
  §3.4.3 y `docs/backend/openapi.yaml`. No se piden cambios al backend; sus
  brechas están en `BACKEND_GAPS.md` de esta carpeta.
- El dinero se maneja como entero en la unidad menor de la moneda, nunca con
  decimales binarios (`AGENTS.md`, contrato §1).
- Referencia visual, con las exclusiones acordadas en "Fuera de alcance":
  `docs/ui/snapshots/007-new-financial-item.png` (formulario),
  `007-financial-item-list.png` (listado) y `007-financial-item-detail.png`
  (detalle), además de `docs/DESIGN.md`. Se conservan la cabecera de familia y
  el botón "+" actuales aunque el mockup del listado no los muestre. Se
  mantiene el texto "Medio de pago" de la 006,
  aunque el mockup diga "Método de pago".
- Reglas que impone el backend y la app no puede cambiar: la fecha de un
  movimiento no puede ser futura (BG-11, anotada como brecha a pedir), su
  moneda es la de la familia, y no existen movimientos recurrentes ni
  divididos en varias categorías.
- El formulario de movimiento debe poder reutilizarse en el registro con IA
  (`docs/ROADMAP.md`, paso 7).

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dado el usuario en la pestaña "Movimientos", entonces ve los movimientos del mes en curso de la familia activa; cuando elige un mes anterior, ve los de ese mes; no puede elegir un mes posterior al actual.
- **CA-02 · RF-02:** Dado un mes con movimientos de varios días, entonces aparecen agrupados por día, del más reciente al más antiguo, y cada uno muestra concepto, categoría (y etiqueta si tiene), icono de la categoría, quién lo registró y monto con la moneda de la familia; un gasto y un ingreso se distinguen también sin el color.
- **CA-03 · RF-03:** Dado un mes con gastos e ingresos, cuando elige "Gastos" o "Ingresos", entonces solo ve los de ese tipo; con "Todos" ve ambos; el filtro se mantiene al cambiar de mes.
- **CA-04 · RF-04:** Dado el usuario en Inicio o en "Movimientos", cuando toca "+", entonces se abre "Nuevo movimiento" de la familia activa; en las demás pestañas el botón no aparece.
- **CA-05 · RF-05:** Dado el formulario con datos válidos, cuando guarda, entonces se cierra y el movimiento aparece en el listado del mes de su fecha, en su día.
- **CA-06 · RF-05:** Dado el formulario con el monto vacío o en cero, el concepto vacío o solo espacios, o sin categoría o sin medio de pago, cuando intenta guardar, entonces no se guarda y ve el aviso junto a cada campo afectado; no puede escribir más de 100 caracteres en el concepto ni más de 500 en las observaciones, ni decimales que la moneda no admite.
- **CA-07 · RF-06:** Dado el formulario, entonces la cuadrícula solo ofrece categorías activas del tipo elegido, con icono y nombre, y al elegir una aparecen sus etiquetas activas, de las que se puede elegir una o ninguna; cuando cambia el tipo, la categoría y la etiqueta elegidas se quitan; cuando cambia la categoría, la etiqueta se quita.
- **CA-08 · RF-07:** Dado un usuario que ya registró un movimiento en esa familia y dispositivo, cuando abre el formulario, entonces el medio de pago viene propuesto con el último usado; la primera vez, o si ese medio fue archivado, viene sin elegir.
- **CA-09 · RF-16:** Dado el formulario, entonces la fecha viene con el día de hoy; "Ayer" la cambia al día anterior y "Hoy" la devuelve a hoy; con "Otra fecha" puede elegir cualquier día pasado y ninguno futuro.
- **CA-10 · RF-08:** Dado el listado, cuando toca un movimiento, entonces ve su tipo, monto, concepto, categoría, etiqueta, medio de pago, fecha, observaciones y quién lo registró; si la categoría, la etiqueta o el medio de pago están archivados, se indica.
- **CA-11 · RF-09:** Dado el detalle, cuando toca "Editar", entonces se abre el formulario con los datos del movimiento y el tipo sin poder cambiarse; cuando cambia el monto, el concepto, las observaciones, la fecha o el medio de pago y guarda, entonces el detalle y el listado muestran los nuevos datos; si cambió la fecha a otro mes, deja de aparecer en el mes mostrado.
- **CA-12 · RF-09:** Dado un movimiento cuyo medio de pago está archivado, cuando edita otro dato sin cambiar el medio y guarda, entonces se guarda; si decide cambiarlo, solo puede elegir uno activo.
- **CA-13 · RF-10:** Dado un gasto en edición, entonces solo puede elegir categorías de gasto activas y sus etiquetas activas, y tras guardar el movimiento muestra la nueva categoría; lo mismo para un ingreso con categorías de ingreso. Si solo se aplica una parte de los cambios, se le informa de qué se guardó y qué no, y el formulario sigue abierto.
- **CA-14 · RF-11:** Dado el detalle, cuando elige eliminar y confirma, entonces vuelve al listado y el movimiento ya no aparece; si cancela, no cambia nada.
- **CA-15 · RF-12:** Dado un Integrante, entonces dispone de las mismas acciones que el Propietario, también sobre movimientos registrados por otro miembro.
- **CA-16 · RF-13:** Dado un mes ya consultado, cuando lo abre sin conexión, entonces ve sus movimientos, con aviso si los datos tienen más de 24 h; un mes nunca consultado muestra un error con "Reintentar".
- **CA-17 · RF-14:** Dado el dispositivo sin conexión, cuando intenta registrar, editar o eliminar, entonces la acción no se aplica, se le informa y lo escrito se conserva.
- **CA-18 · RF-05/RF-09/RF-11:** Dada una acción en curso, cuando pulsa repetidamente, entonces la operación se realiza una sola vez (un alta no crea dos movimientos).
- **CA-19 · RF-05/RF-09:** Dado el formulario con cambios sin guardar, cuando intenta salir, entonces se le pide confirmación; si confirma sale sin guardar y si no, permanece con lo escrito. Sin cambios, sale directamente.
- **CA-20 · Mobile:** Dado el formulario con datos escritos y elegidos, cuando se recrea la pantalla o la app pasa a segundo plano y regresa, entonces sigue abierto con los mismos datos; el listado conserva el mes y el filtro.
- **CA-21 · RF-01:** Dado el usuario que elige otra familia en la cabecera, cuando abre "Movimientos", entonces ve los movimientos de la nueva familia.
- **CA-22 · RF-05/RF-09/RF-10:** Dada una categoría, una etiqueta o un medio de pago que dejó de estar disponible mientras se editaba, cuando guarda, entonces se le informa, lo escrito se conserva y las opciones se actualizan; si el movimiento ya no existe, se le informa y vuelve al listado actualizado.
- **CA-23 · RF-05:** Dada una familia sin categorías activas del tipo elegido o sin medios de pago activos, cuando abre el formulario, entonces se le explica que debe crearlos y dónde.
- **CA-24 · RF-15:** Dado un movimiento en el listado o en su detalle, entonces muestra las iniciales y el nombre de quien lo registró, con "Tú" si fue el propio usuario; "Ex-miembro" si ya no pertenece a la familia; un texto genérico si su nombre viene vacío; el detalle muestra además su rol y en ningún caso su correo. Si la lista de miembros no se pudo obtener, el detalle indica que el dato no está disponible y el listado lo omite, sin impedir ver el resto.
- **CA-25 · RF-05:** Dado el formulario de alta, entonces ocupa toda la pantalla, muestra el nombre de la familia activa y su moneda, y la "X" lo cierra (con confirmación si hay cambios, CA-19).
- **CA-26 · RF-17:** Dado un mes con gastos e ingresos, entonces el resumen muestra el total de ingresos, el total de gastos y su diferencia como balance, y cada día muestra su subtotal; con el filtro "Gastos" o "Ingresos" el resumen del mes no cambia y los subtotales de cada día reflejan solo los movimientos visibles; tras registrar, editar o eliminar un movimiento, resumen y subtotales se actualizan.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Abrir "Movimientos"; cambiar a un mes anterior; intentar avanzar más allá del actual | Mes en curso de entrada; movimientos del mes elegido; no admite meses futuros |
| CA-02 | Mes con gastos e ingresos en varios días, con y sin etiqueta | Grupos por día en orden descendente; datos de cada fila; tipo distinguible sin color |
| CA-03 | Elegir "Gastos", "Ingresos" y "Todos"; cambiar de mes con un filtro puesto | Solo el tipo elegido; el filtro se mantiene |
| CA-04 | Tocar "+" en Inicio y en "Movimientos"; revisar las otras pestañas | Se abre el formulario; sin botón en las demás |
| CA-05 | Registrar un movimiento válido con fecha del mes mostrado y otro de un mes anterior | Aparece en su día; el otro, en su mes |
| CA-06 | Guardar con cada campo obligatorio vacío; monto en cero; superar los límites; decimales en CLP | Avisos junto a cada campo; no se guarda; límites respetados |
| CA-07 | Ver la cuadrícula con cada tipo; elegir categoría y etiqueta; cambiar tipo y categoría con valores elegidos | Solo opciones activas del tipo y de la categoría; una etiqueta como máximo; se quitan los valores dependientes |
| CA-08 | Primer alta; segunda alta; alta tras archivar el último medio usado | Sin propuesta; con el último usado; sin propuesta |
| CA-09 | Abrir el formulario; tocar "Ayer" y "Hoy"; con "Otra fecha" elegir un día pasado e intentar elegir mañana | Hoy de entrada; accesos rápidos; no admite días futuros |
| CA-10 | Abrir el detalle de un movimiento, también con categoría, etiqueta o medio archivados | Todos los datos y las acciones "Editar movimiento" y "Eliminar movimiento"; lo archivado se indica |
| CA-11 | Tocar "Editar"; cambiar cada dato; mover la fecha a otro mes | Formulario con los datos y tipo fijo; datos actualizados en detalle y listado; sale del mes mostrado |
| CA-12 | Editar un movimiento con medio archivado sin cambiarlo y cambiándolo | Se guarda; al cambiar solo ofrece activos |
| CA-13 | Cambiar la categoría de un gasto y de un ingreso al editar; provocar que solo se aplique una parte | Solo categorías activas del mismo tipo y sus etiquetas; nueva categoría visible; aviso de lo guardado y lo pendiente |
| CA-14 | Eliminar: confirmar y cancelar | Desaparece del listado; sin cambios al cancelar |
| CA-15 | Sesión de un Integrante sobre un movimiento de otro miembro | Mismas acciones que el Propietario |
| CA-16 | Sin conexión: mes consultado reciente, con más de 24 h y mes nunca consultado | Listado; listado con aviso; error con "Reintentar" |
| CA-17 | Sin conexión, intentar cada acción de cambio | No se aplica; mensaje; datos conservados |
| CA-18 | Pulsar repetidamente Guardar y la confirmación de eliminar | Una sola operación |
| CA-19 | Salir del formulario con y sin cambios (atrás y Cancelar): confirmar y no confirmar | Confirmación antes de descartar; salida directa sin cambios |
| CA-20 | Con el formulario a medio rellenar, recrear la pantalla y pasar a segundo plano | Formulario con los mismos datos; mes y filtro conservados |
| CA-21 | Elegir otra familia y abrir "Movimientos" | Movimientos de la nueva familia |
| CA-22 | Archivar la categoría, la etiqueta o el medio elegidos (desde otra sesión) antes de guardar; eliminar el movimiento antes de guardar su edición | Aviso, datos conservados y opciones actualizadas; aviso y regreso al listado |
| CA-23 | Familia sin categorías activas de un tipo; familia sin medios de pago activos | Explicación y dónde crearlos |
| CA-24 | Listado y detalle de un movimiento propio, de otro miembro, de alguien que ya no es miembro y de un miembro sin nombre; sin poder obtener los miembros | Iniciales y nombre con "Tú"; nombre; "Ex-miembro"; texto genérico; rol solo en el detalle y nunca el correo; dato no disponible con el resto visible |
| CA-25 | Abrir el alta desde Inicio y desde "Movimientos"; tocar la "X" | Pantalla completa con familia y moneda; se cierra |
| CA-26 | Mes con gastos e ingresos en varios días; aplicar cada filtro de tipo; registrar, editar y eliminar un movimiento | Totales y balance del mes correctos y estables con el filtro; subtotales por día de lo visible; todo actualizado tras cada cambio |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna. Resueltas con la persona: acciones incluidas (editar, también la categoría, y
eliminar), filtros (mes y tipo), medio de pago (se propone el último usado),
consulta sin conexión (meses ya consultados), botón "+" (Inicio y
"Movimientos"), fecha (solo el día), edición con elementos archivados y quién
registró el movimiento (en el listado y en el detalle, sin correo), resumen
del mes y subtotal por día, elementos de los mockups que se omiten y edición
en un solo formulario que incluye categoría y etiqueta.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los puntos
mobile relevantes están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
