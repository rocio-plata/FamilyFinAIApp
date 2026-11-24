# SPEC: Gestión de familias

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

Quien usa la app puede pertenecer a varias familias y compartir su economía con
otras personas. Esta funcionalidad le permite crear familias, ordenarlas y
elegir cuál se abre primero, ver quién pertenece a la familia activa, responder
a las invitaciones que recibe y salir de una familia. El Propietario puede
además invitar, cambiar roles, quitar miembros y elegir la moneda. Completa lo
que la cabecera (002) solo muestra.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

El selector de la cabecera (002) permite cambiar de familia, pero no se abre si
solo hay una, y no permite crear ni ordenar. La campana muestra cuántas
invitaciones pendientes hay, sin acción al tocarla. La lista de miembros (007)
solo se usa para mostrar quién registró un movimiento, sin leer correos. La
familia nace en CLP y su moneda no se puede cambiar desde la app.

Debe conservarse: la familia activa solo en memoria y la favorita (primera de
la lista) al reabrir la app, las cinco pestañas, las gestiones de categorías
(005) y medios de pago (006), que impiden cambiar de familia mientras están
abiertas, los movimientos (007) y el dashboard (008), incluido «Ex-miembro», y
el comportamiento de sesión de la 003.

**Reglas de la 002 que esta spec modifica:** RF-02 y CA-19 (el selector se abre
también con una sola familia), RF-04 y CA-06 (tocar la campana abre las
invitaciones), y el supuesto de que el nombre de familia no se repite ni lleva
emojis: la app lo exige al crear, pero no lo garantiza en las familias a las que
se entra por invitación. También amplía la 007: el Propietario ve el correo de
los miembros en «Mi familia», sin que se guarde; el detalle de un movimiento
sigue sin mostrarlo. Esos documentos se actualizaron al aprobarse esta spec
(2026-11-22).

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

**Selector de familias**

- **RF-01:** El selector de la cabecera se abre también con una sola familia.
  Cada fila muestra el nombre, el rol del usuario y el número de miembros, con
  la activa marcada; debajo de la lista está «Crear nueva familia». Elegir otra
  familia funciona como hasta ahora.
- **RF-02:** Con dos o más familias, cada fila tiene un asa para reordenarlas
  arrastrando, con una alternativa sin arrastre (subir/bajar) utilizable con
  lector de pantalla. El orden se guarda al soltar; la primera es la favorita,
  la que se abre al iniciar la app. Reordenar no cambia la familia activa. Si
  no se puede guardar, vuelve el orden anterior y se informa.
- **RF-03:** Solo la fila de la familia activa tiene el icono de gestión, que
  abre «Mi familia» a pantalla completa, sin cabecera ni barra de pestañas, con
  una flecha para volver a la pestaña de origen.

**Crear familia**

- **RF-04:** «Crear nueva familia» abre a pantalla completa un formulario con un
  único campo, el nombre, con contador «n / 60». Indica que el nombre lo verán
  los miembros y que la familia nace en CLP y su moneda se puede cambiar antes
  del primer movimiento.
- **RF-05:** Al crearse con éxito, el usuario es su Propietario, la familia pasa
  a ser la activa y se abre su «Mi familia»; volver atrás lleva a Inicio de la
  nueva familia.

**Mi familia**

- **RF-06:** «Mi familia» muestra el nombre, la moneda y el número de miembros
  de la familia activa, y la lista de miembros con iniciales, nombre y rol, con
  «Tú» en el propio. Incluye una explicación breve de qué puede hacer cada rol.
- **RF-07:** El Propietario ve además el correo de cada miembro. El correo no se
  guarda en el dispositivo: sin conexión no se muestra. El Integrante no lo ve
  nunca.
- **RF-08:** El Integrante ve la pantalla en solo lectura: no se le muestran las
  acciones sobre miembros, invitaciones ni moneda; solo «Salir de la familia».
- **RF-09:** El Propietario puede cambiar el rol de cualquier miembro entre
  Propietario e Integrante, incluido el suyo, con confirmación. Si deja de ser
  Propietario, su pantalla pasa a solo lectura y su rol se actualiza en la
  cabecera y el selector.
- **RF-10:** El Propietario puede quitar a otro miembro, con confirmación que
  advierte de que perderá el acceso y de que los movimientos que registró se
  conservan.
- **RF-11:** La familia conserva siempre al menos un Propietario: al único
  Propietario no se le ofrece dejar de serlo. Si aun así el backend lo rechaza,
  se informa de que antes debe haber otro Propietario.

**Salir de la familia**

- **RF-12:** Cualquier miembro, Propietario o Integrante, puede salir de la
  familia activa, con confirmación. Al salir, la familia desaparece del selector y la favorita pasa
  a ser la activa. No se puede salir de la única familia que se tiene, ni si se
  es su único Propietario: la acción aparece deshabilitada con el motivo.
  (El backend ya lo permite al Integrante desde el 2026-11-24: BG-01.)

**Moneda**

- **RF-13:** El Propietario puede cambiar la moneda de la familia entre CLP, USD
  y EUR mientras no esté bloqueada. Si lo está, se muestra el motivo (hay
  movimientos, hay presupuestos activos o ambos) y no se ofrece cambiarla. Tras
  el cambio, toda la app usa la nueva moneda para esa familia.

**Invitaciones enviadas (solo Propietario)**

- **RF-14:** «Invitar miembro» abre un formulario con el correo y el rol
  (Integrante preseleccionado, o Propietario). El formulario y la confirmación
  informan de que no se envía ningún correo: la persona verá la invitación al
  entrar en la app con ese correo, y vence a los 7 días.
- **RF-15:** «Mi familia» lista las invitaciones pendientes de la familia con su
  correo, rol, fecha de vencimiento y cantidad. Una vigente se puede revocar,
  con confirmación.
- **RF-16:** Una invitación pendiente cuya fecha de vencimiento ya pasó se
  muestra como «Vencida», con «Volver a invitar» (abre el formulario con su
  correo y rol) y «Quitar», que la elimina de la lista. No se muestra si ese
  correo tiene otra invitación vigente o ya es de un miembro.
- **RF-17:** No se puede invitar a un correo con una invitación vigente: se
  avisa junto al campo de que antes hay que revocarla. Si el backend responde
  que ya es miembro, se avisa junto al campo. En ambos casos se conserva lo
  escrito.

**Invitaciones recibidas**

- **RF-18:** Tocar la campana abre «Invitaciones» a pantalla completa, también
  sin invitaciones pendientes. Cada una muestra el nombre de la familia, quién
  invita, el rol ofrecido y cuándo vence, con «Aceptar» y «Rechazar».
- **RF-19:** Al aceptar, la familia se añade a la lista del usuario, la
  invitación desaparece, el contador de la campana se actualiza y un aviso
  ofrece «Cambiar a esta familia». La familia activa no cambia salvo que se
  elija ese aviso, que la activa y lleva a Inicio.
- **RF-20:** Rechazar pide confirmación y advierte de que haría falta una nueva
  invitación. Al confirmarse, la invitación desaparece y el contador se
  actualiza.
- **RF-21:** Si al responder la invitación ya no existe, venció, dejó de estar
  pendiente o no es para esa cuenta, se informa del motivo y la lista se
  actualiza.

**Reglas comunes**

- **RF-22:** Si el backend rechaza una acción por rol insuficiente, se informa,
  se actualiza el rol del usuario y la pantalla pasa a solo lectura. Si responde
  que ya no pertenece a la familia, se informa, se cierra su gestión y la
  familia desaparece.
- **RF-23:** Cuando el usuario deja de pertenecer a una familia (sale, lo quitan
  o no aparece al actualizar la lista), se borra del dispositivo todo lo
  guardado de ella.
- **RF-24:** Si el usuario se queda sin familias (lo quitaron de la única), las
  pestañas muestran un mensaje con «Crear familia» y «Ver invitaciones».
- **RF-25:** Sin conexión, los miembros se consultan con la copia local ya
  existente y su aviso de antigüedad. Las invitaciones (enviadas y recibidas) y
  el bloqueo de la moneda requieren conexión y no se guardan. Toda acción que
  cambie datos requiere conexión: sin ella no se aplica, se informa y lo escrito
  se conserva.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Acordado:

- Renombrar y eliminar una familia (el backend no lo ofrece: BG-02).
- Elegir la moneda al crear la familia.
- Enviar o compartir avisos de invitación por correo u otras apps; unirse con
  un código o enlace.
- Elementos de los mockups que el backend no ofrece: icono, foto o etiqueta de
  familia, foto de los miembros, presupuesto estimado al crear, «Reenviar
  enlace», cupos y planes, fondo de ahorro, y en la invitación recibida los
  miembros, la moneda, la fecha de envío y el correo de quien invita.
- El conmutador «Vista Propietario / Vista Integrante» del mockup: la vista la
  determina el rol real.
- Copia local de las invitaciones y de los correos.

Pertenecen a otras specs: aviso al Propietario cuando aceptan o rechazan su
invitación, notificaciones push y lista general de notificaciones; datos
personales, ajustes y medio de pago predeterminado («Perfil y ajustes»).

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. Crear: abre el selector, toca «Crear nueva familia», escribe el nombre y
   confirma; ve «Mi familia» de la nueva y, al volver, Inicio con ella activa.
2. Ordenar: abre el selector y arrastra una familia (o usa subir/bajar); el
   orden queda guardado.
3. Gestionar: en el selector toca el icono de gestión de la activa y ve «Mi
   familia». Como Propietario invita, revoca, cambia roles, quita miembros o
   cambia la moneda; como Integrante consulta.
4. Responder: toca la campana y acepta o rechaza; si acepta, puede cambiar a esa
   familia desde el aviso.
5. Salir: en «Mi familia» toca «Salir de la familia» y confirma; vuelve a Inicio
   con la favorita activa.

Atrás desde un formulario vuelve a la pantalla que lo abrió; desde «Mi familia»
e «Invitaciones», a la pestaña de origen.

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- **Nombre de familia:** obligatorio, de 1 a 60 caracteres, sin espacios al
  inicio ni al final (se recortan), sin emojis y distinto, sin distinguir
  mayúsculas de minúsculas, del de las demás familias del usuario. El backend
  solo limita la longitud (BG-03): lo demás lo valida la app. Una invitación
  puede dar lugar a nombres repetidos y no se impide.
- **Roles:** Propietario (`OWNER`) e Integrante (`MEMBER`). Puede haber varios
  Propietarios. El backend es la autoridad del rol.
- **Explicación de roles:** Propietario: gestiona miembros, invitaciones, moneda,
  categorías y tags. Integrante: registra y consulta movimientos y gestiona
  medios de pago. El Propietario puede hacer también lo del Integrante.
- **Orden de los miembros:** primero el propio usuario, después los Propietarios
  y después los Integrantes, por nombre. Sin nombre: texto genérico («Miembro de
  la familia»), al final de su grupo.
- **Orden de las familias:** el del backend; una familia nueva o aceptada queda
  al final.
- **Correo de invitación:** obligatorio, recortado, con el mismo formato que el
  registro (003) y hasta 255 caracteres. Se compara sin distinguir mayúsculas.
  Puede no corresponder a una cuenta existente.
- **Invitación:** vence a los 7 días. El backend la mantiene como pendiente
  aunque haya vencido (BG-05): la app decide por su fecha de vencimiento, que se
  muestra en la zona y el formato del dispositivo. Las enviadas se ordenan por
  vencimiento: primero las vigentes que antes vencen y al final las vencidas.
- **Quien invita:** su nombre, o el texto genérico «Un miembro de la familia» si
  viene vacío.
- **Moneda:** CLP, USD o EUR. El bloqueo lo determina el backend.
- Tras cada cambio con éxito, las pantallas, la cabecera y el contador reflejan
  el resultado sin actualizar a mano.
- Mensajes de error según los códigos del contrato (§4 y §9), aceptando los que
  emite realmente el backend (BG-06).

## Comportamiento mobile y casos alternativos

<!-- Adapta la tabla usando MOBILE_GUIDELINES.md. Añade escenarios relevantes.
Marca No aplica con su motivo cuando corresponda. No presupongas soporte offline
ni conservación de todo el estado. Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Carga o acción en curso | Cada sección de «Mi familia» (miembros, invitaciones, moneda) e «Invitaciones» muestra su propio indicador en la primera carga. Durante una acción se muestra progreso y las acciones quedan deshabilitadas; pulsaciones repetidas no la duplican. |
| Sin datos | Sin invitaciones enviadas o recibidas: mensaje de vacío. Sin familias: RF-24. |
| Entrada inválida | Nombre vacío, con emojis o repetido; correo vacío, con formato inválido o ya invitado: al guardar se avisa junto al campo y no se envía. No se puede escribir más allá del límite. |
| Error o espera excesiva | Carga sin datos: error con «Reintentar» solo en la sección afectada. Acción: mensaje, se conserva lo escrito y se puede reintentar. Si no se sabe si un alta llegó a aplicarse (espera agotada), se vuelven a consultar los datos antes de permitir reintentar, para no duplicar la familia o la invitación. |
| Sin conexión o conexión interrumpida | RF-25. Al recuperar la conexión, los datos se actualizan. |
| Cancelar o volver atrás | Salir de un formulario con cambios sin guardar pide confirmación; sin cambios se cierra directamente. Cancelar una confirmación no cambia nada. Atrás con el selector abierto lo cierra. |
| Pasar a segundo plano y regresar | Se conservan la pantalla y lo escrito; los datos se actualizan al volver. Una acción en curso no se repite. |
| Recrear la pantalla | Se conservan la pantalla abierta, el formulario y lo escrito en él, y el diálogo de confirmación abierto. El selector abierto no es obligatorio conservarlo. |
| Reabrir después de terminarse el proceso | La app abre en Inicio con la favorita; no se conservan cambios sin guardar. |
| Sesión terminada durante una acción | Se aplica la 003: se vuelve al acceso y se borran los datos guardados. |
| Cambio de familia activa | No es posible dentro de «Mi familia». Si la familia deja de existir para el usuario mientras está abierta, se cierra y se informa (RF-22). |
| Cambios hechos por otra persona | Se ven al abrir la pantalla, al volver a la app y al recuperar la conexión; no hay actualización en tiempo real. Si un miembro o una invitación ya no existe al actuar, se informa y se actualiza la lista. |
| Otros puntos aplicables de la guía | Accesibilidad: roles, «Vencida», la familia activa y el motivo de una acción deshabilitada se anuncian y no dependen solo del color; las acciones de cada fila incluyen el nombre o correo al que se refieren; áreas táctiles de al menos 48 dp. Nombres y correos largos se truncan sin romper la fila, también con texto grande. El teclado no tapa el campo ni la acción. Listas desplazables. Textos traducibles. Privacidad: correos y nombres no aparecen en logs ni se guardan los correos. |

**Puntos de la guía no aplicables y motivo:** permisos y capacidades del
dispositivo (no se piden), trabajo en segundo plano (no hay tareas diferidas),
enlaces externos y notificaciones del sistema (fuera de alcance), orientación
(solo vertical, definido en 001).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Se trabaja en la rama `feature/009-family-management`.
- Los datos provienen del backend según `docs/backend/api-contract.md` §3.3 y
  `docs/backend/openapi.yaml`; las brechas están en `BACKEND_GAPS.md`. Solo RF-12
  dependía de un cambio en el backend (BG-01, resuelta el 2026-11-24).
- Referencia visual de estilo y disposición, no vinculante:
  `docs/ui/snapshots/009-selector-familia.png`, `009-nueva-familia.png`,
  `009-gestion-familia.png` y `009-notificaciones-familias.png`, junto con
  `docs/DESIGN.md`. Prevalece esta spec: los títulos «Add Expense» y la barra
  inferior en inglés de los mockups no aplican.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dado un usuario con una o varias familias, cuando toca la cabecera, entonces se abre el selector con nombre, rol y número de miembros de cada una, la activa marcada y «Crear nueva familia».
- **CA-02 · RF-02:** Dadas varias familias, cuando mueve una (arrastrando o con subir/bajar), entonces el orden se conserva al reabrir el selector y la app, la primera es la que se abre al iniciar y la activa no cambia; con una sola familia no hay asa.
- **CA-03 · RF-02:** Dado un fallo al guardar el orden, entonces vuelve el orden anterior y se informa.
- **CA-04 · RF-03:** Dado el selector, entonces solo la activa tiene el icono de gestión; al tocarlo ve «Mi familia» sin cabecera ni pestañas, y la flecha o el gesto de atrás regresan a la pestaña de origen.
- **CA-05 · RF-04:** Dado el formulario de alta, entonces muestra el contador «n / 60» y la información sobre la moneda; con nombre vacío o solo espacios, con emojis, o igual al de otra de sus familias (también con otras mayúsculas), al crear se avisa junto al campo y no se envía; no se pueden escribir más de 60 caracteres.
- **CA-06 · RF-05:** Dado un nombre válido, cuando crea, entonces ve «Mi familia» de la nueva como Propietario; al volver está en Inicio con ella activa, y aparece al final del selector.
- **CA-07 · RF-06:** Dada una familia con varios miembros, cuando abre «Mi familia», entonces ve nombre, moneda, número de miembros y la lista en el orden definido, con «Tú» en el propio, texto genérico en quien no tiene nombre, y la explicación de los roles.
- **CA-08 · RF-07:** Dado un Propietario con conexión, entonces ve el correo de cada miembro; sin conexión ve los miembros sin correo, y en el almacenamiento de la app no hay correos de miembros. Un Integrante no los ve nunca.
- **CA-09 · RF-08:** Dado un Integrante, cuando abre «Mi familia», entonces ve los datos, ninguna acción sobre miembros, invitaciones o moneda, y «Salir de la familia».
- **CA-10 · RF-09:** Dado un Propietario, cuando cambia el rol de otro miembro y confirma, entonces la lista muestra el nuevo rol; si cancela, no cambia nada.
- **CA-11 · RF-09:** Dado un Propietario en una familia con otro Propietario, cuando pasa a ser Integrante y confirma, entonces la pantalla queda en solo lectura y la cabecera y el selector muestran «Integrante».
- **CA-12 · RF-10:** Dado un Propietario, cuando quita a otro miembro y confirma, entonces desaparece de la lista y sus movimientos se conservan como «Ex-miembro»; si cancela, no cambia nada.
- **CA-13 · RF-11:** Dado el único Propietario, entonces no se le ofrece dejar de serlo; si el backend rechaza un cambio por dejar la familia sin Propietario, se informa y nada cambia.
- **CA-14 · RF-12:** Dado un miembro que puede salir, cuando elige salir y confirma, entonces la familia desaparece del selector y Inicio muestra la favorita; si cancela, no cambia nada.
- **CA-15 · RF-12:** Dada su única familia, o una de la que es el único Propietario, entonces «Salir de la familia» aparece deshabilitado con el motivo.
- **CA-16 · RF-13:** Dada una familia sin movimientos ni presupuestos activos, cuando el Propietario elige otra moneda, entonces «Mi familia», Inicio, Movimientos y el alta de un movimiento usan la nueva.
- **CA-17 · RF-13:** Dada una familia con la moneda bloqueada, entonces ve el motivo y no puede cambiarla; si se bloquea entretanto y el backend rechaza el cambio, se informa y se conserva la moneda.
- **CA-18 · RF-14:** Dado un correo válido, cuando invita, entonces la invitación aparece en la lista con su rol y vencimiento, se informa de cómo la verá la persona y, en la cuenta invitada, aparece entre las recibidas.
- **CA-19 · RF-14/RF-17:** Dado un correo vacío, con formato inválido, con invitación vigente (también con otras mayúsculas) o de un miembro actual, cuando invita, entonces se avisa junto al campo y lo escrito se conserva.
- **CA-20 · RF-15:** Dada una invitación vigente, cuando la revoca y confirma, entonces desaparece de la lista y de las recibidas de la persona invitada; si cancela, no cambia nada.
- **CA-21 · RF-16:** Dada una invitación vencida, entonces se marca «Vencida»; «Volver a invitar» abre el formulario con su correo y rol y, al enviarse, solo se ve la nueva; «Quitar» la elimina de la lista. Una vencida de un correo que ya es de un miembro no se muestra.
- **CA-22 · RF-18:** Dada cualquier pestaña, cuando toca la campana, entonces ve «Invitaciones» con la familia, quién invita, el rol y el vencimiento de cada una, o el mensaje de vacío.
- **CA-23 · RF-19:** Dada una invitación, cuando la acepta, entonces desaparece, el contador baja, la familia aparece en el selector, la activa no cambia y el aviso «Cambiar a esta familia» la activa y lleva a Inicio.
- **CA-24 · RF-20:** Dada una invitación, cuando la rechaza y confirma, entonces desaparece y el contador baja; si cancela, no cambia nada.
- **CA-25 · RF-21:** Dada una invitación revocada, vencida o ya respondida en otro dispositivo, cuando responde, entonces se informa del motivo y la lista se actualiza.
- **CA-26 · RF-22:** Dado un usuario que la app cree Propietario pero ya no lo es, cuando intenta un cambio, entonces se le informa, nada cambia, la pantalla queda en solo lectura y su rol se actualiza; si ya no es miembro, se le informa, se cierra la pantalla y la familia desaparece.
- **CA-27 · RF-23:** Dado un usuario que deja de pertenecer a una familia con datos guardados, entonces en el dispositivo no queda ningún dato de ella y los de las demás familias se conservan.
- **CA-28 · RF-24:** Dado un usuario sin familias, entonces las pestañas muestran el mensaje con «Crear familia» y «Ver invitaciones», y al crear o aceptar una puede usar la app con ella.
- **CA-29 · RF-25:** Dado el dispositivo sin conexión, entonces ve los miembros guardados con aviso si tienen más de 24 h, las invitaciones muestran error con «Reintentar», la moneda se ve sin poder cambiarla, y cada acción de cambio informa de que requiere conexión, no se aplica y conserva lo escrito.
- **CA-30 · RF-02/RF-05/RF-09/RF-10/RF-12 a RF-16/RF-19/RF-20:** Dada una acción en curso, cuando pulsa repetidamente, entonces se realiza una sola vez.
- **CA-31 · Mobile:** Dado un formulario con datos escritos, cuando se recrea la pantalla o la app pasa a segundo plano y regresa, entonces sigue abierto con lo escrito; al salir con cambios se pide confirmación, y sin cambios se cierra directamente.
- **CA-32 · Mobile:** Dado un lector de pantalla y el tamaño de texto máximo, cuando recorre el selector, «Mi familia», los formularios e «Invitaciones», entonces todo se anuncia con su etiqueta y nada queda cortado ni inaccesible.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Cuentas con una y con varias familias; tocar la cabecera | Selector abierto con los datos de cada familia |
| CA-02 | Reordenar de las dos formas; reabrir selector y app; cuenta con una familia | Orden conservado, favorita nueva, misma activa; sin asa con una |
| CA-03 | Backend con fallo al guardar el orden | Orden anterior y aviso |
| CA-04 | Abrir «Mi familia» desde dos pestañas y volver con flecha y gesto | Regreso a la pestaña de origen |
| CA-05 | Crear con cada nombre inválido y con más de 60 caracteres | Aviso junto al campo; sin envío |
| CA-06 | Crear con nombre válido y volver | «Mi familia»; después Inicio con la nueva activa |
| CA-07 | Familia con Propietarios, Integrantes y un miembro sin nombre | Datos y orden definidos |
| CA-08 | Propietario con y sin conexión; Integrante; inspeccionar lo guardado | Correos solo para el Propietario con conexión; nada guardado |
| CA-09 | Sesión de un Integrante | Solo lectura y «Salir de la familia» |
| CA-10 | Cambiar el rol de otro en ambos sentidos: confirmar y cancelar | Rol nuevo; sin cambios al cancelar |
| CA-11 | Dos Propietarios; uno se hace Integrante | Solo lectura; rol actualizado en cabecera y selector |
| CA-12 | Quitar a un miembro con movimientos: confirmar y cancelar | Fuera de la lista y «Ex-miembro»; sin cambios al cancelar |
| CA-13 | Único Propietario; y rechazo del backend | Acción no ofrecida; aviso sin cambios |
| CA-14 | Salir como Integrante y como Propietario con otro Propietario; cancelar | Familia fuera del selector y favorita activa; sin cambios al cancelar |
| CA-15 | Única familia; único Propietario con varias familias | Deshabilitado con cada motivo |
| CA-16 | Familia nueva: cambiar a USD; recorrer las pantallas | Todas en la nueva moneda |
| CA-17 | Familia con movimientos, con presupuestos y con ambos; bloqueo durante el cambio | Motivo correcto; aviso y moneda conservada |
| CA-18 | Invitar a una segunda cuenta y a un correo sin cuenta | En la lista; visible en la segunda cuenta |
| CA-19 | Invitar con cada correo inválido | Aviso junto al campo; texto conservado |
| CA-20 | Revocar: confirmar y cancelar; revisar la cuenta invitada | Desaparece en ambas; sin cambios al cancelar |
| CA-21 | Invitación vencida: volver a invitar y quitar; vencida de un miembro | Solo la nueva; desaparece; no se muestra |
| CA-22 | Tocar la campana con y sin invitaciones | Lista con sus datos; vacío |
| CA-23 | Aceptar; usar el aviso y no usarlo | Familia en el selector; activa solo si se usa el aviso |
| CA-24 | Rechazar: confirmar y cancelar | Desaparece; sin cambios al cancelar |
| CA-25 | Responder a una revocada, vencida y ya respondida | Motivo y lista actualizada |
| CA-26 | Otra sesión quita el rol o al miembro; intentar un cambio | Aviso y solo lectura; aviso y cierre |
| CA-27 | Salir y ser quitado de una familia con datos guardados; inspeccionar lo guardado | Sin datos de esa familia; las demás intactas |
| CA-28 | Quitar a un usuario de su única familia; crear y aceptar desde ahí | Mensaje con ambas acciones; app utilizable después |
| CA-29 | Sin conexión con miembros recientes, de más de 24 h y sin datos; intentar cada acción | Copia con aviso si procede; errores con «Reintentar»; nada se aplica |
| CA-30 | Pulsar repetidamente cada acción | Una sola operación |
| CA-31 | Recrear, pasar a segundo plano y salir con y sin cambios | Se conserva lo escrito; confirmación solo con cambios |
| CA-32 | TalkBack y fuente máxima en cada pantalla | Etiquetas anunciadas; sin cortes |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna. Confirmadas por la persona el 2026-11-22 e incorporadas al texto:

- **P-01:** Orden de los miembros y de las invitaciones enviadas (Datos y
  reglas).
- **P-02:** Un Propietario puede cambiar el rol de otro Propietario y quitarlo,
  porque el backend lo permite; todas las acciones sobre miembros y rechazar o
  revocar una invitación piden confirmación, y aceptar no.
- **P-03:** Se incluye la explicación de los roles del mockup, con un texto
  ajustado a lo que cada rol puede hacer hoy.
- **P-04:** Límite de 255 caracteres en el correo de la invitación (BG-07).
- **P-05:** Crear familia e «Invitaciones» son pantallas completas, como en los
  mockups; la campana abre la pantalla también sin invitaciones.
- **P-06:** El selector se abre siempre, también con una sola familia, para
  poder gestionarla (RF-01; modifica RF-02 y CA-19 de la 002).
- **P-07:** Un Integrante también puede salir de una familia (RF-12); el backend
  no lo permitía y se registró como BG-01 (resuelta en el backend el 2026-11-24).

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los puntos
mobile relevantes están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
