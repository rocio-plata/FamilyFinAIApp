# PLAN: Scaffold de la pantalla de inicio

**SPEC de referencia:** `docs/features/001-scaffold/SPEC.md`
**Versión de la spec revisada:** SPEC en estado Aprobada, revisada el 2026-10-18 (sin commit propio: cambios sin confirmar en el árbol de trabajo)
**Estado:** Aprobado <!-- Borrador | En revisión | Aprobado -->

<!-- PARA LA PERSONA
Copia esta plantilla como PLAN.md junto a la SPEC.md aprobada.
Este documento define la solución técnica. Una vez revisado, el agente puede
derivar TASKS.md con tareas, dependencias y comprobaciones.
-->

<!-- PARA EL AGENTE
- Lee la SPEC.md aprobada, las instrucciones del proyecto y MOBILE_GUIDELINES.md.
  Si falta un documento necesario o la spec no está aprobada, indícalo antes de avanzar.
- Inspecciona el repositorio. Referencia rutas verificadas y distingue las nuevas propuestas.
- Propón una solución proporcional al alcance y coherente con el proyecto.
  Reutiliza lo existente y justifica nuevas dependencias o cambios de arquitectura.
- Distingue hechos, decisiones confirmadas y propuestas. Consulta las decisiones
  no resueltas; haz pocas preguntas por vez y actualiza el plan con las respuestas.
- Referencia los requisitos y criterios por su ID, sin copiar toda la spec.
- Si una decisión cambia el comportamiento o alcance, vuelve a la spec y solicita
  confirmación. No resuelvas una duda de producto mediante una suposición técnica.
- Conserva estos comentarios. No implementes durante la planificación.
- Solicita aprobación antes de marcar el plan como Aprobado. La autorización
  para implementar debe ser explícita; no se deduce del estado de los documentos.
-->

## Contexto técnico verificado

<!-- Qué existe hoy y cómo participa en la funcionalidad. -->

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| `MainActivity` | `app/src/main/java/com/rocioplata/familyfinaiapp/MainActivity.kt` | Activity con `enableEdgeToEdge()` y un `Scaffold` con el saludo de ejemplo; se reemplaza su contenido. |
| Tema | `app/src/main/java/com/rocioplata/familyfinaiapp/ui/theme/{Color,Type,Theme}.kt` | Paleta y tema Material 3 ya definidos (`Primary`, `AppBackground`, etc.); se reutilizan sin valores hexadecimales en pantallas. |
| Manifest | `app/src/main/AndroidManifest.xml` | Declara `MainActivity` sin orientación fija. |
| Cadenas | `app/src/main/res/values/strings.xml` | Solo `app_name`; recibe los textos nuevos (RF-12). |
| Dependencias | `gradle/libs.versions.toml`, `app/build.gradle.kts` | Compose BOM `2026.02.01`, Material 3, activity-compose; hoy no incluye librería de navegación ni `material-icons-extended`; este último se añadirá (confirmado, ver "Dependencias y configuración"). `minSdk` 34, `targetSdk` 36. |
| Tests | `app/src/test/.../ExampleUnitTest.kt`, `app/src/androidTest/.../ExampleInstrumentedTest.kt` | Solo plantillas; hay dependencias de test Compose (`ui-test-junit4`, `ui-test-manifest`). |

**Convenciones y patrón de referencia:** `AGENTS.md`: paquetes bajo `com.rocioplata.familyfinaiapp`, colores y tipografía en `ui/theme/`, pantalla con entrada con estado y contenido stateless con callbacks, sin lógica en composables. No hay otra funcionalidad previa que sirva de patrón.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de datos y eventos hasta la interfaz. Usa un diagrama si aporta claridad. -->

**Propuesta.** Un único módulo (`app/`), sin capas de dominio ni datos: no hay datos ni reglas (SPEC: "Datos y reglas de negocio").

- Un contenedor raíz con `Scaffold` (Material 3) que aloja: cabecera común (RF-01, RF-02), barra de navegación inferior con 5 destinos (RF-05), FAB solo en Inicio (RF-04) y el contenido de la pestaña activa (RF-03, RF-06).
- Pestaña seleccionada como estado en memoria (no persistente). Inicio por defecto (RF-05, RF-08).
- Atrás: manejador activo solo cuando la pestaña no es Inicio, que selecciona Inicio; en Inicio no se intercepta y el sistema cierra la app (RF-07, RF-13).
- Datos de cabecera: constantes de ejemplo estáticas (RF-01), sin ViewModel ni repositorio.

Flujo de eventos: toque en pestaña → cambia el estado seleccionado → se recompone el contenido; Atrás → mismo estado hacia Inicio.

**Decisiones confirmadas:** navegación por estado simple en Compose, sin dependencia nueva (se confirmó con la persona).

## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

| Módulo | Existe / nuevo | Responsabilidad y cambios | Dependencias afectadas |
| --- | --- | --- | --- |
No aplica: el proyecto tiene un único módulo `app/` y no se modulariza.

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son propuestas.
Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `MainActivity.kt` | Modificar | Mostrar el contenedor raíz en lugar del saludo y quitar `Greeting` y su preview. | RF-01, RF-05 |
| `ui/theme/*` | Reutilizar | Colores y tipografía existentes; añadir solo los colores faltantes si el snapshot los requiere. | RF-01, RF-10 |
| Contenedor raíz y destinos (propuesta: paquete `ui/home` o `ui/main` bajo `com.rocioplata.familyfinaiapp`; rutas por definir en TASKS) | Crear | `Scaffold` con cabecera, barra inferior, FAB solo en Inicio y placeholders de las 4 pestañas restantes. | RF-01 a RF-07, RF-11 |
| `res/values/strings.xml` | Modificar | Textos de pestañas, cabecera y descripciones accesibles, español por defecto. | RF-10, RF-12 |
| `AndroidManifest.xml` | Modificar | Fijar orientación vertical en `MainActivity`. | RF-09 |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modificar | Añadir `material-icons-extended`. | RF-01, RF-05 |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Modelos y contratos de entrada y salida:** No aplica: no se usa `docs/backend/api-contract.md` (SPEC: fuera de alcance).
- **Identificadores, relaciones y restricciones:** No aplica.
- **Origen de los datos mostrados y transformaciones:** constantes estáticas de ejemplo para la cabecera (SPEC: "Datos y reglas de negocio").
- **Persistencia, consultas y actualizaciones:** No aplica: sin almacenamiento (SPEC).
- **Convivencia entre datos locales y remotos:** No aplica.
- **Compatibilidad y migraciones de datos existentes:** No aplica: la app no guarda datos.

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de MOBILE_GUIDELINES.md. -->

- **Gestión del estado de interfaz y navegación:** propuesta: estado de la pestaña seleccionada en el contenedor raíz; sin ViewModel ni `StateFlow`, porque no hay estado observable de negocio. Atrás según RF-07.
- **Conservación y restauración del estado:** propuesta: estado en memoria que sobrevive a segundo plano y a volver desde otra app (la Activity sigue viva), y se pierde al recrearse la Activity o terminarse el proceso, lo que cumple RF-08. No se usa estado guardado en la instancia. Consecuencia: cualquier recreación (p. ej. cambio de tema o idioma) vuelve a Inicio, como pide RF-08.
- **Ejecución, concurrencia y cancelación de operaciones:** No aplica: no hay operaciones.
- **Errores, reintentos y prevención de duplicados:** seleccionar la pestaña ya activa no cambia el estado; el FAB no tiene acción (RF-13).
- **Otras consideraciones mobile aplicables y su solución:** orientación vertical en el manifest (RF-09); respeto de `WindowInsets` en cabecera y barra con edge-to-edge ya activo (RF-11); `contentDescription` y áreas táctiles mínimas de 48dp de Material 3 (RF-10); textos en recursos de strings (RF-12); gesto Atrás predictivo gestionado por el manejador de Atrás de Compose (RF-13).

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

- Añadir `androidx.compose.material:material-icons-extended` al catálogo de versiones y a `app/build.gradle.kts`, versionado por el Compose BOM. Motivo (confirmado): iconos de Material para Movimientos, Reportes, Presupuestos, Asistente y Notificaciones, que no están en el set básico. Coste: librería grande; se acepta según la decisión de la persona. Con minificación desactivada en release (`app/build.gradle.kts`) no se recortan los iconos no usados; pendiente de vigilar el tamaño del APK.
- Sin permisos nuevos (SPEC: fuera de alcance).
- Manifest: `android:screenOrientation="portrait"` en `MainActivity`.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, UI o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Test de UI Compose nuevo (propuesto): abre el contenedor raíz y comprueba Inicio seleccionada, cabecera, placeholder, FAB y 5 pestañas. | Emulador o dispositivo (`connectedDebugAndroidTest`) | Resultado del test |
| CA-02 | Test de UI nuevo: toca selector, notificaciones, avatar y FAB y comprueba que la pantalla no cambia. | Ídem | Resultado del test |
| CA-03 | Test de UI nuevo: toca cada pestaña y comprueba nombre, cabecera y ausencia de FAB. | Ídem | Resultado del test |
| CA-04 | Test de UI nuevo: Atrás desde otra pestaña lleva a Inicio. Salir de la app desde Inicio, manual. | Ídem; manual para el cierre | Resultado del test y nota manual |
| CA-05 | Manual: seleccionar otra pestaña, recrear (p. ej. cambiar tema) y terminar el proceso. Opcional: test con recreación de Activity. | Emulador | Nota de la prueba manual |
| CA-06 | Manual: girar el dispositivo con rotación automática activada. Se puede apoyar en inspeccionar el manifest. | Emulador | Nota de la prueba manual |
| CA-07 | Test de UI nuevo: comprueba descripciones de contenido de cabecera, FAB y pestañas; manual con TalkBack. | Emulador | Resultado del test y nota manual |
| CA-08 | Manual en 2 emuladores (teléfono pequeño y grande), con navegación por gestos y por 3 botones. | Emuladores AVD | Capturas y nota manual |
| CA-09 | Test de UI nuevo: toques repetidos en pestaña y FAB; manual con el gesto Atrás. | Emulador | Resultado del test y nota manual |

**Comprobaciones de regresión:** no hay comportamiento previo que conservar (SPEC: "Situación actual"). Comprobar que la compilación y los tests existentes (`ExampleUnitTest`, `ExampleInstrumentedTest`) siguen pasando.

**Comandos verificados para compilar y ejecutar tests:** los de `AGENTS.md`, sin ejecutar aún: `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug` y `./gradlew :app:connectedDebugAndroidTest`.

**Pruebas en dispositivo, emulador o simulador:** CA-05, CA-06, CA-07 y CA-08 requieren emulador (2 tamaños de teléfono, gestos y 3 botones, TalkBack). No hace falta hardware real.

**Limitaciones del entorno:** pendiente de comprobar si hay un emulador o dispositivo disponible en esta máquina. Si no lo hay, los CA que lo requieren quedarán sin verificar y se registrarán como tales.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. Dependencia y recursos: añadir `material-icons-extended`, cadenas y orientación en el manifest. Comprobación: `./gradlew :app:assembleDebug`.
2. Contenedor raíz con cabecera, barra inferior, FAB y placeholders, conectado en `MainActivity` (RF-01 a RF-07, RF-11). Depende de 1. Comprobación: compilación y vista previa.
3. Atrás, accesibilidad y pulsaciones repetidas (RF-07, RF-10, RF-13). Depende de 2. Comprobación: tests de UI.
4. Validación completa: tests, lint y pruebas manuales en emulador (CA-01 a CA-09). Depende de 3.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:** (a) peso de `material-icons-extended`: aceptado por la persona; se vigila el tamaño del APK. (b) La versión instalada de `activity-compose` (`1.8.0` en el catálogo) debe soportar el manejador de Atrás predictivo; se comprobará al implementar. (c) Que RF-08 reinicie a Inicio en cualquier recreación (p. ej. cambio de tema) es comportamiento aprobado en la spec, no un defecto. (d) La disponibilidad de emulador condiciona CA-05 a CA-09.
- **Decisiones pendientes:** Ninguna.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
