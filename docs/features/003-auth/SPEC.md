# SPEC: Autenticación y sesión (registro, login, renovación y cierre)

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

Cualquier persona que use FamilyFin AI necesita su propia cuenta para acceder a
las finanzas de sus familias. Esta funcionalidad le permite crear una cuenta,
iniciar sesión y permanecer conectada de forma segura entre usos de la app, y
garantiza que la sesión se cierre y sus datos se borren del teléfono cuando el
backend la invalida. Es la base sobre la que el resto de funcionalidades
consultan y modifican datos reales.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

La app abre directamente la pantalla principal. La cabecera (spec 002) inicia
sesión en segundo plano con credenciales de desarrollo definidas en
`local.properties`, sin pantalla de login, sin registro, sin renovación de la
sesión y sin cierre de sesión. El build de debug falla si esas credenciales no
están definidas. La sesión no sobrevive al terminarse el proceso.

Debe conservarse el comportamiento de la cabecera y de las pestañas de la 002
(familia favorita, selector, contador, inicial, caché offline con aviso de
desactualización), salvo el cambio de RF-09 sobre el borrado de la caché.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Sin sesión, la app muestra la pantalla de inicio de sesión con
  email, contraseña, el botón "Iniciar sesión" y un acceso a "Crear cuenta". Los
  campos de contraseña aparecen ocultos y tienen un control para mostrar u
  ocultar lo escrito, también en el registro. Con
  credenciales correctas, el usuario entra a la pantalla principal.
- **RF-02:** La pantalla de registro pide nombre visible, email, contraseña y
  repetir contraseña, con el botón "Crear cuenta" y un acceso a "Iniciar
  sesión". Al registrarse con éxito, el usuario entra directamente a la pantalla
  principal con su familia personal, sin pasos intermedios.
- **RF-12:** Inicio de sesión y registro se alternan mediante dos pestañas
  ("Iniciar sesión" y "Crear cuenta") sobre el formulario y mediante el enlace
  al pie de cada uno. La pestaña activa se distingue sin depender solo del
  color. Al alternar, cada formulario conserva lo escrito.
- **RF-03:** Los formularios validan los datos antes de enviarlos (ver *Datos y
  reglas*). Cada campo muestra su error al perder el foco y todos al pulsar el
  botón; el error desaparece cuando el valor se corrige. Con errores, no se
  envía nada al backend.
- **RF-04:** Los rechazos del backend se muestran con mensajes comprensibles,
  sin códigos técnicos:
  - Login con credenciales incorrectas o usuario inexistente: "Correo o
    contraseña incorrectos." Nunca se indica cuál de los dos falla.
  - Registro con un email ya registrado: mensaje neutro "No pudimos crear la
    cuenta con esos datos. Si ya tienes una cuenta, inicia sesión." No se
    confirma que el email exista ni se marca el campo email.
  - Otros errores: mensaje genérico con opción de reintentar.
- **RF-05:** La sesión se conserva al cerrar y reabrir la app, incluso tras
  terminarse el proceso o reiniciar el teléfono, hasta que el usuario cierre
  sesión o el backend la invalide. Al reabrir con sesión, se entra directamente
  a la pantalla principal.
- **RF-06:** La sesión se renueva de forma automática e invisible para el
  usuario cuando su acceso caduca, y la operación que lo detectó se completa sin
  intervención. Varias operaciones simultáneas que detectan la caducidad no
  provocan un cierre de sesión.
- **RF-07:** Cuando el backend invalida la sesión, la app la cierra (RF-09) y
  muestra el inicio de sesión con un mensaje:
  - Sesión expirada (el backend no reconoce el token de renovación: no existe o
    caducó): "Tu sesión expiró. Inicia sesión de nuevo."
  - Posible robo de sesión (el backend recibe un token de renovación ya usado o
    revocado, por ejemplo tras cerrar las sesiones desde otro dispositivo):
    "Cerramos tu sesión por seguridad. Inicia sesión de nuevo."
- **RF-08:** Existe la capacidad de cerrar sesión: avisa al backend, que cierra
  las sesiones del usuario en todos sus dispositivos, termina la sesión local
  (RF-09) y muestra el inicio de sesión. Sin conexión no se cierra la sesión:
  se muestra "Necesitas conexión para cerrar sesión." y la sesión sigue activa
  en el teléfono. Si el backend responde que la sesión ya no es válida, se
  termina igualmente la sesión local. En esta spec no hay botón para
  ejecutarla; el botón y su confirmación ("Se cerrará tu sesión en todos tus
  dispositivos") se incorporan en la spec de perfil.
- **RF-09:** Al terminar una sesión (RF-07 o RF-08) se eliminan del teléfono la
  sesión y los datos guardados de la cabecera (familias, invitaciones y nombre).
  Tras cerrar sesión, el inicio de sesión aparece con los campos vacíos. Esto
  sustituye la regla de la 002 según la cual esos datos no se borraban al
  cerrar sesión.
- **RF-10:** Si se abre la app sin conexión y hay una sesión guardada, se entra
  a la pantalla principal con los datos guardados de la cabecera (con el aviso
  de posible desactualización de la 002) y se actualizan al recuperar la
  conexión. La falta de conexión no cierra la sesión.
- **RF-11:** Se elimina el inicio de sesión automático con credenciales de
  desarrollo: el build de debug ya no exige `DEV_EMAIL` ni `DEV_PASSWORD`, y en
  todos los builds se accede mediante RF-01 o RF-02. Se conserva la
  configuración de la dirección del backend (`DEV_TARGET`, `BACKEND_BASE_URL`).

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Botón de cerrar sesión y su confirmación: spec de perfil (RF-08).
- Cambiar la contraseña y editar el nombre visible: spec de perfil.
- Recuperación de contraseña ("Olvidé mi contraseña").
- Verificación del email por correo y protección completa contra la
  enumeración de cuentas: requiere cambios en el backend (ver
  `BACKEND_GAPS.md`, BG-01).
- Login con proveedores externos (Google, etc.), biometría o PIN de la app.
- Cerrar sesión solo en este dispositivo: el backend no lo permite.
- Que el avatar de la cabecera abra un menú: sigue sin acción (RF-06 de la 002).
- Elementos de la referencia visual que no se implementan: "Recordarme" (la
  sesión siempre se conserva, RF-05), "¿Olvidaste tu contraseña?", acceso con
  huella o Face ID, acceso o registro con Google o Apple, indicador de
  fortaleza de contraseña, aceptación de Términos de Servicio y Política de
  Privacidad, suscripción a sugerencias por correo, distintivo de versión y
  los textos promocionales o de seguridad ("Configuración en 60 segundos",
  "Bóveda familiar encriptada", "Cifrado bancario AES-256", "Copiloto IA 100%
  privado"), que no describen el comportamiento real de la app.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. Primer uso: el usuario abre la app y ve el inicio de sesión.
2. Sin cuenta, toca "Crear cuenta", completa nombre, email, contraseña y su
   repetición, y pulsa "Crear cuenta". Entra a la pantalla principal con su
   familia personal.
3. Con cuenta, escribe email y contraseña y pulsa "Iniciar sesión". Entra a la
   pantalla principal con su familia favorita.
4. Usos posteriores: abre la app y entra directamente a la pantalla principal.
5. Si el backend invalida la sesión mientras usa la app o al abrirla, vuelve al
   inicio de sesión con el mensaje correspondiente (RF-07).
6. Alternativas: la pestaña o el enlace "Iniciar sesión" muestra el login; la
   pestaña o el enlace "Crear cuenta" muestra el registro (RF-12).

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- **Nombre visible (registro):** obligatorio, de 1 a 60 caracteres tras quitar
  espacios al inicio y al final.
- **Email:** obligatorio, con formato de correo válido. Se quitan espacios al
  inicio y al final. El backend lo trata sin distinguir mayúsculas.
- **Contraseña:** obligatoria; en el registro, mínimo 8 caracteres. En el
  login solo se exige que no esté vacía (no se revela la regla de longitud a
  quien intenta entrar). Se envía tal cual, sin recortar espacios.
- **Repetir contraseña (registro):** debe coincidir con la contraseña.
- La contraseña nunca se guarda en el teléfono ni aparece en logs o mensajes.
- Al registrarse, el backend crea la familia personal ("Mis finanzas
  personales") con el usuario como propietario; la cabecera la muestra como
  favorita.
- El acceso caduca a los 15 minutos y la sesión puede renovarse durante 30 días
  desde la última renovación (definido por el backend). Cada renovación invalida
  la anterior; reutilizar una ya usada hace que el backend cierre todas las
  sesiones del usuario.
- La sesión pertenece a un único usuario. Iniciar sesión con otro usuario en el
  mismo teléfono nunca muestra datos del anterior (se conserva CA-18 de la 002).
- **Backend:** `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh` y
  `POST /auth/logout` (`docs/backend/api-contract.md` §2.1 y §3.2).

## Comportamiento mobile y casos alternativos

<!-- Adapta la tabla usando MOBILE_GUIDELINES.md. Añade escenarios relevantes.
Marca No aplica con su motivo cuando corresponda. No presupongas soporte offline
ni conservación de todo el estado. Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Carga o acción en curso | Al enviar login o registro, el botón muestra progreso y queda deshabilitado junto con los campos; no se pueden lanzar envíos duplicados. Al abrir la app con sesión, no se muestra el login ni siquiera por un instante. |
| Sin datos | Primer uso o sesión terminada: inicio de sesión con campos vacíos. |
| Entrada inválida | Errores por campo según RF-03; el botón sigue disponible y, al pulsarlo con errores, se muestran todos y se lleva el foco al primero. |
| Error o espera excesiva | Error del backend o espera excesiva (mismos límites que la 002): mensaje según RF-04 con opción de reintentar; los datos escritos se conservan. |
| Sin conexión o conexión interrumpida | Login o registro sin conexión: "Sin conexión. Revisa tu red e inténtalo de nuevo.", conservando los datos escritos. Con sesión guardada: RF-10. Una renovación que falla por red no cierra la sesión; se reintenta cuando haya conexión. |
| Cancelar o volver atrás | Atrás con "Crear cuenta" activa vuelve a "Iniciar sesión". Atrás en el login sale de la app. Tras entrar a la pantalla principal, Atrás no vuelve al login ni al registro. |
| Pasar a segundo plano y regresar | Los datos escritos se conservan. Un envío en curso termina y su resultado se aplica al volver. |
| Recrear la pantalla | Se conservan los datos escritos de ambos formularios, la pestaña activa y los errores mostrados. |
| Reabrir después de terminarse el proceso | Con sesión: pantalla principal (RF-05). Sin sesión: login con campos vacíos; los datos escritos no se conservan. |
| Otros puntos aplicables de la guía | Teclado: tipo email en el campo email; "Siguiente" pasa al campo siguiente y la acción del último envía el formulario. El contenido se desplaza para que campos y botón sigan visibles con el teclado abierto y con texto grande. Accesibilidad: cada campo y su error se anuncian con etiqueta; el control mostrar/ocultar contraseña tiene etiqueta; áreas táctiles de al menos 48 dp; los errores no se comunican solo con color. Textos traducibles. Privacidad: contraseñas y tokens nunca en logs, mensajes ni capturas de estado; los campos de contraseña se ocultan por defecto. |

**Puntos de la guía no aplicables y motivo:** permisos y capacidades del
dispositivo (no se piden), trabajo en segundo plano diferido (no hay tareas
programadas), listas grandes e imágenes (no hay), orientación (solo vertical,
definido en la 001).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Usar los endpoints de `docs/backend/api-contract.md`; su implementación real
  en el backend no está verificada.
- La app no revela si un email está registrado (RF-04), aunque el backend hoy
  lo permita a quien llame directamente a la API (BG-01).
- La sesión se guarda en el teléfono de forma protegida: no legible por otras
  apps ni incluida en copias de seguridad. El mecanismo se decide en PLAN.md.
- Referencia visual orientativa, no vinculante:
  `../../ui/snapshots/004-inicio-sesion-cuenta-nueva.png` y el prompt de login
  y registro de `docs/ui/prompts-stitch.md` (logo, nombre y lema "Smarter
  family finances" arriba, pestañas, campos con icono y botón principal).
  Prevalece esta spec: los elementos excluidos en *Fuera de alcance* no se
  muestran.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dado un primer uso, cuando se abre la app, entonces se muestra el inicio de sesión con campos vacíos y acceso a "Crear cuenta".
- **CA-02 · RF-01:** Dada una cuenta existente, cuando el usuario inicia sesión con sus credenciales, entonces entra a la pantalla principal y la cabecera muestra su familia favorita y su inicial.
- **CA-03 · RF-02:** Dado un email no registrado, cuando el usuario completa el registro con datos válidos, entonces entra a la pantalla principal y la cabecera muestra su familia personal.
- **CA-04 · RF-03:** Dado un email con formato inválido, una contraseña de registro de menos de 8 caracteres, contraseñas que no coinciden o un nombre vacío, cuando el campo pierde el foco o se pulsa el botón, entonces se muestra el error del campo, no se envía nada al backend y el error desaparece al corregirlo.
- **CA-05 · RF-04:** Dadas credenciales incorrectas o un email no registrado, cuando se inicia sesión, entonces se muestra "Correo o contraseña incorrectos." en ambos casos, sin indicar el campo.
- **CA-06 · RF-04:** Dado un email ya registrado, cuando se intenta crear la cuenta, entonces se muestra el mensaje neutro de RF-04 y el campo email no se marca con error.
- **CA-07 · RF-05:** Dada una sesión iniciada, cuando se termina el proceso (o se reinicia el teléfono) y se reabre la app, entonces se entra directamente a la pantalla principal sin ver el login.
- **CA-08 · RF-06:** Dada una sesión con el acceso caducado, cuando la app consulta datos, entonces los obtiene sin mostrar el login ni errores.
- **CA-09 · RF-06:** Dado el acceso caducado, cuando varias consultas se lanzan a la vez, entonces todas se completan y la sesión sigue activa.
- **CA-10 · RF-07:** Dada una sesión cuyo token de renovación el backend ya no reconoce (no existe o caducó), cuando la app intenta renovarla, entonces muestra el login con "Tu sesión expiró. Inicia sesión de nuevo."
- **CA-11 · RF-07:** Dado un token de renovación que el backend considera ya usado o revocado (posible robo de sesión; ocurre también tras cerrar las sesiones desde otro dispositivo), cuando la app intenta renovarla, entonces muestra el login con "Cerramos tu sesión por seguridad. Inicia sesión de nuevo."
- **CA-12 · RF-08:** Dada una sesión iniciada, cuando se ejecuta el cierre de sesión, entonces el backend recibe el aviso, la app muestra el login y la sesión anterior deja de ser válida en el backend.
- **CA-13 · RF-09:** Dada una sesión terminada (RF-07 o RF-08), cuando se reabre la app sin conexión, entonces se muestra el login con campos vacíos y no queda ningún dato del usuario anterior.
- **CA-14 · RF-10:** Dada una sesión guardada y datos de cabecera guardados, cuando se abre la app sin conexión, entonces se entra a la pantalla principal con esos datos y, al recuperar la conexión, se actualizan sin cerrar la sesión.
- **CA-15 · RF-11:** Dado un `local.properties` sin `DEV_EMAIL` ni `DEV_PASSWORD`, cuando se compila el build de debug, entonces compila y la app muestra el login.
- **CA-16 · RF-01, RF-02:** Dado un envío en curso, cuando se pulsa el botón repetidamente, entonces el backend recibe un solo envío.
- **CA-17 · RF-01, RF-02:** Dado el login o el registro sin conexión, cuando se envía, entonces se muestra el mensaje de sin conexión y los datos escritos se conservan.
- **CA-18 · RF-01, RF-02:** Dado el registro con datos escritos, cuando se recrea la pantalla, entonces sigue en el registro con los datos y errores; y al pulsar Atrás vuelve al login.
- **CA-19 · RF-01:** Dada la pantalla principal tras entrar, cuando se pulsa Atrás, entonces no se vuelve al login ni al registro.
- **CA-20 · RF-01, RF-02:** Dado TalkBack y tamaño de fuente máximo, cuando se recorren los formularios con el teclado abierto, entonces cada campo, error y control se anuncia con su etiqueta y el botón sigue accesible.
- **CA-21 · RF-08:** Dada una sesión iniciada y sin conexión, cuando se ejecuta el cierre de sesión, entonces se muestra "Necesitas conexión para cerrar sesión.", la sesión sigue activa y los datos guardados se conservan.
- **CA-22 · RF-01, RF-02:** Dado un campo de contraseña con texto, cuando se pulsa el control mostrar/ocultar, entonces el texto alterna entre visible y oculto, y el control anuncia su estado al lector de pantalla.
- **CA-23 · RF-12:** Dado texto escrito en ambos formularios, cuando se alterna entre "Iniciar sesión" y "Crear cuenta" por las pestañas o los enlaces del pie, entonces se muestra el formulario elegido con lo que tenía escrito y la pestaña activa se anuncia como seleccionada.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Instalación limpia; abrir la app | Login vacío con acceso a registro |
| CA-02 | Cuenta existente; iniciar sesión | Pantalla principal con familia favorita e inicial |
| CA-03 | Email nuevo; registrarse | Pantalla principal con familia personal |
| CA-04 | Probar cada dato inválido saliendo del campo y pulsando el botón; corregir | Error por campo; sin envío; error desaparece |
| CA-05 | Login con contraseña errónea y con email inexistente | Mismo mensaje en ambos casos |
| CA-06 | Registrarse con un email existente | Mensaje neutro; email sin error |
| CA-07 | Iniciar sesión; matar el proceso o reiniciar; reabrir | Pantalla principal directa |
| CA-08 | Sesión con acceso caducado (esperar 15 min o backend de prueba); recargar | Datos cargados sin login ni error |
| CA-09 | Acceso caducado; lanzar varias consultas a la vez | Todas completan; sesión activa |
| CA-10 | Que el backend no reconozca el token de renovación (inexistente o caducado); recargar | Login con mensaje de sesión expirada |
| CA-11 | Reutilizar una renovación ya usada o revocada (p. ej. cerrar las sesiones desde otro cliente, esperar la caducidad del acceso y recargar) | Login con mensaje de seguridad |
| CA-12 | Ejecutar el cierre de sesión; usar la sesión anterior contra el backend | Login; backend rechaza la sesión anterior |
| CA-13 | Terminar la sesión; reabrir sin red | Login vacío; sin datos del usuario anterior |
| CA-14 | Con sesión y caché, abrir sin red; activar red | Pantalla principal con caché; se actualiza; sigue la sesión |
| CA-15 | Quitar `DEV_EMAIL`/`DEV_PASSWORD`; compilar debug; abrir | Compila; se muestra el login |
| CA-16 | Pulsar varias veces rápido "Iniciar sesión" / "Crear cuenta" | Un solo envío |
| CA-17 | Sin red; enviar login y registro | Mensaje de sin conexión; datos conservados |
| CA-18 | Escribir en registro; rotar o recrear; pulsar Atrás | Datos y errores conservados; vuelve al login |
| CA-19 | Entrar a la app; pulsar Atrás | Sale de la app sin mostrar login |
| CA-20 | TalkBack y fuente máxima en login y registro | Etiquetas anunciadas; nada cortado ni inaccesible |
| CA-21 | Sin red; ejecutar el cierre de sesión; reabrir | Mensaje de conexión necesaria; sigue en la pantalla principal con sus datos |
| CA-22 | Escribir una contraseña; alternar mostrar/ocultar con y sin TalkBack | Texto visible/oculto; estado anunciado |
| CA-23 | Escribir en ambos formularios; alternar por pestañas y por enlaces; con TalkBack | Formulario correcto con su texto; pestaña activa anunciada |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

- Resuelto · Ajuste posterior (pruebas manuales de T-21, 2026-11-10): una
  sesión cerrada desde otro dispositivo con `logout` deja el token de renovación
  revocado y el backend responde `AUTH.POSSIBLE_TOKEN_THEFT`, no
  `AUTH.INVALID_REFRESH_TOKEN`. RF-07, CA-10 y CA-11 se precisaron para que
  «expirada» (token inexistente o caducado) y «revocada o reutilizada» (mensaje de
  seguridad) no se solapen; sigue el contrato del backend.
- Resuelto · Cambio en la 002: RF-09 sustituye la regla de la 002 de no
  borrar los datos guardados al cerrar sesión. Actualizados
  `002-main-header/SPEC.md` y `002-main-header/PLAN.md`.
- Resuelto: alcance (registro, login, sesión persistente, renovación y cierre;
  cambio de contraseña en perfil); cierre de sesión usa el backend y avisa del
  efecto en todos los dispositivos (en perfil); sin botón hasta perfil;
  confirmar contraseña en el registro; validación al perder el foco y al
  enviar; mensaje neutro ante email registrado; borrar sesión y caché al
  terminar; mensajes distintos para expiración y robo; entrar con caché sin
  conexión; login vacío tras cerrar sesión; eliminar credenciales de desarrollo;
  cierre de sesión exige conexión; en el login la contraseña solo se valida no
  vacía; control mostrar/ocultar contraseña; textos de los mensajes aceptados.
  El logo y el lema siguen la referencia orientativa. Pestañas además del
  enlace al pie; sin indicador de fortaleza; términos y privacidad fuera de
  alcance.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los puntos
mobile relevantes están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
