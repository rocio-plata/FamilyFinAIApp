# SPEC: Categorías y tags

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

Los miembros de una familia necesitan clasificar sus gastos e ingresos. Esta
funcionalidad permite consultar las categorías de la familia activa y sus tags
(subetiquetas), separadas en Gastos e Ingresos. El Propietario de la familia,
además, puede crearlas, editarlas, archivarlas y eliminarlas. Es la base para
registrar movimientos (spec futura), que necesitan una categoría.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

La app tiene acceso (003), cabecera con familia activa (002) y cinco pestañas
que solo muestran su título y el nombre de la familia activa. No existe ninguna
pantalla de categorías ni otra pantalla fuera de las pestañas. El backend ya
ofrece todas las operaciones de categorías y tags (contrato §3.4.2).

Debe conservarse: la cabecera y las cinco pestañas tal como están, la familia
activa elegida en la cabecera y el comportamiento de sesión de la 003.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

**Acceso y listado**

- **RF-01:** La pestaña Inicio muestra el título de sección "Categorías" con
  el enlace "Gestionar" a su derecha (posición del mockup `001-home.png`), sin
  cuadrícula ni importes. "Gestionar" abre el listado de categorías de la
  familia activa. La gestión de categorías ocupa toda la pantalla, sin la
  cabecera de familia ni la barra de pestañas. El listado muestra, a la
  izquierda de su título "Categorías", una flecha para volver a Inicio. La
  familia no puede cambiarse desde la gestión de categorías.
- **RF-02:** El listado separa las categorías en dos pestañas, "Gastos" e
  "Ingresos"; cada una muestra cuántas categorías activas tiene. Al abrir se
  muestra "Gastos".
- **RF-03:** Cada categoría activa muestra su icono, su nombre y sus tags
  activos en su orden. Una categoría sin icono, o con un icono que la app no
  conoce, muestra un icono genérico.
- **RF-04:** Debajo de las activas hay una sección plegable "Categorías
  archivadas" con su cantidad, que lista las archivadas del tipo mostrado
  marcadas como "Archivada". Si no hay ninguna, la sección no se muestra.
- **RF-05:** Un buscador filtra las categorías del tipo mostrado (activas y
  archivadas) por nombre de categoría o de tag, sobre los datos ya cargados.
- **RF-06:** Tocar una categoría abre su detalle.

**Roles**

- **RF-07:** El Integrante ve el listado y el detalle en solo lectura: no se
  le muestran las acciones de crear, editar, reordenar, archivar ni eliminar.
- **RF-08:** Si el backend rechaza una acción por rol insuficiente, se informa
  al usuario, se vuelven a consultar sus familias para actualizar su rol (regla
  de `002-main-header`) y la pantalla pasa a solo lectura.

**Alta y edición de categorías (solo Propietario)**

- **RF-09:** "Crear nueva categoría" abre un formulario con tipo (Gasto o
  Ingreso, preseleccionado según la pestaña visible y modificable), nombre e
  icono opcional. Al crearse con éxito, la misma pantalla pasa a modo edición
  de la categoría creada y aparece la sección de tags.
- **RF-10:** En edición se pueden cambiar el nombre y el icono (incluido
  quitarlo); se guardan con la acción "Guardar". El tipo se muestra pero no
  puede cambiarse.
- **RF-11:** El icono se elige de un catálogo de 15 iconos (los del mockup
  `006-category-edit.png`).

**Archivar y eliminar categorías (solo Propietario)**

- **RF-12:** Archivar una categoría activa pide confirmación advirtiendo que
  no puede deshacerse. Al confirmarse, la categoría pasa a la sección de
  archivadas.
- **RF-13:** Eliminar una categoría (desde su edición si está activa, o desde
  su menú ⋮ si está archivada) pide confirmación. Si el backend responde que
  tiene movimientos asociados, se informa; si está activa se ofrece archivarla
  en su lugar, y si ya está archivada solo se informa de que no puede
  eliminarse.
- **RF-14:** Una categoría archivada se abre en solo lectura para todos: no se
  puede editar ni modificar sus tags.

**Tags (solo Propietario, en una categoría activa)**

- **RF-15:** Añadir un tag escribiendo su nombre; se guarda al instante y
  aparece al final de la lista.
- **RF-16:** Renombrar un tag tocándolo; se guarda al confirmar el nuevo
  nombre.
- **RF-17:** Reordenar los tags activos arrastrándolos; el orden se guarda al
  soltar. Existe una alternativa sin arrastre (subir/bajar) utilizable con
  lector de pantalla.
- **RF-18:** Quitar un tag pide confirmación e intenta eliminarlo. Si el
  backend responde que tiene movimientos asociados, se ofrece archivarlo
  (irreversible) en su lugar.
- **RF-19:** Los tags archivados se muestran atenuados, marcados como
  "Archivado" y en solo lectura, después de los activos. El Propietario puede
  eliminarlos si no tienen movimientos.

**Datos sin conexión**

- **RF-20:** Se guarda en el dispositivo la última lista de categorías y tags
  descargada de cada familia para poder consultarla sin conexión, con el mismo
  aviso de datos posiblemente desactualizados que la cabecera.
- **RF-21:** Crear, editar, reordenar, archivar y eliminar requieren conexión:
  sin ella la acción no se aplica, se informa con un mensaje claro y lo
  escrito se conserva en pantalla.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Elementos de los mockups 005/006 que el backend no ofrece (acordado omitirlos):

- Contador "N reglas AI", insignia "Compartido", "ID: CAT-…" e indicador
  "IA sincronizada".
- Tonalidad o color por categoría (ver `BACKEND_GAPS.md`, BG-02).
- Fecha de archivado (BG-03) y texto de presupuesto en las archivadas.
- "Reordenar" categorías (solo se reordenan tags; BG-04).
- Número de movimientos ligados en el botón de eliminar.
- Banner "Organización inteligente".
- Reactivar una categoría o un tag archivados (ver `BACKEND_GAPS.md`, BG-01).

Otras exclusiones acordadas:

- Hacer cambios sin conexión para enviarlos después.
- Menú ⋮ de la barra superior del listado e icono de perfil del mockup.
- El resto de la sección "Gastos por categoría" de Inicio (`001-home.png`):
  cuadrícula de categorías con el gasto del mes, contador "N categorías" y
  desglose de gastos. Pertenecen a la spec de Dashboard, que conservará el
  enlace "Gestionar" definido aquí.
- Gestionar las categorías archivadas más allá de consultarlas y eliminarlas:
  se definirá cuando el backend permita reactivarlas (BG-01).

Pertenecen a otras specs: elegir categoría y tag al registrar un movimiento,
presupuestos por categoría e importes por categoría.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. En Inicio, el usuario toca "Gestionar" y ve el listado de la familia
   activa en la pestaña "Gastos".
2. Cambia entre "Gastos" e "Ingresos", busca por nombre, despliega las
   archivadas o toca una categoría para ver su detalle.
3. Propietario, alta: toca "Crear nueva categoría", elige tipo, escribe el
   nombre, opcionalmente elige icono y crea. La pantalla pasa a edición y
   puede añadir tags.
4. Propietario, edición: cambia nombre o icono y toca "Guardar"; añade,
   renombra, reordena o quita tags, que se guardan al instante.
5. Propietario, archivar o eliminar: confirma en un diálogo; al terminar
   vuelve al listado, ya actualizado.
6. Volver atrás desde el detalle lleva al listado, y desde el listado a
   Inicio.

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Categoría: tipo (Gasto/Ingreso, fijo tras crearla), nombre, icono opcional y
  estado (Activa/Archivada). Tag: nombre, estado y posición dentro de su
  categoría.
- Nombre de categoría: obligatorio, de 1 a 50 caracteres, con contador
  "n / 50". Nombre de tag: obligatorio, de 1 a 30 caracteres. Los espacios al
  inicio y al final se eliminan antes de validar y guardar.
- Duplicados: los decide el backend. No puede haber dos categorías del mismo
  tipo con el mismo nombre (cuentan también las archivadas) ni dos tags con el
  mismo nombre en una categoría. La app muestra el mensaje junto al campo y
  conserva lo escrito.
- Orden: las categorías se muestran en el orden que devuelve el backend; los
  tags, según su posición.
- Archivar es irreversible. Una categoría archivada no admite tags nuevos.
- Eliminar solo es posible si no hay movimientos asociados; el backend es
  quien lo determina.
- El backend es la autoridad del rol. La app oculta las acciones al
  Integrante según el rol conocido de la familia activa.
- Copia local: por familia y usuario, con las mismas reglas que la cabecera
  (aviso a partir de 24 h, descarte a los 7 días o si es de otro usuario,
  borrado al terminar la sesión). Es la regla común de toda la app para los
  datos guardados en el dispositivo.
- Toda familia nace con 9 categorías de gasto creadas por el backend, sin
  icono. No se crean categorías de ingreso por defecto, por lo que "Ingresos"
  empieza vacío hasta que el Propietario cree alguna (ver `BACKEND_GAPS.md`,
  BG-05).
- Tras cada cambio con éxito, listado y detalle reflejan el resultado sin que
  el usuario tenga que actualizar manualmente.
- Mensajes de error: según los códigos del contrato (§4 y §9).

## Comportamiento mobile y casos alternativos

<!-- Adapta la tabla usando MOBILE_GUIDELINES.md. Añade escenarios relevantes.
Marca No aplica con su motivo cuando corresponda. No presupongas soporte offline
ni conservación de todo el estado. Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Carga o acción en curso | Primera carga sin copia local: indicador de carga. Con copia local: se muestra y se actualiza al llegar los datos. Durante un guardado, la acción muestra progreso y queda deshabilitada; pulsaciones repetidas no duplican la operación. |
| Sin datos | Tipo sin categorías (habitual en "Ingresos" de una familia nueva; el backend solo crea categorías de gasto por defecto): mensaje de vacío; al Propietario se le invita a crear una. Búsqueda sin resultados: mensaje específico. Categoría sin tags: mensaje de vacío en la sección de tags. |
| Entrada inválida | Nombre vacío o solo espacios: no se puede guardar. No se puede escribir más allá del límite. Duplicado: mensaje junto al campo. |
| Error o espera excesiva | Sin copia local: error con "Reintentar". En una acción: mensaje de error, se conserva lo escrito y se puede reintentar. Si la categoría o el tag ya no existe, se informa y se actualiza la lista. |
| Sin conexión o conexión interrumpida | Consulta con la copia local y aviso si es antigua; sin copia, error con reintento. Las acciones de cambio informan de que requieren conexión (RF-21). Al recuperar la conexión, los datos se actualizan. |
| Cancelar o volver atrás | Al salir de la pantalla de alta o edición con nombre o icono sin guardar, se pide confirmación antes de descartar. Cancelar un diálogo de confirmación no cambia nada. |
| Pasar a segundo plano y regresar | Se conserva la pantalla y lo escrito; el listado se actualiza al volver. |
| Recrear la pantalla | Se conservan la pantalla abierta, la pestaña Gastos/Ingresos, el texto de búsqueda y lo escrito en el formulario. |
| Reabrir después de terminarse el proceso | La app abre en Inicio; no se conservan cambios sin guardar. |
| Cambio de familia activa | No es posible dentro de la gestión de categorías (RF-01). Al volver a Inicio y elegir otra familia, "Gestionar" abre las categorías de la nueva familia. |
| Otros puntos aplicables de la guía | Accesibilidad: iconos del catálogo con descripción, el seleccionado y el estado "Archivada" se anuncian y no dependen solo del color, reordenar tiene alternativa sin arrastre (RF-17), áreas táctiles de al menos 48 dp. Nombres largos se truncan sin romper la fila, también con texto grande. El teclado no tapa el campo activo ni la acción de guardar. Listas desplazables. Textos traducibles. Sin datos personales en logs. |

**Puntos de la guía no aplicables y motivo:** permisos y capacidades del
dispositivo (no se piden), trabajo en segundo plano (no hay tareas diferidas),
orientación (solo vertical, definido en 001).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- El backend ya está definido e implementado: solo se construye la app, según
  `docs/backend/api-contract.md` §3.4.2 y `docs/backend/openapi.yaml`. No se
  piden cambios al backend.
- Referencia visual orientativa, no vinculante:
  `docs/ui/snapshots/005-categories-list.png` y
  `docs/ui/snapshots/006-category-edit.png`. Donde difieran, prevalece esta
  spec.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dado el usuario en Inicio, cuando toca "Gestionar", entonces ve el listado de la familia activa sin cabecera de familia ni barra de pestañas, con una flecha a la izquierda del título "Categorías"; al tocarla, o con el gesto de atrás del sistema, regresa a Inicio.
- **CA-23 · RF-01:** Dado el usuario que vuelve a Inicio y elige otra familia, cuando toca "Gestionar", entonces ve las categorías de la nueva familia.
- **CA-24 · RF-09/RF-10:** Dado el formulario con nombre o icono modificados sin guardar, cuando intenta salir, entonces se le pide confirmación; si confirma se descartan y si cancela permanece en la pantalla. Sin cambios, sale directamente.
- **CA-02 · RF-02:** Dada una familia con categorías de ambos tipos, cuando abre el listado, entonces ve "Gastos" con sus categorías y el número de activas, y al tocar "Ingresos" ve las de ingreso con el suyo.
- **CA-03 · RF-03:** Dada una categoría con icono y tags, una sin icono y una con icono desconocido, cuando se listan, entonces la primera muestra su icono, nombre y tags en orden, y las otras dos un icono genérico.
- **CA-04 · RF-04:** Dado un tipo con categorías archivadas, cuando se despliega "Categorías archivadas", entonces aparecen marcadas como "Archivada" con su cantidad; sin archivadas, la sección no aparece.
- **CA-05 · RF-05:** Dado el listado, cuando escribe un texto en el buscador, entonces solo quedan las categorías cuyo nombre o algún tag lo contiene; sin coincidencias ve un mensaje de sin resultados.
- **CA-06 · RF-07:** Dado un Integrante, cuando abre el listado y el detalle de una categoría, entonces ve los datos y ninguna acción de crear, editar, reordenar, archivar o eliminar.
- **CA-07 · RF-08:** Dado un usuario que la app cree Propietario pero ya no lo es, cuando intenta guardar un cambio, entonces ve un aviso de permisos, el cambio no se aplica y la pantalla queda en solo lectura con su rol actualizado en la cabecera.
- **CA-08 · RF-09:** Dado el Propietario en "Ingresos", cuando toca "Crear nueva categoría", entonces el tipo viene en Ingreso; al crear con un nombre válido, la pantalla pasa a edición con la sección de tags y la categoría aparece en el listado.
- **CA-09 · RF-09/RF-10:** Dado un nombre vacío o solo de espacios, cuando intenta crear o guardar, entonces no se envía; y con un nombre ya usado en ese tipo ve el mensaje de duplicado junto al campo, conservando lo escrito.
- **CA-10 · RF-10/RF-11:** Dada una categoría activa, cuando el Propietario cambia el nombre, elige otro icono del catálogo o lo quita y toca "Guardar", entonces listado y detalle muestran los nuevos valores; el tipo no es editable.
- **CA-11 · RF-12:** Dada una categoría activa, cuando el Propietario la archiva y confirma el aviso de irreversibilidad, entonces pasa a "Categorías archivadas" y el contador de activas baja; si cancela, nada cambia.
- **CA-12 · RF-13:** Dada una categoría sin movimientos, cuando el Propietario la elimina y confirma, entonces desaparece del listado. Dada una activa con movimientos, entonces ve el aviso y la opción de archivarla; dada una archivada con movimientos, solo el aviso.
- **CA-13 · RF-14:** Dada una categoría archivada, cuando cualquier usuario la abre, entonces ve sus datos sin acciones de edición ni de tags.
- **CA-14 · RF-15:** Dada una categoría activa, cuando el Propietario añade un tag con nombre válido, entonces aparece al final sin pulsar "Guardar"; con nombre duplicado ve el mensaje y el tag no se añade.
- **CA-15 · RF-16:** Dado un tag activo, cuando el Propietario lo renombra, entonces listado y detalle muestran el nuevo nombre.
- **CA-16 · RF-17:** Dados varios tags activos, cuando el Propietario mueve uno (arrastrando o con subir/bajar), entonces el nuevo orden se mantiene al salir y volver a entrar.
- **CA-17 · RF-18:** Dado un tag sin movimientos, cuando lo quita y confirma, entonces desaparece. Dado uno con movimientos, entonces se le ofrece archivarlo y, si acepta, queda como "Archivado".
- **CA-18 · RF-19:** Dada una categoría con tags archivados, cuando se abre, entonces se ven atenuados y marcados después de los activos, sin poder renombrarlos ni reordenarlos.
- **CA-19 · RF-20:** Dado un listado ya consultado, cuando abre Categorías sin conexión, entonces ve los últimos datos de esa familia, con aviso si tienen más de 24 h; sin datos guardados ve un error con "Reintentar".
- **CA-20 · RF-21:** Dado el Propietario sin conexión, cuando intenta crear, guardar, reordenar, archivar o eliminar, entonces ve un mensaje de que requiere conexión, nada cambia y lo escrito se conserva.
- **CA-21 · RF-09/RF-10:** Dada una acción de guardado en curso, cuando pulsa repetidamente, entonces se realiza una sola operación.
- **CA-22 · RF-10:** Dado el formulario con texto escrito, cuando se recrea la pantalla o la app pasa a segundo plano y regresa, entonces el texto y el icono elegido se conservan.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Desde Inicio, tocar "Gestionar"; volver con la flecha y con el gesto de atrás | Listado de la familia activa sin cabecera ni pestañas, con flecha junto al título; regreso a Inicio en ambos casos |
| CA-02 | Familia con gastos e ingresos; alternar pestañas | Listas y contadores de activas correctos por tipo |
| CA-03 | Categorías con icono, sin icono y con icono desconocido | Icono propio, genérico y genérico; tags en orden |
| CA-04 | Tipo con y sin archivadas; desplegar la sección | Archivadas marcadas y contadas; sección ausente si no hay |
| CA-05 | Buscar por nombre de categoría, por tag y por texto inexistente | Filtrado correcto; mensaje de sin resultados |
| CA-06 | Entrar como Integrante; abrir listado y detalle | Datos visibles; ninguna acción de cambio |
| CA-07 | Backend responde rol insuficiente a un guardado | Aviso, sin cambio, solo lectura, rol actualizado |
| CA-08 | Propietario en "Ingresos"; crear con nombre válido | Tipo preseleccionado; pasa a edición con tags; aparece en el listado |
| CA-09 | Crear/guardar con nombre vacío, de espacios y duplicado | No se envía; mensaje de duplicado junto al campo; texto conservado |
| CA-10 | Cambiar nombre, cambiar icono, quitar icono; guardar | Nuevos valores en listado y detalle; tipo no editable |
| CA-11 | Archivar confirmando y cancelando | Pasa a archivadas y baja el contador; cancelar no cambia nada |
| CA-12 | Eliminar sin movimientos; activa con movimientos; archivada con movimientos | Desaparece; aviso con oferta de archivar; solo aviso |
| CA-13 | Abrir una archivada como Propietario e Integrante | Solo lectura en ambos |
| CA-14 | Añadir tag válido y duplicado | Aparece al final al instante; duplicado rechazado con mensaje |
| CA-15 | Renombrar un tag | Nuevo nombre en listado y detalle |
| CA-16 | Reordenar arrastrando y con subir/bajar; salir y volver | Orden conservado |
| CA-17 | Quitar tag sin movimientos y con movimientos | Desaparece; oferta de archivar y queda "Archivado" |
| CA-18 | Abrir categoría con tags archivados | Atenuados, marcados, al final, sin acciones de edición |
| CA-19 | Consultar, cortar la red y reabrir; repetir sin datos guardados y con datos de más de 24 h | Últimos datos; aviso de antigüedad; error con reintento |
| CA-20 | Sin red, intentar cada acción de cambio | Mensaje de conexión; sin cambios; texto conservado |
| CA-21 | Pulsar varias veces crear/guardar/añadir | Una sola operación |
| CA-22 | Escribir, girar/recrear y pasar a segundo plano | Texto e icono conservados |
| CA-23 | Volver a Inicio, elegir otra familia, tocar "Gestionar" | Categorías de la nueva familia |
| CA-24 | Modificar nombre o icono y salir (confirmar y cancelar); salir sin cambios | Confirmación antes de descartar; sin cambios, salida directa |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los puntos
mobile relevantes están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
