# SPEC: Categorías reversibles y ordenables (reactivar, orden y fecha de archivado)

**Estado:** Aprobada (2026-11-24) <!-- Borrador | En revisión | Aprobada -->

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

El Propietario de una familia necesita poder corregir un archivado hecho por
error o que dejó de tener sentido. Hoy archivar una categoría o un tag es
irreversible en la app, aunque el backend ya permite reactivarlos desde el
2026-11-24 (BG-01 de `005-categories/BACKEND_GAPS.md`). Esta funcionalidad
permite volver a activar categorías y tags archivados y deja de advertir que
archivar no puede deshacerse. Además permite ordenar las categorías (hoy solo
se ordenan los tags) y muestra desde cuándo está archivada cada una, ambas cosas
también publicadas por el backend (BG-03 y BG-04). Los Integrantes siguen
viendo todo en solo lectura.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Según `005-categories` (implementada):

- Archivar una categoría pide confirmación advirtiendo que no puede deshacerse
  (RF-12). Una categoría archivada está en la sección plegable «Categorías
  archivadas», se abre en solo lectura para todos (RF-14) y su menú ⋮ solo
  ofrece eliminarla (RF-13).
- Los tags archivados se muestran atenuados, marcados como «Archivado», en solo
  lectura y después de los activos; el Propietario solo puede eliminarlos si no
  tienen movimientos (RF-19). Al quitar un tag con movimientos se ofrece
  archivarlo «(irreversible)» (RF-18).
- Las categorías se muestran en el orden que devuelve el backend y solo
  se marcan como «Archivada», sin fecha (RF-04). No hay forma de ordenarlas.
- Las categorías y los tags archivados siguen visibles en lo que ya los usa
  (por ejemplo, movimientos antiguos) y no se ofrecen para registrar otros nuevos.

Del backend (verificado en su `main` y en su contrato, no probado en ejecución):
`POST /families/:familyId/categories/:categoryId/reactivate` y
`POST /families/:familyId/categories/:categoryId/tags/:tagId/reactivate`, solo
Propietario, responden `204`; errores `403 FAMILY.INSUFFICIENT_ROLE` y
`404 FINANCIAL_TRACKING.CATEGORY_NOT_FOUND` (en tags también
`FINANCIAL_TRACKING.TAG_NOT_FOUND`). Por el código del backend, reactivar no
cambia la posición guardada de la categoría ni del tag, y reactivar algo que ya
está activo no da error. Cada categoría trae además `deprecatedAt` (fecha de
archivado; `null` si está activa o si se archivó antes de esa migración) y
`displayOrder` (posición dentro de su tipo; las anteriores a la migración están
todas en 0), y `PUT /families/:familyId/categories/order` con
`orderedCategoryIds` (solo Propietario, `204`; `400
FINANCIAL_TRACKING.INVALID_CATEGORY_ORDER` si la lista está vacía, repite ids o
mezcla tipos; `404 FINANCIAL_TRACKING.CATEGORY_NOT_FOUND`). Las categorías no
enviadas del mismo tipo quedan después, en su orden previo.

Debe conservarse todo lo demás de la 005: listado, detalle, edición, alta,
eliminación, tags, caché sin conexión y roles.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

**Categorías**

- **RF-01:** El menú ⋮ de una categoría archivada ofrece «Reactivar» (junto a
  «Eliminar») solo al Propietario. Pide confirmación. Al confirmarse, la
  categoría deja la sección de archivadas y aparece entre las activas de su
  tipo **como la última de ellas** (RF-11), la cantidad de activas y de
  archivadas se actualiza y un aviso breve confirma «Categoría reactivada».
- **RF-02:** Archivar una categoría (RF-12 de la 005) sigue pidiendo
  confirmación, pero su texto ya no dice que no puede deshacerse: indica que
  se puede reactivar desde «Categorías archivadas». Lo mismo en la oferta de
  archivar cuando eliminar falla por movimientos asociados (RF-13 de la 005).
- **RF-03:** Una categoría archivada sigue abriéndose en solo lectura (RF-14 de
  la 005): no se edita ni se cambian sus tags sin reactivarla antes.

**Tags**

- **RF-04:** En una categoría activa, cada tag archivado muestra «Reactivar»
  solo al Propietario. Al pulsarlo, el tag pasa a la lista de activos, como el
  último de ellos (RF-11) y deja de estar atenuado, con un aviso breve «Tag
  reactivado». No pide confirmación (la acción es reversible y no borra nada).
- **RF-05:** La oferta de archivar un tag que no se puede eliminar (RF-18 de la
  005) ya no dice «irreversible».

**Comunes**

- **RF-06:** Reactivar requiere conexión: sin ella la acción no se aplica y se
  informa con un mensaje claro (regla de RF-21 de la 005). Mientras una
  reactivación está en curso no se puede iniciar otra operación de cambio.
- **RF-07:** Si el backend responde que la categoría o el tag ya no existe, se
  informa y la lista se actualiza. Si rechaza por rol insuficiente, se aplica
  RF-08 de la 005 (se informa, se consulta de nuevo el rol y la pantalla pasa
  a solo lectura).
- **RF-08:** El resultado se refleja al instante en el listado, el detalle y la
  copia guardada para consultar sin conexión (RF-20 de la 005), sin esperar a
  una nueva descarga.

**Fecha de archivado**

- **RF-09:** Cada categoría de «Categorías archivadas» muestra «Archivada en
  <mes año>» (por ejemplo, «Archivada en sep 2026», con el formato del
  dispositivo), como en el mockup `005-categories-list.png`. Si el backend no
  informa la fecha (categorías archivadas antes de que existiera), se muestra
  solo «Archivada». Al reactivar la categoría la fecha deja de mostrarse.

**Orden de las categorías**

- **RF-10:** El Propietario puede reordenar las categorías **activas** del tipo
  visible con la acción «Reordenar» junto al título de la sección (posición del
  mockup `005-categories-list.png`): arrastrándolas, con una alternativa
  subir/bajar utilizable con lector de pantalla, como los tags (RF-17 de la
  005). El orden se guarda al soltar o al usar subir/bajar y se muestra igual
  en todas las pantallas que listan categorías. Las archivadas no se
  reordenan. Requiere conexión (RF-06) y no está disponible para el Integrante.
  Mientras haya texto en el buscador (RF-05 de la 005) «Reordenar» está
  desactivada, porque la lista filtrada daría un orden parcial; al borrar el
  texto vuelve a estar disponible. «Reordenar» abre un modo de orden: las
  activas muestran el asa y subir/bajar, tocarlas no abre la categoría, no se
  muestran «Crear nueva categoría» ni las archivadas, y «Listo» (o atrás)
  cierra el modo. Cada movimiento se guarda en el momento.
- **RF-11:** Las categorías archivadas se muestran siempre después de las
  activas de su tipo. Al reactivar una categoría, queda como la **última de las
  activas** de su tipo, no en su posición anterior. Lo mismo vale para un tag
  reactivado dentro de su categoría: queda como el último de los activos.
  Si la reactivación se confirma pero no se puede guardar su nueva posición,
  queda activa donde la deje el backend y el aviso lo dice («Categoría
  reactivada, pero no se pudo mover al final»; igual con el tag).
- **RF-12:** Si el backend rechaza el orden (la lista de categorías cambió desde
  otro dispositivo o una ya no existe), se informa, la lista se actualiza con lo
  que devuelve el backend y no queda un orden a medias.
## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Reactivar medios de pago: su backend no tiene la operación (BG-02 de
  `006-payment-methods`).
- El selector «Activa / Archivada» del mockup `006-category-edit.png`: se
  acordó (2026-11-24) reactivar desde el menú ⋮ de la fila, no desde la edición.
- Los demás elementos de los mockups 005/006 ya excluidos en la 005 (reglas AI,
  «Compartido», «ID: CAT-…», presupuestos, etc.).
- Reordenar las categorías archivadas y reordenar entre tipos: cada tipo se
  ordena por separado y solo sus activas.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. El Propietario abre «Gestionar» en Inicio y despliega «Categorías archivadas»
   de la pestaña Gastos o Ingresos.
2. Toca el menú ⋮ de una categoría archivada y elige «Reactivar»; confirma.
3. La categoría pasa a las activas de su tipo y se informa con un aviso breve.
4. Para un tag: abre una categoría activa, ve sus tags archivados al final de
   la lista y toca «Reactivar» en uno; el tag pasa a los activos.
5. Para ordenar: en el listado toca «Reordenar» junto al título de la sección,
   arrastra (o usa subir/bajar) y el orden se guarda al soltar.
6. En «Categorías archivadas» ve «Archivada en <mes año>» en cada fila.
7. Alternativas: sin conexión se informa y nada cambia; si cancela la
   confirmación no cambia nada; el Integrante no ve «Reactivar» ni «Reordenar».

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Solo el Propietario puede reactivar; el backend lo valida (RF-07).
- Una categoría reactivada vuelve a estar disponible para registrar movimientos
  y para elegirla en los formularios, como cualquier activa; igual un tag
  reactivado dentro de su categoría.
- Nombres duplicados: no puede haber una activa con el mismo nombre que una
  archivada del mismo tipo (el backend los rechaza al crear o renombrar contando
  también las archivadas), por lo que reactivar no genera duplicados.
- Orden: las activas de un tipo van en el orden que el Propietario eligió
  (`displayOrder` del backend) y las archivadas después. Una categoría o un tag
  reactivado es el último de las activas de su tipo o categoría (RF-11); el
  backend no lo hace solo (conserva la posición anterior), así que el
  resultado visible debe ser ese. Las categorías anteriores a la migración del
  backend tienen todas la misma posición: se muestran en el orden que devuelve
  el backend hasta que el Propietario las ordena por primera vez.
- Fecha de archivado: solo informativa; si falta, no se inventa (RF-09).
- Color de respaldo (RF-14 de la 008): una categoría sin color sigue usando el
  de su posición, así que reordenar o reactivar puede cambiar su color, igual en
  la gestión y en el dashboard. Archivar no cambia el color de las demás.

## Comportamiento mobile y casos alternativos

<!-- Adapta la tabla usando MOBILE_GUIDELINES.md. Añade escenarios relevantes.
Marca No aplica con su motivo cuando corresponda. No presupongas soporte offline
ni conservación de todo el estado. Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Carga o acción en curso | Mientras se reactiva o se guarda un orden, la fila o el tag muestra un indicador y no se puede iniciar otra operación de cambio; una doble pulsación reactiva una sola vez. |
| Sin datos | No aplica: «Reactivar» solo existe sobre algo archivado. Si era la última archivada, la sección de archivadas desaparece (RF-04 de la 005). |
| Entrada inválida | No aplica: no hay campos de entrada. Un orden que el backend rechaza se trata como error (RF-12). |
| Error o espera excesiva | Mensaje claro con la posibilidad de repetir; la categoría o el tag no cambia de estado hasta que el backend confirma. Si ya no existe (404), se informa y la lista se actualiza. |
| Sin conexión o conexión interrumpida | Reactivar requiere conexión: se informa y nada cambia. Un fallo de red incierto no deja el estado a medias: la lista se vuelve a consultar al recuperar la conexión. Un orden que no se pudo guardar vuelve al anterior. |
| Cancelar o volver atrás | Cancelar la confirmación no cambia nada. Volver atrás durante la operación no la cancela: el resultado se refleja al volver. |
| Pasar a segundo plano y regresar | La lista se refresca como en la 005; un cambio ya confirmado se conserva. |
| Recrear la pantalla | Se conserva el diálogo de confirmación abierto y el estado de la lista. |
| Reabrir después de terminarse el proceso | No aplica: no hay estado propio que conservar; se muestra la copia guardada de la 005. |
| Otros puntos aplicables de la guía | Accesibilidad: «Reactivar» con descripción que nombra la categoría o el tag, 48 dp de área táctil, el estado «Archivada/Archivado» y su fecha se anuncian y no dependen solo del color, reordenar tiene alternativa sin arrastre; fuente al 200 %; modo oscuro. |

**Puntos de la guía no aplicables y motivo:** permisos, ubicación, cámara,
notificaciones y teclado: no hay entrada de texto ni acceso a recursos del
dispositivo en esta funcionalidad.

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Reactivar categorías y tags usa solo las dos rutas que el backend ya ofrece;
  no se piden cambios al backend.
- Los cambios se hacen sobre la 005 sin romper su comportamiento: los textos de
  RF-12 y RF-18 de la 005 cambian como indica RF-02 y RF-05 de esta spec.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dado el Propietario con una categoría archivada, cuando elige «Reactivar» en su menú ⋮ y confirma, entonces la categoría aparece entre las activas de su tipo, sale de las archivadas y los contadores se actualizan.
- **CA-02 · RF-01:** Dado el mismo caso, cuando cancela la confirmación, entonces no cambia nada.
- **CA-03 · RF-01, RF-03:** Dado un Integrante, cuando despliega las archivadas, entonces no ve «Reactivar» y la categoría se abre en solo lectura.
- **CA-04 · RF-02:** Dada una categoría activa, cuando pide archivarla, entonces el aviso indica que se puede reactivar y no dice que no puede deshacerse.
- **CA-05 · RF-04:** Dado el Propietario en una categoría activa con un tag archivado, cuando toca «Reactivar» en ese tag, entonces pasa a los activos, deja de estar atenuado y no pide confirmación.
- **CA-06 · RF-04:** Dado un Integrante o una categoría archivada, cuando abre sus tags, entonces no ve «Reactivar».
- **CA-07 · RF-05:** Dado un tag con movimientos, cuando intenta quitarlo, entonces se le ofrece archivarlo sin la palabra «irreversible».
- **CA-08 · RF-06:** Dado el Propietario sin conexión, cuando intenta reactivar una categoría o un tag, entonces ve un mensaje de que requiere conexión y nada cambia.
- **CA-09 · RF-06:** Dada una reactivación en curso, cuando pulsa repetidamente o intenta otra operación de cambio, entonces se realiza una sola vez y no se inicia otra.
- **CA-10 · RF-07:** Dada una categoría o un tag eliminado por otra persona, cuando intenta reactivarlo, entonces se informa de que ya no existe y la lista se actualiza.
- **CA-11 · RF-07:** Dado un Propietario que la app cree vigente pero ya no lo es, cuando intenta reactivar, entonces se le informa, nada cambia, su rol se actualiza y la pantalla pasa a solo lectura.
- **CA-12 · RF-08:** Dada una reactivación correcta, cuando se cierra y se vuelve a abrir la gestión sin conexión, entonces la categoría o el tag aparece como activo.
- **CA-13 · RF-09:** Dada una categoría archivada con fecha, cuando se despliega «Categorías archivadas», entonces ve «Archivada en <mes año>»; sin fecha ve solo «Archivada»; al reactivarla la fecha desaparece.
- **CA-14 · RF-10:** Dado el Propietario con varias categorías activas, cuando las reordena arrastrando o con subir/bajar, entonces el orden se guarda, se mantiene al salir y volver y se ve igual en las demás pantallas que las listan.
- **CA-15 · RF-10:** Dado un Integrante, o las categorías archivadas, cuando mira el listado, entonces no hay «Reordenar» para ellos; y dado el Propietario con texto en el buscador, entonces «Reordenar» está desactivada hasta que lo borre.
- **CA-16 · RF-10, RF-06:** Dado el Propietario sin conexión, cuando intenta reordenar, entonces ve el mensaje de conexión y el orden anterior se conserva.
- **CA-17 · RF-11:** Dadas tres categorías activas y una archivada, cuando la archivada se reactiva, entonces queda la cuarta (la última) de las activas; igual con un tag reactivado.
- **CA-18 · RF-12:** Dada una categoría eliminada en otro dispositivo, cuando se guarda un orden que la incluye, entonces se informa, la lista se actualiza y no queda un orden a medias.
- **CA-19 · Accesibilidad:** Dado TalkBack, fuente al 200 % o modo oscuro, cuando se usa «Reactivar», entonces es alcanzable, se anuncia con el nombre de la categoría o el tag y mide al menos 48 dp.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Propietario, familia con una categoría archivada; ⋮ → Reactivar → confirmar | Activa en su tipo; contadores correctos |
| CA-02 | Mismo caso; cancelar la confirmación | Sin cambios |
| CA-03 | Integrante; desplegar archivadas y abrir una | Sin «Reactivar»; solo lectura |
| CA-04 | Archivar una categoría activa | Aviso sin «no puede deshacerse» |
| CA-05 | Categoría activa con tag archivado; Reactivar | Tag activo sin confirmación |
| CA-06 | Integrante; categoría archivada | Sin «Reactivar» en tags |
| CA-07 | Quitar un tag con movimientos | Oferta de archivar sin «irreversible» |
| CA-08 | Modo avión; Reactivar | Mensaje de conexión; sin cambios |
| CA-09 | Doble pulsación con respuesta lenta | Una sola llamada; sin otra operación |
| CA-10 | Eliminar desde otro dispositivo y reactivar | Aviso y lista actualizada |
| CA-11 | Bajar el rol en otro dispositivo y reactivar | Aviso, rol actualizado y solo lectura |
| CA-12 | Reactivar, cerrar la gestión y reabrir sin conexión | Sigue activa |
| CA-13 | Categorías archivadas con y sin fecha; reactivar una | Fecha o solo «Archivada»; sin fecha tras reactivar |
| CA-14 | Propietario; reordenar con arrastre y con subir/bajar; salir y volver | Orden conservado y común a todas las pantallas |
| CA-15 | Integrante; categorías archivadas; Propietario con texto en el buscador | Sin «Reordenar»; desactivada con texto |
| CA-16 | Modo avión; reordenar | Mensaje de conexión; orden anterior |
| CA-17 | 3 activas y 1 archivada; reactivar; repetir con un tag | Queda la última de las activas |
| CA-18 | Eliminar desde otro dispositivo y guardar un orden con ella | Aviso, lista actualizada y sin orden a medias |
| CA-19 | TalkBack, 200 % y modo oscuro | Alcanzable, anunciado y de 48 dp |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Resueltas el 2026-11-24 (ya reflejadas arriba): entran «Archivada en …» y reordenar
categorías (RF-09 a RF-12); al reactivar, la categoría y el tag quedan como los
últimos de las activas (RF-11); la categoría pide confirmación y el tag no (RF-01,
RF-04); aviso breve tras reactivar («Categoría reactivada» / «Tag reactivado»);
reordenar con arrastre y alternativa subir/bajar, guardado al soltar, solo
activas y Propietario (RF-10).

Con texto en el buscador «Reordenar» se desactiva (RF-10, CA-15).

Resueltas el 2026-11-24 al planificar: «Reordenar» abre un modo con «Listo» (RF-10);
si se reactiva pero no se puede mover al final, se avisa (RF-11); el color de
respaldo sigue a la posición (Datos y reglas de negocio).

Ninguna.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los puntos
mobile relevantes están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
