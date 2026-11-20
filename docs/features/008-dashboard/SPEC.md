# SPEC: Dashboard

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

Los miembros de una familia quieren saber, de un vistazo, cómo va el mes: cuánto
han gastado, cuánto han ingresado, cuánto les queda disponible y en qué
categorías se va (o de dónde viene) el dinero. El dashboard ocupa la pestaña
Inicio y muestra ese resumen para la familia activa y un mes elegido, con su
comparación con el mes anterior, y permite registrar un movimiento directamente
desde una categoría. Para que el gráfico y las categorías se relacionen, cada
categoría pasa a tener un color, que el Propietario puede elegir.

Referencias visuales: `docs/ui/snapshots/008-dashboard-gastos.png` y
`docs/ui/snapshots/008-dashboard-ingresos.png`; selector de color:
`docs/ui/snapshots/006-category-edit.png`.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Inicio muestra, bajo la cabecera, las secciones "Medios de pago" (006) y
"Categorías" (005), cada una con su enlace "Gestionar", y el botón "+" que abre
"Nuevo movimiento" (007). La pestaña "Movimientos" ya muestra los movimientos de
un mes con su resumen (balance, ingresos y gastos) y guarda en el dispositivo
cada mes consultado para verlo sin conexión. No hay ninguna vista del reparto
del mes por categoría ni comparación entre meses. Las categorías tienen icono
pero no color (BG-02 de la 005).

Debe conservarse: la cabecera y el selector de familia, las cinco pestañas, el
botón "+", la sección "Medios de pago" con su gestión, el acceso a la gestión de
categorías, la pestaña "Movimientos" tal cual y el comportamiento de sesión.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

**Mes mostrado**

- **RF-01:** Inicio muestra el dashboard de la familia activa para un mes, con
  flechas para ir al mes anterior y al siguiente y un botón "Mes actual" que
  vuelve al mes en curso. Al abrir la app se muestra el mes en curso. No se
  pueden elegir meses futuros. El mes de Inicio es independiente del de la
  pestaña "Movimientos": cambiar uno no cambia el otro.

**Desglose**

- **RF-02:** Una tarjeta de desglose muestra el total de gastos y el de
  ingresos del mes, y "Disponible" (ingresos menos gastos, que puede ser
  negativo).
- **RF-03:** Uno de los dos tipos (gastos o ingresos) está activo; de entrada,
  gastos. Tocar la tarjeta del tipo no activo lo activa. El título de la
  tarjeta ("Desglose de gastos" / "Desglose de ingresos"), el gráfico y la
  sección por categoría corresponden al tipo activo.
- **RF-04:** Un gráfico circular reparte el total del tipo activo entre las
  categorías con monto en el mes, cada una con su color (RF-14), y muestra en
  el centro el total del tipo activo y su porcentaje.
- **RF-05:** El porcentaje de la tarjeta activa y del centro del gráfico es,
  en gastos, los gastos del mes como porcentaje de los ingresos del mes; en
  ingresos, 100 %. Sin ingresos en el mes no se muestra porcentaje.
- **RF-06:** Un indicador compara el total del tipo activo con el del mes
  anterior ("−4,8 % vs mes anterior"), indicando la dirección también con
  texto o símbolo, no solo con color. Se muestra en verde cuando el cambio es
  favorable (gastos que bajan o ingresos que suben) y en rojo cuando no. En el mes en curso se compara el mismo
  tramo de días (del día 1 a hoy en ambos meses); en un mes pasado, los meses
  completos. El indicador no se muestra si el mes anterior tiene $0 en ese
  tipo o si sus datos no están disponibles; el resto del dashboard se muestra
  igual.

**Por categoría**

- **RF-07:** Bajo el desglose, la sección "Gastos por categoría" o "Ingresos
  por categoría" (según el tipo activo) muestra cuántas categorías incluye y
  una cuadrícula con una tarjeta por categoría: icono en su color, nombre y
  monto del mes.
- **RF-08:** La sección incluye todas las categorías activas del tipo activo,
  también las que no tienen movimientos en el mes, que se muestran atenuadas
  con $0. Las categorías archivadas solo aparecen si tienen movimientos en el
  mes, marcadas como archivadas.
- **RF-09:** Las categorías siguen el orden de la gestión de categorías; las
  archivadas que aparezcan van al final.
- **RF-10:** El enlace "Gestionar" de la sección abre la gestión de categorías
  (sustituye a la sección "Categorías" actual de Inicio).
- **RF-11:** Tocar la tarjeta de una categoría activa abre "Nuevo movimiento"
  con el tipo y esa categoría ya elegidos y la fecha de hoy (como el "+"),
  sea cual sea el mes mostrado. Las tarjetas de categorías archivadas no se
  pueden tocar.

**Resto de Inicio**

- **RF-12:** La sección "Medios de pago" se mantiene sin cambios, debajo de la
  sección por categoría.

**Datos**

- **RF-13:** Los importes del dashboard se calculan en la app a partir de los
  movimientos del mes de la familia activa, los mismos que muestra la pestaña
  "Movimientos" para ese mes, de modo que ambos coinciden siempre. Registrar,
  editar o eliminar un movimiento se refleja en el dashboard.

**Color de categoría**

- **RF-14:** Cada categoría tiene un color de una paleta cerrada de la app,
  con su versión para modo claro y oscuro. Una categoría sin color, o con uno
  que la app no conoce, usa un color de la paleta asignado según su posición
  en el orden de la gestión. El color se usa en el gráfico, en el icono de su
  tarjeta del dashboard y en la gestión de categorías.
- **RF-15:** Al crear y al editar una categoría, el Propietario puede elegir
  su color entre los de la paleta (como el selector de tonalidad del mockup
  `006-category-edit.png`); es opcional. Se rige por las mismas reglas de la
  005 que el icono: solo el Propietario, requiere conexión y cambiarlo sin
  guardar pide confirmación al salir.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Iconos de IA y de notificaciones de la cabecera que muestra el mockup: la
  cabecera no cambia.
- Usar los endpoints de reportes del backend (`/dashboard`, `/reports/*`):
  quedan para Reportes (11), aunque `/dashboard` ya trae el desglose por
  categoría (BG-02 de `BACKEND_GAPS.md`).
- Filtrar la pestaña "Movimientos" por categoría.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. El usuario abre la app (o vuelve a la pestaña Inicio) y ve el dashboard del
   mes en curso de la familia activa, con gastos activo.
2. Toca la tarjeta "Ingresos" para ver el reparto de ingresos; toca "Gastos"
   para volver.
3. Cambia de mes con las flechas y vuelve con "Mes actual".
4. Toca una categoría activa: se abre "Nuevo movimiento" con el tipo y la
   categoría elegidos; al guardar o cerrar vuelve a Inicio y el dashboard
   refleja el cambio.
5. Toca "Gestionar" en la sección por categoría para gestionar categorías (y,
   como Propietario, elegir su color), o en "Medios de pago" para gestionar
   medios de pago.

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Todos los importes están en la moneda de la familia y se muestran con el
  formato regional del dispositivo; los cálculos son exactos (sin redondeos
  intermedios). Los porcentajes se muestran con un decimal.
- El mes se interpreta con el calendario y la zona del dispositivo, igual que
  en "Movimientos".
- Monto de una categoría: suma de los movimientos del mes del tipo activo con
  esa categoría.
- Disponible = ingresos del mes − gastos del mes.
- Variación = (total del tramo actual − total del tramo anterior) / total del
  tramo anterior. Si hoy es un día que el mes anterior no tiene (p. ej. 31),
  el tramo del mes anterior es el mes completo.
- Color de categoría: dato opcional de la categoría, guardado en el backend
  como clave de la paleta de la app (igual que el icono, de 1 a 40
  caracteres); se puede quitar. Un color rechazado por el backend se informa
  como error al guardar la categoría.

## Comportamiento mobile y casos alternativos

<!-- Adapta la tabla usando MOBILE_GUIDELINES.md. Añade escenarios relevantes.
Marca No aplica con su motivo cuando corresponda. No presupongas soporte offline
ni conservación de todo el estado. Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Carga o acción en curso | Al abrir Inicio o cambiar de mes sin datos de ese mes: indicador de carga en lugar del desglose y la cuadrícula. Con datos guardados, se muestran mientras se actualizan. |
| Sin datos | Mes sin movimientos: totales y categorías en $0, gráfico vacío y un mensaje breve que invita a registrar con "+". Sin categorías activas del tipo activo: la sección lo indica y "Gestionar" sigue disponible. |
| Entrada inválida | No aplica: el dashboard no tiene entradas de texto. El selector de color no admite valores fuera de la paleta. |
| Error o espera excesiva | Mes sin copia local que no se puede cargar: mensaje de error con "Reintentar". Si solo falla el mes anterior, se oculta el indicador de comparación (RF-06). |
| Sin conexión o conexión interrumpida | Igual que "Movimientos" (007): un mes ya consultado se muestra desde la copia local, con aviso si tiene más de 24 h; un mes nunca consultado muestra error con "Reintentar". Al recuperar la conexión se actualiza. Elegir color requiere conexión (RF-15). |
| Cancelar o volver atrás | Desde "Nuevo movimiento" abierto por una categoría, cerrar o atrás vuelve a Inicio con el mismo mes y tipo. En la edición de categoría, salir con color cambiado sin guardar pide confirmación (RF-15). |
| Pasar a segundo plano y regresar | Se conservan mes y tipo activo, y los datos se actualizan al volver. |
| Recrear la pantalla | Se conservan el mes mostrado y el tipo activo. |
| Reabrir después de terminarse el proceso | Se vuelve al mes en curso con gastos activo. |
| Cambio de pestaña | Al volver a Inicio desde otra pestaña se conservan mes y tipo activo. |
| Cambio de familia activa | Se muestra el mes en curso de la nueva familia con gastos activo. |
| Otros puntos aplicables de la guía | Accesibilidad: el gráfico tiene una descripción textual (total y reparto por categoría); la información nunca depende solo del color (montos y nombres en las tarjetas, signo y texto en la comparación, "Archivada" escrito); el tipo activo se anuncia como seleccionado; cada color del selector tiene nombre; áreas táctiles de al menos 48 dp. Nombres largos y montos grandes se truncan o ajustan sin romper las tarjetas, también con texto grande; la cuadrícula se adapta al ancho. Textos traducibles. Sin montos en logs. |

**Puntos de la guía no aplicables y motivo:** permisos y capacidades del
dispositivo (no se usan), trabajo en segundo plano (no hay tareas fuera de la
pantalla), interacción y formularios en el dashboard (no tiene campos; el
formulario de alta es el de la 007).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Los importes del dashboard se calculan en la app a partir de los movimientos
  y las categorías; no se usan los endpoints de reportes del backend.
- El color de categoría lo guarda el backend (BG-01 de `BACKEND_GAPS.md`, ya
  implementado y documentado en el contrato §3.4.2).

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dado el usuario en Inicio, entonces ve el mes en curso; cuando toca ‹ ve el mes anterior y cuando toca "Mes actual" vuelve al mes en curso; en el mes en curso › no permite avanzar.
- **CA-02 · RF-01:** Dado Inicio en un mes pasado, cuando va a "Movimientos", entonces esta sigue en su propio mes; y al revés.
- **CA-03 · RF-02:** Dado un mes con $1.850.000 de ingresos y $1.120.450 de gastos, entonces ve ambos totales y "Disponible" $729.550; con más gastos que ingresos, "Disponible" es negativo.
- **CA-04 · RF-03:** Dado gastos activo, cuando toca la tarjeta "Ingresos", entonces el título pasa a "Desglose de ingresos" y el gráfico y la sección muestran ingresos; al tocar "Gastos" vuelve a gastos. El tipo activo se distingue también sin color.
- **CA-05 · RF-04/RF-14:** Dado un mes con gastos en varias categorías, entonces el gráfico muestra un segmento por categoría con monto, del color de la categoría y proporcional a su monto, y en el centro el total; las categorías en $0 no tienen segmento.
- **CA-06 · RF-05:** Dado un mes con $1.850.000 de ingresos y $1.120.450 de gastos, entonces gastos muestra 60,6 % e ingresos 100 %; en un mes sin ingresos no se muestra porcentaje.
- **CA-07 · RF-06:** Dado un mes pasado con gastos de $95.200 y un mes anterior de $100.000, entonces el indicador muestra −4,8 % vs mes anterior con símbolo de bajada; si el mes anterior tiene $0 de gastos, o no está disponible sin conexión, el indicador no aparece y el resto sí.
- **CA-08 · RF-06:** Dado que hoy es el día 10, entonces el indicador del mes en curso compara del 1 al 10 de este mes con del 1 al 10 del mes anterior; dado que hoy es el 31 y el mes anterior tiene 30 días, compara con el mes anterior completo.
- **CA-09 · RF-07/RF-08/RF-09:** Dado un tipo con categorías activas con y sin movimientos y una archivada con movimientos en el mes, entonces la sección muestra todas las activas en el orden de la gestión (las de $0 atenuadas) y la archivada al final marcada como archivada, con el número de categorías mostradas; una archivada sin movimientos en el mes no aparece.
- **CA-10 · RF-10/RF-12:** Dado Inicio, entonces ya no aparece la sección "Categorías"; "Gestionar" de la sección por categoría abre la gestión de categorías, y "Medios de pago" aparece debajo con su "Gestionar" como antes.
- **CA-11 · RF-11:** Dado Inicio en un mes pasado con ingresos activo, cuando toca una categoría de ingreso activa, entonces se abre "Nuevo movimiento" con tipo ingreso, esa categoría y la fecha de hoy; al tocar una archivada no ocurre nada.
- **CA-12 · RF-13:** Dado el dashboard de un mes, cuando registra, edita o elimina un movimiento de ese mes y vuelve a Inicio, entonces los totales, el gráfico y la categoría afectada reflejan el cambio y coinciden con el resumen de ese mes en "Movimientos".
- **CA-13 · RF-14:** Dada una categoría sin color o con una clave desconocida, entonces se muestra con un color de la paleta en el dashboard y en la gestión; los colores se ven correctamente en modo claro y oscuro.
- **CA-14 · RF-15:** Dado el Propietario editando una categoría con conexión, cuando elige un color y guarda, entonces el dashboard y la gestión muestran ese color; sin conexión se le informa y no se guarda; el Integrante no puede cambiarlo; salir con el color cambiado sin guardar pide confirmación.
- **CA-15 · Sin datos:** Dado un mes sin movimientos, entonces ve totales en $0, las categorías activas en $0, el gráfico vacío y la invitación a registrar.
- **CA-16 · Sin conexión:** Dado un mes ya consultado, cuando abre Inicio sin conexión, entonces ve su dashboard desde la copia local, con aviso si tiene más de 24 h; un mes nunca consultado muestra error con "Reintentar", que al recuperar la conexión carga el mes.
- **CA-17 · Estado:** Dado un mes pasado con ingresos activo, cuando gira la pantalla, pasa a segundo plano y vuelve, o cambia de pestaña y vuelve, entonces sigue en ese mes con ingresos activo; tras terminarse el proceso, vuelve al mes en curso con gastos.
- **CA-18 · Cambio de familia:** Dado Inicio en un mes pasado, cuando cambia de familia activa, entonces ve el mes en curso de la nueva familia con gastos activo.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Abrir Inicio; tocar ‹, luego "Mes actual"; intentar › en el mes en curso. | Mes en curso, anterior, en curso; no avanza a un mes futuro. |
| CA-02 | Ir a un mes pasado en Inicio; abrir "Movimientos"; cambiar allí de mes y volver a Inicio. | Cada pestaña mantiene su propio mes. |
| CA-03 | Mes con ingresos y gastos conocidos; otro con más gastos que ingresos. | Totales y "Disponible" exactos; negativo en el segundo. |
| CA-04 | Tocar "Ingresos" y luego "Gastos"; revisar con lector de pantalla. | Cambian título, gráfico y sección; el tipo activo se anuncia como seleccionado. |
| CA-05 | Mes con gastos en tres categorías y otras en $0. | Tres segmentos proporcionales con el color de cada categoría; total en el centro. |
| CA-06 | Mes con ingresos y gastos; mes solo con gastos. | 60,6 % / 100 % en el primero; sin porcentaje en el segundo. |
| CA-07 | Mes pasado y su anterior con totales conocidos; anterior en $0; anterior no consultado y sin conexión. | Variación correcta con símbolo; indicador oculto en los otros dos casos. |
| CA-08 | Fijar la fecha del dispositivo en el día 10 y en un día 31 tras un mes de 30 días, con movimientos antes y después de ese día en el mes anterior. | Solo cuentan los días del tramo; con el 31, el mes anterior completo. |
| CA-09 | Tipo con activas con y sin movimientos y archivadas con y sin movimientos en el mes. | Orden de la gestión, $0 atenuadas, archivada con movimientos al final y marcada, archivada sin movimientos ausente, contador correcto. |
| CA-10 | Abrir Inicio; tocar ambos "Gestionar". | Sin sección "Categorías"; abren la gestión de categorías y de medios de pago. |
| CA-11 | Mes pasado, ingresos activo: tocar una categoría activa y luego una archivada. | Alta con ingreso, categoría y fecha de hoy; la archivada no responde. |
| CA-12 | Registrar, editar y eliminar un movimiento del mes mostrado; comparar con "Movimientos". | El dashboard cambia en consecuencia y coincide con el resumen del mes. |
| CA-13 | Categoría sin color y otra con clave desconocida; modo claro y oscuro. | Color de respaldo de la paleta, legible en ambos modos. |
| CA-14 | Propietario elige color y guarda; repetir sin conexión; abrir como Integrante; cambiar color y salir sin guardar. | Color aplicado; aviso sin conexión; Integrante sin selector editable; confirmación al salir. |
| CA-15 | Mes sin movimientos. | Todo en $0, gráfico vacío e invitación a registrar. |
| CA-16 | Consultar un mes, quitar la conexión y reabrirlo; abrir un mes nunca consultado sin conexión y reconectar. | Copia local con aviso si es antigua; error con "Reintentar" que luego carga. |
| CA-17 | Mes pasado e ingresos activo; girar, segundo plano, cambiar de pestaña; luego terminar el proceso y reabrir. | Se conservan mes y tipo; tras terminar el proceso, mes en curso con gastos. |
| CA-18 | Mes pasado; cambiar de familia en la cabecera. | Mes en curso de la nueva familia, gastos activo. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

- Paleta de colores de categoría (cuántos y cuáles): se define en
  `docs/DESIGN.md` como parte del plan, a partir del mockup `006-category-edit.png`.
