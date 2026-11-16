# SPEC: Medios de pago

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

Los miembros de una familia necesitan indicar con qué pagaron cada movimiento.
Esta funcionalidad permite a cualquier miembro consultar y gestionar los medios
de pago de la familia activa: crearlos, renombrarlos, archivarlos y
eliminarlos. Los medios de pago son compartidos por toda la familia, sin
duplicados. Es la base para registrar movimientos (spec futura), que necesitan
un medio de pago.

El medio predeterminado de cada miembro es una preferencia individual que
pertenece al perfil del usuario y no forma parte de esta spec.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

La app tiene acceso (003), cabecera con familia activa (002) y gestión de
categorías (005), que se abre desde "Gestionar" en Inicio. No existe ninguna
pantalla de medios de pago ni de Perfil. El backend ya ofrece crear, listar,
renombrar, archivar y eliminar medios de pago (contrato §3.4.1).

Debe conservarse: la cabecera, las cinco pestañas, la sección "Categorías" de
Inicio y su gestión (005), la familia activa elegida en la cabecera y el
comportamiento de sesión de la 003.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

**Acceso y listado**

- **RF-01:** La pestaña Inicio muestra el título de sección "Medios de pago"
  con el enlace "Gestionar" a su derecha, justo encima de la sección
  "Categorías" y con su mismo patrón (005). "Gestionar" abre el listado de
  medios de pago de la familia activa, que ocupa toda la pantalla, sin la
  cabecera de familia ni la barra de pestañas, con una flecha a la izquierda
  del título para volver a Inicio.
- **RF-02:** El listado muestra los medios de pago activos de la familia con su
  nombre, tal como los devuelve el backend y en su mismo orden. La app no
  contiene nombres de medios de pago propios.
- **RF-03:** Bajo los activos, una sección desplegable "Medios de pago
  archivados" con su cantidad lista los archivados, marcados como "Archivado".
  Si no hay ninguno, la sección no se muestra.
- **RF-04:** Cada medio de pago tiene un menú ⋮ con las acciones disponibles
  según su estado: en un activo, "Renombrar", "Archivar" y "Eliminar"; en un
  archivado, solo "Eliminar".

**Permisos**

- **RF-05:** Cualquier miembro de la familia (Propietario o Integrante) puede
  realizar todas las acciones de esta spec; no hay modo de solo lectura por
  rol.

**Alta y renombrado**

- **RF-06:** "Crear medio de pago" abre, sobre el listado, un diálogo o
  bottom sheet con un único campo de nombre, su contador "n / 40" y las
  acciones Guardar y Cancelar. Al crearse con éxito se cierra y el nuevo medio
  aparece en el listado.
- **RF-07:** "Renombrar" abre el mismo formulario con el nombre actual. Al
  guardarse con éxito se cierra y el listado muestra el nuevo nombre.
- **RF-13:** Si se intenta guardar sin nombre, no se guarda y se avisa junto
  al campo de que el nombre es obligatorio. Al cerrar el formulario con
  cambios sin guardar (Cancelar, gesto de atrás o tocar fuera), se pide
  confirmación: salir sin guardar o seguir en el formulario. Sin cambios, se
  cierra directamente.

**Archivar y eliminar**

- **RF-08:** Archivar un medio activo pide confirmación advirtiendo que no
  puede deshacerse. Al confirmarse, el medio pasa a la sección de archivados.
- **RF-09:** Eliminar un medio (activo o archivado) pide confirmación. Al
  confirmarse, desaparece del listado. Si el backend responde que tiene
  movimientos asociados, se informa; si está activo se ofrece archivarlo en su
  lugar, y si ya está archivado solo se informa de que no puede eliminarse.
- **RF-10:** Si al archivar o eliminar el backend responde que el medio es el
  predeterminado de algún miembro de la familia (puede ser el del propio
  usuario), la acción no se aplica y se informa de que ese miembro debe elegir
  antes otro predeterminado. La app no lo impide de antemano porque no conoce
  el predeterminado del usuario (BG-01); se revisará en la spec de Perfil.

**Datos sin conexión**

- **RF-11:** Se guarda en el dispositivo la última lista de medios de pago
  descargada de cada familia para poder consultarla sin conexión, con el mismo
  aviso de datos posiblemente desactualizados que la cabecera y las
  categorías.
- **RF-12:** Crear, renombrar, archivar y eliminar requieren conexión: sin
  ella la acción no se aplica, se informa con un mensaje claro y lo escrito se
  conserva en pantalla.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Acordado:

- Medio de pago predeterminado del usuario: verlo, elegirlo o marcarlo en el
  listado. Es una preferencia del perfil del usuario y se especificará con
  "Perfil y ajustes" (ver `BACKEND_GAPS.md`, BG-01, para esa spec).
- Pantalla de Perfil (el acceso es desde Inicio).
- Buscador en el listado.
- Ver el predeterminado de otros miembros.
- Reordenar los medios de pago.
- Hacer cambios sin conexión para enviarlos después.
- Reactivar un medio de pago archivado: el backend no lo ofrece (BG-02).

Pertenecen a otras specs: elegir el medio de pago al registrar un movimiento,
filtrar movimientos por medio de pago y el reporte por medio de pago.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. En Inicio, el usuario toca "Gestionar" en la sección "Medios de pago" y ve
   el listado de la familia activa.
2. Alta: toca "Crear medio de pago", escribe el nombre y guarda; el nuevo
   medio aparece en el listado.
3. Renombrar: abre el menú ⋮ de un medio activo, elige "Renombrar", cambia el
   nombre y guarda.
4. Archivar o eliminar: elige la acción en el menú ⋮ y confirma en un diálogo;
   el listado queda actualizado, o se le informa de por qué no fue posible.
5. Despliega "Medios de pago archivados" para consultarlos o eliminarlos.
6. Volver atrás desde el listado lleva a Inicio.

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Medio de pago: nombre y estado (Activo/Archivado). Pertenece a la familia y
  lo comparten todos sus miembros. Los nombres provienen siempre del backend.
- Nombre: obligatorio, de 1 a 40 caracteres, con contador "n / 40". Los
  espacios al inicio y al final se eliminan antes de validar y guardar. Sin
  nombre no se puede guardar y se avisa de ello (RF-13).
- Duplicados: los decide el backend. No puede haber dos medios con el mismo
  nombre en una familia, sin distinguir mayúsculas de minúsculas y contando
  también los archivados. La app muestra el mensaje junto al campo y conserva
  lo escrito.
- Orden: el que devuelve el backend.
- Archivar es irreversible (BG-02). Un medio archivado no puede renombrarse.
- Ni archivar ni eliminar son posibles mientras el medio sea el predeterminado
  de algún miembro; eliminar, además, solo si no tiene movimientos asociados.
  El backend es quien lo determina y comprueba primero el predeterminado.
- Toda familia nace con medios de pago creados por el backend; la app los
  obtiene al consultar el listado.
- Copia local: por familia y usuario, con la regla común de la app (aviso a
  partir de 24 h, descarte a los 7 días o si es de otro usuario, borrado al
  terminar la sesión).
- Tras cada cambio con éxito, el listado refleja el resultado sin que el
  usuario tenga que actualizar manualmente.
- Mensajes de error: según los códigos del contrato (§4 y §9), aceptando
  también el prefijo que emite realmente el backend (BG-04).

## Comportamiento mobile y casos alternativos

<!-- Adapta la tabla usando MOBILE_GUIDELINES.md. Añade escenarios relevantes.
Marca No aplica con su motivo cuando corresponda. No presupongas soporte offline
ni conservación de todo el estado. Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Carga o acción en curso | Primera carga sin copia local: indicador de carga. Con copia local: se muestra y se actualiza al llegar los datos. Durante un guardado o una acción del menú, se muestra progreso y la acción queda deshabilitada; pulsaciones repetidas no duplican la operación. |
| Sin datos | Familia sin medios de pago activos (excepcional: el backend crea algunos al crear la familia): mensaje de vacío con invitación a crear uno. |
| Entrada inválida | Nombre vacío o solo espacios: al guardar se avisa de que el nombre es obligatorio y no se guarda. No se puede escribir más allá de 40 caracteres. Duplicado: mensaje junto al campo. |
| Error o espera excesiva | Sin copia local: error con "Reintentar". En una acción: mensaje de error, se conserva lo escrito y se puede reintentar. Si el medio ya no existe o ya está archivado, se informa y se actualiza la lista. |
| Sin conexión o conexión interrumpida | Consulta con la copia local y aviso si es antigua; sin copia, error con reintento. Las acciones de cambio informan de que requieren conexión (RF-12). Al recuperar la conexión, los datos se actualizan. |
| Cancelar o volver atrás | Cerrar el formulario con cambios sin guardar pide confirmación para salir sin guardar o seguir editando (RF-13); sin cambios se cierra directamente. Cancelar un diálogo de confirmación no cambia nada. |
| Pasar a segundo plano y regresar | Se conserva la pantalla y lo escrito; el listado se actualiza al volver. |
| Recrear la pantalla | Se conservan la pantalla abierta, la sección de archivados desplegada o no, el formulario abierto y lo escrito en él. |
| Reabrir después de terminarse el proceso | La app abre en Inicio; no se conservan cambios sin guardar. |
| Cambio de familia activa | No es posible dentro de la gestión de medios de pago. Al volver a Inicio y elegir otra familia, "Gestionar" abre los medios de pago de la nueva familia. |
| Cambios hechos por otro miembro | Se ven al actualizarse el listado (al abrirlo, al volver a la app y al recuperar conexión); no hay actualización en tiempo real. |
| Otros puntos aplicables de la guía | Accesibilidad: la etiqueta "Archivado" se anuncia y no depende solo del color, el menú ⋮ tiene descripción con el nombre del medio, áreas táctiles de al menos 48 dp. Nombres largos se truncan sin romper la fila, también con texto grande. El teclado no tapa el campo ni la acción de guardar. Lista desplazable. Textos traducibles. Sin datos personales en logs. |

**Puntos de la guía no aplicables y motivo:** permisos y capacidades del
dispositivo (no se piden), trabajo en segundo plano (no hay tareas diferidas),
orientación (solo vertical, definido en 001).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Solo se construye la app (frontend), según `docs/backend/api-contract.md`
  §3.4.1 y `docs/backend/openapi.yaml`. No se piden cambios al backend.
- No hay mockup de medios de pago en `docs/ui/snapshots`; la referencia visual
  es el patrón de la gestión de categorías (005) y `docs/DESIGN.md`.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dado el usuario en Inicio, entonces ve la sección "Medios de pago" con "Gestionar" justo encima de "Categorías"; cuando toca "Gestionar", ve el listado de la familia activa sin cabecera de familia ni barra de pestañas, con una flecha junto al título; al tocarla, o con el gesto de atrás del sistema, regresa a Inicio.
- **CA-02 · RF-01:** Dado el usuario que vuelve a Inicio y elige otra familia, cuando toca "Gestionar", entonces ve los medios de pago de la nueva familia.
- **CA-03 · RF-02:** Dada una familia con medios activos, cuando se abre el listado, entonces aparecen con los nombres y el orden que devuelve el backend.
- **CA-04 · RF-03:** Dada una familia con medios archivados, cuando se despliega "Medios de pago archivados", entonces aparecen marcados como "Archivado" con su cantidad; sin archivados, la sección no aparece.
- **CA-05 · RF-04:** Dado el listado, cuando abre el menú ⋮ de un medio activo, entonces ve "Renombrar", "Archivar" y "Eliminar"; en un archivado, solo "Eliminar".
- **CA-06 · RF-05:** Dado un Integrante, cuando abre el listado, entonces dispone de las mismas acciones que el Propietario.
- **CA-07 · RF-06:** Dado el formulario de alta con un nombre válido, cuando guarda, entonces se cierra y el medio aparece en el listado como activo.
- **CA-08 · RF-06/RF-07/RF-13:** Dado el formulario con el nombre vacío o solo espacios, cuando intenta guardar, entonces no se guarda y ve junto al campo el aviso de que el nombre es obligatorio; el contador muestra "n / 40" y no se pueden escribir más de 40 caracteres.
- **CA-09 · RF-06/RF-07:** Dado un nombre que ya existe en la familia (también con otras mayúsculas o en un archivado), cuando guarda, entonces ve el mensaje de duplicado junto al campo y lo escrito se conserva.
- **CA-10 · RF-07:** Dado un medio activo, cuando lo renombra con un nombre válido, entonces el listado muestra el nuevo nombre.
- **CA-11 · RF-08:** Dado un medio activo que no es predeterminado de nadie, cuando elige "Archivar" y confirma tras la advertencia de irreversibilidad, entonces pasa a la sección de archivados; si cancela, no cambia nada.
- **CA-12 · RF-09:** Dado un medio sin movimientos que no es predeterminado de nadie, cuando lo elimina y confirma, entonces desaparece del listado.
- **CA-13 · RF-09:** Dado un medio activo con movimientos, cuando intenta eliminarlo, entonces se le informa y se le ofrece archivarlo; si está archivado, solo se le informa.
- **CA-14 · RF-10:** Dado un medio que es el predeterminado de algún miembro, cuando intenta archivarlo o eliminarlo, entonces se le informa de que ese miembro debe elegir antes otro predeterminado y el medio permanece activo en el listado.
- **CA-15 · RF-11:** Dado un listado ya descargado, cuando lo abre sin conexión, entonces ve los medios de pago, con aviso si los datos tienen más de 24 h; sin datos guardados ve un error con "Reintentar".
- **CA-16 · RF-12:** Dado el dispositivo sin conexión, cuando intenta crear, renombrar, archivar o eliminar, entonces la acción no se aplica, se le informa y lo escrito se conserva.
- **CA-19 · RF-13:** Dado el formulario con cambios sin guardar, cuando intenta cerrarlo, entonces se le pide confirmación; si confirma sale sin guardar y si no, permanece en el formulario con lo escrito. Sin cambios, se cierra directamente.
- **CA-17 · RF-06/RF-07/RF-08/RF-09:** Dada una acción en curso, cuando pulsa repetidamente, entonces la operación se realiza una sola vez.
- **CA-18 · Mobile:** Dado el formulario con un nombre escrito, cuando se recrea la pantalla o la app pasa a segundo plano y regresa, entonces el formulario sigue abierto con lo escrito.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | En Inicio, comprobar la posición de la sección; tocar "Gestionar"; volver con la flecha y con el gesto de atrás | Sección encima de "Categorías"; listado sin cabecera ni pestañas; regreso a Inicio en ambos casos |
| CA-02 | Volver a Inicio, elegir otra familia, tocar "Gestionar" | Medios de pago de la nueva familia |
| CA-03 | Familia con varios medios activos | Nombres y orden del backend |
| CA-04 | Familia con y sin archivados; desplegar la sección | Archivados marcados y con cantidad; sin archivados, sin sección |
| CA-05 | Abrir el menú ⋮ de un activo y de un archivado | Tres acciones en el activo; solo "Eliminar" en el archivado |
| CA-06 | Sesión de un Integrante | Mismas acciones que el Propietario |
| CA-07 | Crear un medio con nombre válido | Formulario cerrado; medio en el listado |
| CA-08 | Guardar con nombre vacío y solo espacios; escribir más de 40 caracteres | No se guarda y aparece el aviso de nombre obligatorio; contador y límite de 40 |
| CA-09 | Crear y renombrar con un nombre existente, con otras mayúsculas y con el de un archivado | Mensaje de duplicado junto al campo; texto conservado |
| CA-10 | Renombrar un medio activo | Nuevo nombre en el listado |
| CA-11 | Archivar un activo que no es predeterminado de nadie: confirmar y cancelar | Pasa a archivados al confirmar; sin cambios al cancelar |
| CA-12 | Eliminar un medio sin movimientos ni predeterminado de nadie | Desaparece del listado |
| CA-13 | Eliminar un medio con movimientos, activo y archivado | Aviso y oferta de archivar en el activo; solo aviso en el archivado |
| CA-14 | Archivar y eliminar un medio que es predeterminado de un miembro (por ejemplo "Efectivo" en una familia nueva) | Aviso; el medio permanece activo |
| CA-15 | Abrir sin conexión con datos recientes, con datos de más de 24 h y sin datos | Listado; listado con aviso; error con "Reintentar" |
| CA-16 | Sin conexión, intentar cada acción de cambio | No se aplica; mensaje; texto conservado |
| CA-17 | Pulsar repetidamente Guardar y las acciones de confirmación | Una sola operación |
| CA-18 | Con el formulario abierto, recrear la pantalla y pasar a segundo plano | Formulario abierto con lo escrito |
| CA-19 | Cerrar el formulario con cambios (Cancelar, atrás y tocar fuera): confirmar y no confirmar; cerrarlo sin cambios | Confirmación antes de descartar; sin cambios, cierre directo |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los puntos
mobile relevantes están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
