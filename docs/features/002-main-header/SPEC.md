# SPEC: Cabecera principal operativa (selector de familia, notificaciones y perfil)

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

Los miembros de una o varias familias necesitan ver y cambiar en todo momento
qué familia están consultando, saber si tienen invitaciones pendientes y
reconocer su cuenta por la inicial de su nombre. Esta funcionalidad vuelve operativa la cabecera de la
app con datos reales del backend (`docs/backend/api-contract.md`). Referencia
visual: `docs/ui/snapshots/001-home.png` y `docs/ui/snapshots/002-header.png`
(esta última muestra el icono de cambios solo locales a la izquierda de
notificaciones).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

La cabecera (spec 001) se muestra en las 5 pestañas con datos estáticos de
ejemplo ("Familia Plata", "Propietario"); selector, notificaciones y avatar no
ejecutan acciones. La app no tiene acceso a red, sesión ni almacenamiento de
datos. Debe conservarse la cabecera visible en las 5 pestañas, sus áreas
táctiles, etiquetas accesibles e idioma (RF-02, RF-10, RF-11, RF-12 de la 001).

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Al abrir la app, la cabecera muestra la familia favorita del
  usuario: la primera de `GET /me/families` (orden definido por el usuario),
  con su nombre y, debajo, el rol del usuario en ella.
- **RF-02:** Tocar el selector abre una lista con todas las familias del
  usuario, en el orden del backend, mostrando nombre y rol de cada una y
  marcando la activa. Elegir una familia la convierte en la activa, actualiza
  la cabecera y cierra la lista. Si el usuario tiene una sola familia, no hay
  selector: la cabecera muestra su nombre y rol sin indicador de selector y
  tocarla no abre ninguna lista.
- **RF-03:** La familia activa se mantiene mientras el proceso de la app siga
  vivo; al reabrir la app tras terminarse el proceso, la activa vuelve a ser la
  favorita.
- **RF-04:** El icono de notificaciones muestra un contador con el número de
  invitaciones pendientes recibidas por el usuario (`GET /me/invitations`),
  siempre que sea distinto de 0 (sin límite superior). Con 0 no se muestra
  contador. Tocar el icono no navega ni ejecuta otra acción.
- **RF-05:** El icono de perfil muestra la inicial del nombre del usuario
  (`GET /me/profile`, `displayName`).
- **RF-06:** Tocar el icono de perfil no navega ni ejecuta ninguna acción: en
  esta spec solo muestra la inicial (RF-05).
- **RF-07:** La cabecera muestra los estados de carga y error de la carga de
  familias y permite reintentar (ver tabla mobile). Los demás comportamientos
  de la 001 se conservan.
- **RF-08:** Al cambiar de familia, la pestaña activa recibe la nueva familia
  y recarga su contenido con ella. Como verificación visible, el contenido de
  cada pestaña muestra su título actual (nombre de la pestaña) seguido del
  nombre de la familia activa (p. ej. "Movimientos · Familia Plata"). Al
  cambiar de pestaña, la nueva pestaña también refleja la familia activa.
  Inicio, hoy sin título en su cuerpo, muestra el mismo formato con "Inicio".
  El contador de invitaciones y el avatar son del usuario y no cambian al
  elegir otra familia.
- **RF-09:** La cabecera incluye, a la izquierda del icono de notificaciones,
  un icono que indica que hay cambios solo a nivel local (pendientes de
  sincronizar por falta de red). Solo se muestra cuando existen esos cambios;
  en caso contrario permanece oculto. Esta spec no genera cambios locales, por
  lo que el icono no se muestra aquí; qué lo activa, su acción al pulsarlo y la
  sincronización se definen en la spec de sincronización posterior.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Pantalla de perfil (nombre, email, ajustes, cerrar sesión) y la navegación
  hacia ella desde la cabecera: spec posterior. Aquí el icono de perfil solo
  muestra la inicial y no ejecuta ninguna acción al pulsarlo.
- Pantalla o lista de invitaciones, y aceptar o rechazar invitaciones: spec
  posterior. El icono de notificaciones solo muestra el contador.
- Lista de notificaciones, invitaciones enviadas por el OWNER y su estado
  (pendiente, rechazada, aceptada) y marcar como leída: spec de notificaciones
  posterior. El contador no incluye invitaciones enviadas.
- Crear familias, reordenarlas y gestionar miembros desde el selector.
- Login, registro, renovación de tokens y cierre de sesión: se asume sesión ya
  iniciada y válida (confirmado).
- Permisos de notificaciones del sistema y notificaciones push.
- Sincronización de los cambios pendientes al recuperar la conexión, qué
  activa el icono de cambios solo locales, su acción al pulsarlo y resolución
  de conflictos cuando otro usuario haya modificado los datos: spec de
  sincronización posterior. Aquí solo se define su ubicación y visibilidad
  (RF-09).
- Datos de las pantallas distintas de la cabecera (cuerpo de Inicio y otras
  pestañas): siguen siendo placeholders; solo muestran título y nombre de la
  familia activa (RF-08). Qué mostrar en cada pestaña para la familia
  seleccionada se define en specs posteriores.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. El usuario abre la app y ve la cabecera con su familia favorita y, si hay
   invitaciones pendientes, un contador en el icono de notificaciones.
2. Toca el selector, ve sus familias y elige otra; la cabecera pasa a mostrarla
   y el contenido de la pestaña activa muestra su título seguido del nombre de
   la nueva familia.
3. Ve su inicial en el icono de perfil; tocarlo no hace nada.
4. Si en el futuro hubiera cambios solo locales, vería el icono de cambios
   locales a la izquierda de notificaciones; sin ellos no aparece (RF-09).

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Familia: nombre y rol del usuario (`OWNER` = "Propietario"; `MEMBER` =
  "Integrante"). El rol solo puede ser uno de esos dos. El orden de la lista es el del
  backend; la primera es la favorita. No existe un campo "favorita" propio.
- Las invitaciones pendientes son las del usuario autenticado. Se muestra su
  cantidad exacta cuando no es 0.
- Todo usuario tiene al menos una familia: al registrarse se crea su familia
  personal.
- La inicial del avatar es la primera letra de `displayName`, en mayúscula.
- El backend es la autoridad del rol y rechaza las acciones no permitidas. Si
  una operación sobre la familia activa responde `FAMILY.INSUFFICIENT_ROLE`,
  se vuelven a consultar las familias del usuario para actualizar su rol en la
  cabecera y la lista, sin cambiar la familia activa. Esta spec no tiene
  acciones restringidas por rol: la regla aplica a las specs que las
  introduzcan, y cada una define su criterio de aceptación.
- Cada familia se identifica por su identificador, no por su nombre. El nombre
  no se repite, no está vacío y no contiene emojis: esas validaciones se
  definen en la spec de creación de familias, por lo que aquí se asumen
  garantizadas.
- Regla para specs futuras: cuando haya cambios de datos, se envían al backend
  y a la memoria local a la vez. Si no se puede por problemas de red, se
  muestra el icono de cambios solo locales (RF-09), que indica que esos
  cambios están solo a nivel local. Esta spec solo lee datos y no genera
  cambios locales, por lo que ese icono permanece oculto.
- **Backend:** `GET /me/families`, `GET /me/invitations` y `GET /me/profile`
  (con `userId`) están documentados en `api-contract.md` y `openapi.yaml`.
- Se guardan en el dispositivo los últimos datos de familias, invitaciones y
  nombre del usuario, solo para mostrarlos cuando no hay conexión. No se borran
  al cerrar sesión. Esta spec parte de una sesión ya iniciada, por lo que el
  usuario autenticado se conoce antes de mostrar cualquier dato: los datos
  guardados de un usuario distinto se eliminan, nunca se muestran y se
  reemplazan por los del backend (confirmado).
- Los datos guardados se eliminan a los 7 días de antigüedad o cuando otro
  usuario accede a la app; hasta entonces se conservan. Eliminados, se tratan
  como si no hubiera datos. Si tienen más de 24 horas, se informa al
  usuario con una alerta de que podrían estar desactualizados; la alerta
  desaparece al actualizar los datos con éxito.

## Comportamiento mobile y casos alternativos

<!-- Adapta la tabla usando MOBILE_GUIDELINES.md. Añade escenarios relevantes.
Marca No aplica con su motivo cuando corresponda. No presupongas soporte offline
ni conservación de todo el estado. Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Carga o acción en curso | Mientras se cargan las familias, el selector muestra un estado de carga y no se puede abrir la lista. Si los datos guardados son de otro usuario, se informa de que no hay datos mientras se consultan en el backend. Mientras carga el contador no se muestra. |
| Sin datos | Sin invitaciones: icono sin contador. Sin familias: no se espera (siempre existe la personal). Una sola familia: sin selector ni lista (RF-02). |
| Entrada inválida | No aplica: el usuario solo elige entre opciones existentes. |
| Error o espera excesiva | Sin datos previos, el selector muestra un error con opción de reintentar. Si falla solo el contador o el perfil, no se muestra contador y el avatar usa un icono genérico y no se bloquea la cabecera. Tiempo máximo de espera: conexión 10 s y solicitud 15 s (PLAN.md). Errores `401`/`403`: se tratan en la spec de sesión. |
| Sin conexión o conexión interrumpida | Con datos guardados de menos de 7 días, se muestran los últimos conocidos (con alerta de posible desactualización si tienen más de 24 h); sin ellos, eliminados por antigüedad (7 días) o de otro usuario, error con reintento. Al recuperar la conexión se actualizan los datos. |
| Cancelar o volver atrás | Atrás con la lista abierta la cierra sin cambiar la familia. |
| Pasar a segundo plano y regresar | Se mantiene la familia activa; los datos se actualizan al volver. Una carga en curso no duplica llamadas. |
| Recrear la pantalla | Se mantiene la familia activa (RF-03) y la lista abierta no es obligatorio conservarla. |
| Reabrir después de terminarse el proceso | La activa vuelve a ser la favorita (RF-03). |
| Otros puntos aplicables de la guía | Pulsaciones repetidas en selector o elección no abren listas duplicadas. Accesibilidad: el contador se anuncia (p. ej. "N invitaciones pendientes"), la activa se anuncia como seleccionada y las áreas táctiles son de al menos 48 dp. Textos largos de nombre de familia se truncan sin romper la cabecera ni con texto grande. Todos los textos son traducibles. Si la lista es larga, es desplazable. Privacidad: sin datos personales en logs ni mensajes de error. |

**Puntos de la guía no aplicables y motivo:** formularios y teclado (no hay
entradas), permisos y capacidades del dispositivo (no se piden), trabajo en
segundo plano (no hay tareas diferidas), orientación (solo vertical, ya
definido en 001).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Usar el composable `MainHeader` existente como punto de partida.
- Los datos provienen del backend según `docs/backend/api-contract.md`.
- Se asume sesión iniciada; esta spec no incluye autenticación.
- Los endpoints necesarios ya están documentados en el contrato (ver `BACKEND_GAPS.md`); su implementación real en el backend no está verificada.
- Referencia visual orientativa, no vinculante: `docs/ui/snapshots/001-home.png` y `docs/ui/snapshots/002-header.png`. La segunda muestra un avatar con foto y un punto en la campana, distintos de la inicial y el contador definidos aquí; prevalece esta spec.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dado un usuario con varias familias, cuando abre la app, entonces la cabecera muestra nombre y rol de la primera familia de su lista.
- **CA-02 · RF-02:** Dado el usuario en cualquier pestaña, cuando toca el selector, entonces ve todas sus familias con nombre y rol y la activa marcada.
- **CA-03 · RF-02:** Dada la lista abierta, cuando elige otra familia, entonces la lista se cierra y la cabecera muestra la elegida.
- **CA-04 · RF-02:** Dada la lista abierta, cuando pulsa Atrás, entonces se cierra y la familia activa no cambia.
- **CA-05 · RF-03:** Dada una familia no favorita elegida, cuando la app pasa a segundo plano y vuelve o se recrea la pantalla, entonces sigue activa; tras terminarse el proceso y reabrir, muestra la favorita.
- **CA-06 · RF-04:** Dado un usuario con N>0 invitaciones pendientes, cuando se muestra la cabecera, entonces el icono muestra el contador N; con 0, no muestra contador; y tocarlo no produce ninguna acción.
- **CA-07 · RF-05:** Dado un usuario llamado «rocío», cuando se muestra la cabecera, entonces el avatar muestra «R».
- **CA-08 · RF-06:** Dada cualquier pestaña, cuando toca el icono de perfil, entonces no ocurre ninguna acción ni navegación.
- **CA-09 · RF-07:** Dado un primer uso sin conexión, cuando se abre la app, entonces el selector muestra error con reintento y, al reintentar con conexión, muestra la familia favorita.
- **CA-10 · RF-07:** Dados datos previos guardados y sin conexión, cuando se abre la app, entonces se muestran esos datos y no un error.
- **CA-11 · RF-07:** Dado que falla solo la carga de invitaciones, cuando se muestra la cabecera, entonces el selector funciona y no se muestra contador.
- **CA-12 · RF-02:** Dadas pulsaciones repetidas rápidas en el selector o en una familia, entonces no se abren listas duplicadas.
- **CA-13 · RF-07:** Dado un lector de pantalla y texto grande, cuando se recorre la cabecera, entonces selector, contador y perfil se anuncian con su etiqueta y nada queda cortado.
- **CA-14 · RF-08:** Dado el usuario en una pestaña, cuando elige otra familia, entonces el contenido de esa pestaña muestra su título seguido del nombre de la nueva familia, sin cambiar de pestaña.
- **CA-15 · RF-08:** Dada una familia elegida, cuando el usuario cambia a otra pestaña, entonces su contenido muestra su título seguido de la familia activa; y al abrir la app muestra la favorita.
- **CA-16 · RF-07:** Dados datos guardados de más de 24 h y menos de 7 días y sin conexión, cuando se abre la app, entonces se muestran esos datos con una alerta de que podrían estar desactualizados; con datos de menos de 24 h, no hay alerta.
- **CA-17 · RF-07:** Dados datos guardados de más de 7 días y sin conexión, cuando se abre la app, entonces se eliminan, no se muestran y aparece el error con reintento.
- **CA-18 · RF-07:** Dados datos guardados de otro usuario, cuando el usuario autenticado abre la app, entonces se eliminan y nunca se muestran: se informa de que no hay datos mientras se consultan en el backend (o error con reintento si no hay conexión) y luego se muestran los suyos.
- **CA-19 · RF-02:** Dado un usuario con una sola familia, cuando se muestra la cabecera, entonces ve su nombre y rol sin indicador de selector, y al tocarla no se abre ninguna lista.
- **CA-20 · RF-09:** Dado que no hay cambios solo locales, cuando se muestra la cabecera, entonces el icono de cambios locales no se muestra; y dado que existen, entonces aparece a la izquierda del icono de notificaciones con una etiqueta accesible (p. ej. "Cambios solo en este dispositivo").

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Cuenta con 2+ familias; abrir la app | Se muestra la primera de la lista |
| CA-02 | Tocar el selector | Lista completa con activa marcada |
| CA-03 | Elegir otra familia | Lista cerrada; cabecera actualizada |
| CA-04 | Abrir lista y pulsar Atrás | Lista cerrada; sin cambio |
| CA-05 | Elegir otra; segundo plano, recrear y matar el proceso | Persiste hasta matar el proceso; luego favorita |
| CA-06 | Cuentas con 0 y con N invitaciones; tocar el icono | Sin contador / contador N; sin acción |
| CA-07 | Cuenta con nombre «rocío» | Avatar «R» |
| CA-08 | Tocar el icono de perfil desde distintas pestañas | Sin acción ni navegación |
| CA-09 | Instalación limpia, sin red, abrir; activar red y reintentar | Error con reintento; luego familia favorita |
| CA-10 | Abrir con red, cerrar, abrir sin red | Datos previos sin error |
| CA-11 | Backend con fallo solo en invitaciones | Selector operativo; sin contador |
| CA-12 | Pulsar varias veces rápido | Sin duplicados |
| CA-13 | TalkBack y tamaño de fuente máximo | Etiquetas anunciadas; sin cortes |
| CA-14 | En cada pestaña, elegir otra familia | Título + nombre de la nueva familia en el contenido |
| CA-15 | Elegir familia; recorrer las 5 pestañas; reabrir app | Todas muestran título + familia activa; tras reabrir, la favorita |
| CA-16 | Datos guardados de 1 h y de 30 h; abrir sin red | Sin alerta / con alerta de posible desactualización |
| CA-17 | Datos guardados de 8 días; abrir sin red | Error con reintento; datos eliminados y no mostrados |
| CA-18 | Iniciar sesión con otro usuario en el mismo dispositivo; abrir con y sin red | Datos del anterior nunca visibles; aviso de sin datos y luego los propios / error con reintento |
| CA-19 | Cuenta con una sola familia; tocar la cabecera | Sin indicador de selector; no se abre lista |
| CA-20 | Abrir la app en cualquier estado de red; con un indicador de prueba de cambios locales activo e inactivo | Sin cambios locales: icono oculto; con cambios locales: visible a la izquierda de notificaciones con su etiqueta accesible |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

- Resuelto: tiempo máximo de espera de las llamadas, fijado en PLAN.md (conexión 10 s, solicitud 15 s).
- Resuelto: `userId` en `GET /me/profile` (A6) y `GET /me/invitations` (A1) están documentados en el contrato; los datos guardados se asocian al `userId`. RF-04 se mantiene en esta spec. El avatar usa un icono de persona genérico mientras no hay nombre.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los puntos
mobile relevantes están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
