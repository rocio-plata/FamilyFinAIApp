# SPEC: Identidad visual de la app (icono, nombre y logo)

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

La app todavía se presenta con el icono de la plantilla de Android Studio (fondo
verde cuadriculado y el robot de Android) y con el nombre técnico del proyecto.
Cualquier persona que instale FamilyFin AI necesita reconocerla entre sus apps
y ver una marca propia desde el primer contacto: en la pantalla de inicio del
teléfono, al abrirla y en la pantalla de acceso. Esta funcionalidad sustituye la
identidad de la plantilla por la de FamilyFin AI, usando el icono diseñado para
la app (`docs/ui/snapshots/familyfin-ai-icon.svg`).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

- El icono del lanzador es el de la plantilla: fondo verde (`#3DDC84`) con
  cuadrícula y el robot de Android. Es un icono adaptativo con capa monocromática,
  pero la capa también es el robot.
- El nombre que muestra el teléfono bajo el icono es "FamilyFinAIApp".
- La pantalla de acceso (spec 003) muestra, sobre el nombre "FamilyFin AI" y el lema,
  un logo que reutiliza el robot de Android sobre un cuadrado verde oscuro.
- Ya existe el diseño del icono propio: `docs/ui/snapshots/familyfin-ai-icon.svg`
  (tejado, tres barras de gráfico y una chispa ámbar sobre fondo menta), con la
  paleta de `docs/DESIGN.md`.
- Existe también una variante para el modo oscuro: la imagen
  `docs/ui/snapshots/familyfin_ai_app_icon_dark_mode.png` y su versión vectorial
  `docs/ui/snapshots/familyfin-ai-icon-dark.svg`, dibujada a partir de esa imagen.

Debe conservarse el comportamiento de la pantalla de acceso (spec 003: textos,
pestañas, validación, estados), el identificador de la app y los permisos.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** El icono del lanzador es el de FamilyFin AI, no el de la plantilla. Se
  ve completo (tejado, barras y chispa) con las formas de máscara que aplican los
  lanzadores (círculo, cuadrado redondeado y similares) y en la versión redonda.
- **RF-02:** Con los iconos temáticos de Android activados, el icono se muestra en
  una versión de un solo color que conserva la silueta reconocible de la marca.
- **RF-03:** El nombre que muestra el teléfono bajo el icono, en la lista de apps y
  en las apps recientes es "FamilyFin AI".
- **RF-04:** La pantalla de acceso muestra el logo de FamilyFin AI, en lugar del
  robot de Android, sobre el nombre y el lema, sin otros cambios en esa pantalla.
  Con el tema claro se usa el logo claro y con el tema oscuro, la variante oscura.
- **RF-05:** Ninguna superficie de la app muestra ya el icono de la plantilla: ni el
  lanzador, ni la pantalla de arranque que dibuja el sistema, ni las apps
  recientes, ni la pantalla de acceso.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Acordadas con la persona:

- Pantalla de arranque propia o animada: al abrir la app solo se ve la que dibuja
  el sistema con el icono de RF-01.
- Material para tiendas de aplicaciones (icono de 512 px de la ficha, imagen
  destacada, capturas): la app no se publica.
- Cambiar el diseño del icono o la paleta: se usan los diseños aportados, con las
  adaptaciones técnicas que exija la plataforma.
- Cambiar el identificador de la app (`applicationId`) o el nombre del paquete.

La variante para el modo oscuro **sí** está dentro del alcance (RF-04).

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. La persona instala o actualiza la app y ve el icono de FamilyFin AI con ese
   nombre en la pantalla de inicio y en la lista de apps.
2. Si usa iconos temáticos (Android 13 o posterior), lo ve en un solo color acorde a
   su fondo de pantalla.
3. Abre la app: el sistema muestra el icono durante el arranque y, sin sesión,
   aparece la pantalla de acceso con el logo de la marca, en su variante clara u
   oscura según el tema de la app.
4. Al cambiar a otra app, ve el icono y el nombre en las apps recientes.

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- **Fuente del diseño:** los SVG son la fuente de verdad: el claro,
  `docs/ui/snapshots/familyfin-ai-icon.svg`, y el oscuro,
  `docs/ui/snapshots/familyfin-ai-icon-dark.svg` (ambos 512 × 512). La imagen PNG
  del modo oscuro es solo la referencia a partir de la cual se dibujó el SVG. Se
  eliminó el archivo `familyfin-ai-icon.txt` (una versión codificada en Base64 que
  no coincidía con el SVG): no es fuente ni se usa.
- **Sin sombras:** el icono no reproduce la sombra suave del SVG claro (el lanzador
  aplica la suya). El SVG oscuro ya está dibujado sin filtros: su halo se resuelve
  con degradados.
- **Adaptaciones permitidas:** las que exija el formato de iconos de Android
  (zona segura, capa de fondo y de primer plano, capa de un solo color). Si una
  adaptación cambia el aspecto percibido, se documenta.
- **Nombre mostrado:** "FamilyFin AI", con esa grafía exacta y sin traducción.
- Los colores del icono son los del diseño aportado, coherentes con `DESIGN.md`.

## Comportamiento mobile y casos alternativos

<!-- Adapta la tabla usando MOBILE_GUIDELINES.md. Añade escenarios relevantes.
Marca No aplica con su motivo cuando corresponda. No presupongas soporte offline
ni conservación de todo el estado. Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Carga o acción en curso | No aplica: son recursos estáticos incluidos en la app. |
| Sin datos | No aplica. |
| Entrada inválida | No aplica. |
| Error o espera excesiva | No aplica. |
| Sin conexión o conexión interrumpida | No aplica: nada depende de la red. |
| Cancelar o volver atrás | No aplica. |
| Pasar a segundo plano y regresar | El icono y el nombre se ven igual en las apps recientes. |
| Recrear la pantalla | El logo de la pantalla de acceso se ve igual; el comportamiento de esa pantalla no cambia (spec 003). |
| Reabrir después de terminarse el proceso | Igual: el arranque en frío muestra el icono de RF-01. |
| Otros puntos aplicables de la guía | Pantallas y adaptación visual: el icono es legible en tamaños pequeños y con cualquier máscara; el logo no se corta con texto grande. Tema: el logo sigue el tema de la app (claro u oscuro) y se recompone al cambiarlo, sin reiniciar la pantalla. Accesibilidad: el logo es decorativo (el nombre va a su lado, como hoy) y tiene contraste suficiente sobre su fondo en ambos temas. Idiomas: el nombre de la marca no se traduce. |

**Puntos de la guía no aplicables y motivo:** ciclo de vida de operaciones,
conservación de estado, conectividad, persistencia, interacción y formularios,
permisos, rendimiento y trabajo en segundo plano (no hay operaciones, datos ni
interacción nuevos; el peso añadido es el de unos pocos recursos vectoriales),
privacidad (no se maneja información del usuario), navegación e interrupciones
(no cambia).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Usar el icono personalizado aportado, en sus variantes clara y oscura, y no el de
  Android.
- Seguir la metodología del proyecto y documentar la funcionalidad (SPEC, PLAN,
  TASKS con evidencia y estado en `docs/ROADMAP.md`).
- `minSdk` es 34: el icono adaptativo y la capa de un solo color están disponibles
  en todos los dispositivos compatibles.
- Sin dependencias nuevas, y sin cambiar el `applicationId`, el `namespace` ni los
  permisos.
- Los colores reutilizables siguen en `ui/theme`; los colores propios del icono
  forman parte de sus recursos.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dado el APK de debug instalado en un emulador, cuando se abre la lista de apps o la pantalla de inicio, entonces se ve el icono de FamilyFin AI (tejado, barras y chispa) y no el robot ni la cuadrícula verde.
- **CA-02 · RF-01:** Dado un lanzador con máscara circular y otro con cuadrado redondeado, cuando se muestra el icono, entonces la marca aparece completa, sin recortes del tejado, las barras ni la chispa.
- **CA-03 · RF-02:** Dado Android 13 o posterior con iconos temáticos activados, cuando se muestra el icono, entonces aparece en un solo color con la silueta de la marca reconocible.
- **CA-04 · RF-03:** Dada la app instalada, cuando se ve su nombre en la pantalla de inicio, la lista de apps, los ajustes de aplicaciones y las apps recientes, entonces es "FamilyFin AI".
- **CA-05 · RF-04:** Dado un primer uso, cuando se abre la app, entonces la pantalla de acceso muestra el logo de FamilyFin AI sobre el nombre y el lema: la variante clara con el tema claro y la oscura con el tema oscuro, y el resto de la pantalla se comporta como en la spec 003.
- **CA-06 · RF-05:** Dado un arranque en frío, cuando el sistema dibuja la pantalla de arranque, entonces muestra el icono de FamilyFin AI.
- **CA-07 · RF-05:** Dada la app completa, cuando se recorren el lanzador, el arranque, las apps recientes y el acceso, entonces en ninguna aparece el robot de Android, y el repositorio no conserva recursos de la plantilla sin uso.
- **CA-08 · RF-04:** Dados los tests automáticos existentes, cuando se ejecutan tras el cambio, entonces siguen pasando sin modificar lo que comprueban.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Instalar el APK de debug en el emulador; abrir el lanzador | Icono de FamilyFin AI; sin robot ni cuadrícula |
| CA-02 | Ver el icono con máscara circular y con cuadrado redondeado (forma de icono configurable en el emulador) | Marca completa en ambas |
| CA-03 | Activar iconos temáticos y fondo de pantalla en el emulador (Android 13+) | Icono monocromo reconocible |
| CA-04 | Ver el nombre en el lanzador, en Ajustes > Apps y en recientes | "FamilyFin AI" en los tres |
| CA-05 | Datos de la app borrados; abrir con tema claro y con oscuro, y cambiar de tema con la pantalla abierta | Variante clara u oscura del logo según el tema, sobre nombre y lema; resto igual |
| CA-06 | Cerrar el proceso y abrir desde el lanzador; capturar el arranque | Icono de FamilyFin AI en la pantalla de arranque |
| CA-07 | Recorrer las cuatro superficies; buscar en el repositorio los recursos de la plantilla | Sin robot visible; sin recursos de plantilla |
| CA-08 | Ejecutar la suite automática completa | Todo en verde sin cambiar lo que comprueban |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

- Resuelto: nombre mostrado "FamilyFin AI" (cambia el nombre técnico `app_name`
  del proyecto, y con él la etiqueta del lanzador).
- Resuelto: se incluye el icono temático de Android 13 y posteriores (RF-02).
- Resuelto: la funcionalidad se registra en el roadmap como paso 4, en la carpeta
  `004-brand-identity`, y los pasos pendientes se renumeran.
- Resuelto: las exclusiones propuestas quedan acordadas, salvo la de la variante
  para el modo oscuro, que pasa a estar dentro del alcance (RF-04).
- Resuelto: se prescinde de la sombra suave del SVG claro.
- Resuelto: los SVG son la fuente de verdad (claro y oscuro). El oscuro se dibujó a
  mano a partir de `familyfin_ai_app_icon_dark_mode.png`; no es un vectorizado
  automático, por lo que puede diferir en detalles finos de la imagen.
- Resuelto: la variante oscura se aplica al logo de la pantalla de acceso según el
  tema de la app (RF-04). El icono del lanzador es único: Android no permite
  cambiarlo con el tema; solo existe el icono temático de RF-02.
- Resuelto: se elimina el archivo `familyfin-ai-icon.txt`.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los puntos
mobile relevantes están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
