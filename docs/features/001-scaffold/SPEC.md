# SPEC: Scaffold de la pantalla de inicio

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->


## Qué construimos y para quién

<!-- Qué necesidad resolvemos, quién tiene esa necesidad y qué podrá hacer.
Describe el objetivo en lenguaje de producto. -->

Los miembros de una familia necesitan un punto de entrada a FamilyFin AI. Esta
funcionalidad entrega solo el esqueleto de la pantalla de inicio: cabecera,
espacio para el FAB y barra inferior de navegación, para que las siguientes
especificaciones completen el detalle. Referencia visual:
`docs/ui/snapshots/001-home.png`.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

La app solo muestra un saludo de ejemplo ("Hello Android!"); no hay cabecera,
navegación ni pestañas. No hay comportamiento existente que conservar.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** La app abre en la pantalla Inicio con una cabecera que muestra,
  con contenido estático de ejemplo, un selector de familia (nombre y rol), un
  icono de notificaciones y un avatar. Los tres son visibles y no ejecutan
  ninguna acción.
- **RF-02:** La cabecera se muestra en las 5 pestañas.
- **RF-03:** Inicio muestra un cuerpo placeholder, sin datos ni secciones.
- **RF-04:** Inicio muestra un FAB "+" visible, sin acción. No se muestra en
  las demás pestañas.
- **RF-05:** Una barra inferior ofrece 5 pestañas: Inicio, Movimientos,
  Reportes, Presupuestos y Asistente. Inicio está seleccionada al abrir.
- **RF-06:** Cada pestaña distinta de Inicio muestra un placeholder con solo el
  nombre de la sección.
- **RF-07:** Atrás desde otra pestaña vuelve a Inicio; Atrás desde Inicio sale
  de la app.
- **RF-08:** Al recrear la pantalla o reabrir tras terminarse el proceso, la app inicia en Inicio. Al volver desde segundo plano o desde otra app se mantiene la pestaña en que estaba.
- **RF-09:** La app solo admite orientación vertical.
- **RF-11:** En teléfonos, con navegación por gestos y por 3 botones, la
  cabecera queda bajo la barra de estado y los recortes de pantalla, y la barra
  inferior sobre la navegación del sistema, ambas completas y sin cortarse.
- **RF-12:** Los textos visibles y las etiquetas accesibles están preparados
  para traducción, con español por defecto.
- **RF-13:** Pulsar repetidamente una pestaña o el FAB no produce efectos
  duplicados ni errores. Atrás, incluido el gesto predictivo, sigue RF-07.
- **RF-10:** Los elementos interactivos tienen etiqueta accesible y área
  táctil adecuada.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Permisos (p. ej. notificaciones): se definen en la spec de notificaciones.
- Detalle del selector de familia, notificaciones, filtro de tiempo, desglose
  de gastos e ingresos y su detalle (specs posteriores).
- Uso del backend y almacenamiento de datos en el dispositivo: sin datos reales
  en este scaffold.
- Opciones y acciones del FAB.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. El usuario abre la app y ve Inicio con cabecera, cuerpo placeholder, FAB y
   barra inferior.
2. Toca otra pestaña y ve su placeholder, con la cabecera y sin FAB.
3. Pulsa Atrás y regresa a Inicio; pulsa Atrás en Inicio y sale de la app.

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Los datos de la cabecera son ejemplos estáticos (p. ej. "Familia Plata").
- Orden de pestañas: Inicio, Movimientos, Reportes, Presupuestos, Asistente.
- Sin uso del backend ni almacenamiento en el dispositivo (confirmado).

## Comportamiento mobile y casos alternativos

<!-- Adapta la tabla usando MOBILE_GUIDELINES.md. Añade escenarios relevantes.
Marca No aplica con su motivo cuando corresponda. No presupongas soporte offline
ni conservación de todo el estado. Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Carga o acción en curso | No aplica: no hay carga. |
| Sin datos | El cuerpo muestra el placeholder. |
| Entrada inválida | No aplica: no hay entradas, operaciones ni red. |
| Error o espera excesiva | No aplica: no hay entradas, operaciones ni red. |
| Sin conexión o conexión interrumpida | No aplica: no hay entradas, operaciones ni red. |
| Cancelar o volver atrás | Ver RF-07. |
| Pasar a segundo plano y regresar (incluye volver desde otra app) | Se mantiene la pestaña en que estaba. |
| Recrear la pantalla | Inicia en Inicio (RF-08). |
| Reabrir después de terminarse el proceso | Inicia en Inicio (RF-08). |
| Otros puntos aplicables de la guía | Orientación vertical (RF-09); accesibilidad (RF-10); áreas del sistema y tamaños de teléfono (RF-11); idioma (RF-12); pulsaciones repetidas (RF-13). |

**Puntos de la guía no aplicables y motivo:** carga y error, conectividad, persistencia, permisos, formularios, trabajo en segundo plano y privacidad: este scaffold no tiene datos, red ni entradas (confirmado).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Referencia visual orientativa, no vinculante: `docs/ui/snapshots/001-home.png`.
- Implementar con Kotlin, Jetpack Compose y Material 3 (confirmado).

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01, RF-03, RF-05:** Dada la app cerrada, cuando se abre, entonces se ve Inicio seleccionada con cabecera, cuerpo placeholder, FAB y las 5 pestañas.
- **CA-02 · RF-01, RF-04:** Dado Inicio, cuando se tocan el selector, las notificaciones, el avatar o el FAB, entonces no ocurre ninguna acción.
- **CA-03 · RF-02, RF-04, RF-06:** Dado Inicio, cuando se toca otra pestaña, entonces se ve su nombre, la cabecera y ningún FAB.
- **CA-04 · RF-07:** Dada otra pestaña, cuando se pulsa Atrás, entonces se muestra Inicio; y desde Inicio, Atrás sale de la app.
- **CA-05 · RF-08:** Dada otra pestaña seleccionada, cuando se recrea la pantalla o se reabre la app tras terminar el proceso, entonces se muestra Inicio.
- **CA-06 · RF-09:** Dado el dispositivo girado, cuando se abre la app, entonces permanece en vertical.
- **CA-08 · RF-11:** Dado un teléfono pequeño y uno grande, con navegación por gestos y por 3 botones, cuando se abre la app, entonces la cabecera y las 5 pestañas se ven completas y sin quedar bajo las áreas del sistema.
- **CA-09 · RF-13:** Dado Inicio, cuando se pulsa repetidamente una pestaña o el FAB, entonces no hay efectos duplicados ni errores.
- **CA-07 · RF-10:** Dado un lector de pantalla, cuando se recorren cabecera, FAB y pestañas, entonces cada elemento se anuncia con su etiqueta.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Abrir la app | Inicio con todos los elementos |
| CA-02 | Tocar cada elemento de la cabecera y el FAB | Sin efecto |
| CA-03 | Tocar cada pestaña | Nombre, cabecera, sin FAB |
| CA-04 | Atrás desde otra pestaña y desde Inicio | Vuelve a Inicio / sale |
| CA-05 | Recrear y matar el proceso en otra pestaña | Inicia en Inicio |
| CA-06 | Girar el dispositivo | Sigue en vertical |
| CA-08 | Abrir en emulador de teléfono pequeño y grande, con gestos y con 3 botones | Cabecera y 5 pestañas completas y visibles |
| CA-09 | Pulsar varias veces rápido pestañas y FAB; usar gesto Atrás | Sin duplicados ni errores; RF-07 se cumple |
| CA-07 | Usar TalkBack | Etiquetas anunciadas |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los puntos
mobile relevantes están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
