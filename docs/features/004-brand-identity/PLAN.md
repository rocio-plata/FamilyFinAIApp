# PLAN: Identidad visual de la app (icono, nombre y logo)

**SPEC de referencia:** `docs/features/004-brand-identity/SPEC.md`
**Versión de la spec revisada:** SPEC aprobada el 2026-11-08, con RF-01 a RF-05 y CA-01 a CA-08 (sin commit propio al redactar este plan).
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

Rutas relativas a `app/src/main/` salvo indicación.

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| Manifest | `AndroidManifest.xml` (l. 13-15 y 21) | Declara `@mipmap/ic_launcher`, `@mipmap/ic_launcher_round` y `@string/app_name` como etiqueta de la aplicación y de `MainActivity`. No cambia: el trabajo está en los recursos que referencia. |
| Iconos adaptativos | `res/mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml` | `adaptive-icon` con capas de fondo, primer plano y `monochrome`; esta última apunta hoy al mismo drawable del robot. Se modifican para usar una capa monocromática propia. |
| Fondo y primer plano de la plantilla | `res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` | Vectores de 108 dp de la plantilla: fondo verde `#3DDC84` con cuadrícula y robot de Android. Se reescriben. |
| Iconos heredados | `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp` y `ic_launcher_round.webp` (10 archivos, 48 KB) | Versión por densidad de la plantilla. Con `minSdk 34` el recurso `anydpi` se impone, por lo que no se usan; se confirma al retirarlos (CA-07). |
| Nombre | `res/values/strings.xml` (`app_name`) | Vale "FamilyFinAIApp". Solo lo usa el manifiesto: ningún código ni test depende de su valor. |
| Logo de acceso | `java/.../ui/auth/AuthContent.kt` (`AuthHeader`) | `Box` de 88 dp con fondo `primaryContainer` y `Image(R.drawable.ic_launcher_foreground)`. Es la única referencia en código a los recursos del lanzador. |
| Tema | `java/.../ui/theme/Theme.kt` | `FamilyFinAIAppTheme(darkTheme = isSystemInDarkTheme())`: el tema oscuro sigue el ajuste del sistema, no hay selector propio. |
| Tests de acceso y raíz | `app/src/androidTest/.../ui/auth/AuthScreenTest.kt`, `ui/root/AppRootTest.kt` | Comprueban textos ("FamilyFin AI", lema) y no el logo; son la regresión de RF-04 (CA-08). |
| Diseños fuente | `docs/ui/snapshots/familyfin-ai-icon.svg`, `familyfin-ai-icon-dark.svg` | 512 × 512. El claro lleva un `filter` de sombra y todo su dibujo dentro de `translate(0 4)`; el oscuro no usa filtros. Medidos: la marca (tejado, barras, base y chispas) ocupa x 111-402 e y 100-400, centrada en (256,5; 250), y su punto más lejano del centro está a ≈192 unidades. |
| Entorno de validación | AVD `Medium_Phone_API_37.0` (API 37, Pixel Launcher) | Hay `screencap` y `screenrecord`; `cmd uimode night` cambia el tema. La clave `themed_icon_enabled` existe sin valor (que el lanzador la respete se comprobará al implementar). No hay overlays de forma de icono ni `ffmpeg`. |
| Herramientas | `build-tools` 36.0.0 (`aapt2`) | Permite volcar etiqueta y recursos del APK. No hay dependencias ni plugins que añadir. |

**Convenciones y patrón de referencia:** recursos en `res/` con nombres en
minúsculas y guiones bajos, como los existentes; textos en `strings.xml`; colores
reutilizables en `ui/theme` (los del icono forman parte de sus recursos, según la
restricción de la spec). Patrón de referencia: la pantalla de acceso de la 003
(`AuthContent` sin estado, previews con `@Preview`) y sus tests de UI.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de datos y eventos hasta la interfaz. Usa un diagrama si aporta claridad. -->

**Enfoque.** La identidad es un conjunto de recursos estáticos: no hay datos,
red ni estado. Los dos SVG de `docs/ui/snapshots` son la fuente de verdad (decisión
de la spec) y se convierten **a mano** a `VectorDrawable`, sin dependencias ni pasos
de generación en el build. Cada recurso lleva en su cabecera el SVG del que sale.

```
familyfin-ai-icon.svg ───────────► res/drawable/ic_brand_logo.xml          (claro)
familyfin-ai-icon-dark.svg ──────► res/drawable-night/ic_brand_logo.xml    (oscuro)
        │                                   ▲
        │                                   └─ AuthContent: Image(ic_brand_logo, 88 dp)
        └─ marca (tejado, barras, base, chispas) + fondo menta
             ├─► res/drawable/ic_launcher_background.xml   fondo menta a pantalla completa
             ├─► res/drawable/ic_launcher_foreground.xml   marca a escala 0,165, centrada
             └─► res/drawable/ic_launcher_monochrome.xml   siluetas de un color (alfa)
                      ▲
   res/mipmap-anydpi/ic_launcher.xml e ic_launcher_round.xml  (las tres capas)
   res/values/strings.xml: app_name = "FamilyFin AI"  ──► etiqueta del lanzador y de recientes
```

**Decisiones técnicas** (propuestas; ver *Riesgos y decisiones pendientes*):

1. **Logo por calificador `-night` (RF-04).** `ic_brand_logo` existe en `drawable/`
   y en `drawable-night/`, y `painterResource` toma el que corresponde. El
   calificador sigue el mismo ajuste del sistema que `isSystemInDarkTheme()` en
   `Theme.kt`, así que ambos coinciden y no hace falta código de selección.
   Descartado: elegir el recurso en Compose con `isSystemInDarkTheme()`; añade una
   rama y dos identificadores sin ventaja mientras no exista un selector de tema
   propio.
2. **Icono adaptativo (RF-01).** Fondo: degradado menta (`#E8F7F4` → `#D4ECE6`) que
   cubre los 108 dp, sin esquinas ni borde (la máscara la pone el lanzador).
   Primer plano: la marca dentro de un `group` con escala 0,165 y traslación que
   lleva su centro (256,5; 250) al centro (54; 54). El punto más lejano queda a
   ≈31,7 dp del centro, dentro de la zona segura de 66 dp de diámetro (radio 33 dp).
   Con la escala completa (0,211) saldría a ≈40 dp y se recortaría.
3. **Icono temático (RF-02).** Capa `monochrome` con las siluetas del tejado, las
   barras, la base y las chispas, en un solo color y con alfa (base y chispa pequeña
   atenuadas) para conservar la jerarquía. Sin degradados: el sistema solo usa el
   canal alfa. Misma escala y posición que el primer plano.
4. **Icono redondo.** `ic_launcher_round.xml` reutiliza las mismas tres capas: el
   lanzador aplica la máscara circular.
5. **Nombre (RF-03).** `app_name` pasa a "FamilyFin AI". Es una cadena, sin
   traducción.
6. **Limpieza (RF-05).** Se eliminan los 10 `.webp` heredados y la referencia de
   `AuthContent` a `ic_launcher_foreground`. Los fondos y primeros planos de la
   plantilla se sobrescriben, no conviven.

**Reglas de conversión SVG → `VectorDrawable`** (documentadas porque son las
adaptaciones que permite la spec):

| SVG | VectorDrawable |
| --- | --- |
| `rect` con `rx` | `path` con arcos (`A`), sin `rect` |
| `linearGradient` en `objectBoundingBox` (barras) | `gradient` lineal con coordenadas absolutas del viewport, vía `aapt:attr` |
| `radialGradient` (halos del oscuro) | `gradient` radial con `centerX`, `centerY` y `gradientRadius` |
| `stroke` con extremos y uniones redondeados (tejado oscuro) | `strokeLineCap` y `strokeLineJoin` en `round`, con `strokeColor` en degradado |
| `opacity` y `stop-opacity` | color `#AARRGGBB` o `fillAlpha`/`strokeAlpha` |
| `filter` (sombra suave) | se omite (decisión de la spec); el halo del oscuro ya está hecho con degradados |
| `translate(0 4)` del SVG claro | se aplica con un `group` con `translateY="4"` (equivale a sumarlo a cada coordenada y es más fiel al SVG; ver T-02) |


## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

| Módulo | Existe / nuevo | Responsabilidad y cambios | Dependencias afectadas |
| --- | --- | --- | --- |
| No aplica: único módulo `:app`; los cambios son de recursos y de un composable | Existente | Recursos de icono, logo y nombre; un cambio en `AuthContent`. | Ninguna. |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son propuestas.
Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `res/values/strings.xml` | Modificar | `app_name` = "FamilyFin AI". | RF-03 |
| `AndroidManifest.xml` | Reutilizar | Sin cambios: ya apunta a `ic_launcher`, `ic_launcher_round` y `app_name`. | RF-01, RF-03 |
| `res/drawable/ic_launcher_background.xml` | Modificar | Degradado menta a pantalla completa (108 dp). | RF-01 |
| `res/drawable/ic_launcher_foreground.xml` | Modificar | La marca a color, escala 0,165, centrada en la zona segura. | RF-01 |
| `res/drawable/ic_launcher_monochrome.xml` | Crear | Siluetas de un color con alfa. | RF-02 |
| `res/mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml` | Modificar | La capa `monochrome` apunta al drawable nuevo. | RF-02 |
| `res/mipmap-*dpi/ic_launcher*.webp` (10) | Eliminar | Recursos heredados de la plantilla sin uso. | RF-05 |
| `res/drawable/ic_brand_logo.xml` | Crear | Logo claro: tarjeta redondeada, borde y marca (viewport 512). | RF-04 |
| `res/drawable-night/ic_brand_logo.xml` | Crear | Logo oscuro: tarjeta, borde, halo y marca (viewport 512). | RF-04 |
| `java/.../ui/auth/AuthContent.kt` | Modificar | `AuthHeader` usa `Image(R.drawable.ic_brand_logo)` de 88 dp, sin el `Box` con fondo propio; se retiran los imports sin uso y se añade una preview con `uiMode` nocturno. El resto de la pantalla no cambia. | RF-04 |
| `app/src/androidTest/.../BrandIdentityTest.kt` | Crear | Comprobaciones automáticas de etiqueta, icono adaptativo, zona segura, capa monocromática y variantes del logo. | RF-01 a RF-05 |
| `docs/ROADMAP.md`, `docs/features/004-brand-identity/TASKS.md` | Modificar / crear | Estado y evidencias. | Documentación |


## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Modelos y contratos de entrada y salida:** No aplica: no hay modelos, contratos ni backend; son recursos estáticos.
- **Identificadores, relaciones y restricciones:** nuevo identificador de recurso `R.drawable.ic_brand_logo` (con variante `night`); se reutilizan `ic_launcher_background` y `ic_launcher_foreground` (contenido nuevo) y se añade `ic_launcher_monochrome`. `applicationId`, `namespace` y permisos no cambian.
- **Origen de los datos mostrados y transformaciones:** el dibujo sale de los dos SVG por las reglas de conversión de *Solución propuesta*. Único texto nuevo: `app_name`.
- **Persistencia, consultas y actualizaciones:** No aplica: nada se guarda ni se consulta.
- **Convivencia entre datos locales y remotos:** No aplica.
- **Compatibilidad y migraciones de datos existentes:** instalar sobre una versión anterior (mismo `applicationId`) conserva sesión y datos; solo cambian icono y nombre. El lanzador puede tardar en refrescar su caché de iconos: se valida con una instalación nueva y con una actualización.

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de MOBILE_GUIDELINES.md. -->

- **Gestión del estado de interfaz y navegación:** sin estado nuevo. El logo depende del tema del sistema (RF-04) y se resuelve por recurso. La navegación no cambia.
- **Conservación y restauración del estado:** al cambiar entre tema claro y oscuro el sistema recrea la `Activity` (no se declara `uiMode` en `configChanges`, igual que hoy). Lo escrito en la pantalla de acceso se conserva porque vive en `AuthViewModel` (CA-18 de la 003) y el logo se vuelve a resolver con la variante nueva (CA-05). Ver la interpretación de "sin reiniciar la pantalla" en *Riesgos*.
- **Ejecución, concurrencia y cancelación de operaciones:** No aplica: no hay operaciones. Los `VectorDrawable` se dibujan en el hilo de interfaz y son ligeros.
- **Errores, reintentos y prevención de duplicados:** No aplica en ejecución. Los errores posibles son de construcción (XML o gradiente mal formado, o un recurso que falte) y los detecta `assembleDebug` o los tests de la etapa 6.
- **Otras consideraciones mobile aplicables y su solución:** pantallas y adaptación
  visual: el primer plano cabe en la zona segura (RF-01, CA-02), medido y probado,
  y el logo mantiene los 88 dp actuales, sin cortarse con texto grande.
  Accesibilidad: el logo sigue siendo decorativo (`contentDescription = null`, el
  nombre va a su lado) y la contraseña y demás controles de la pantalla no cambian.
  Rendimiento y peso: se retiran 48 KB de `.webp` y se añaden unos pocos vectores.
  Privacidad: no aplica.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

- **Sin dependencias nuevas.** Los iconos adaptativos, la capa monocromática y los
  degradados en vectores son de la plataforma, y `minSdk 34` los soporta sin
  bibliotecas de compatibilidad. No se añade generación de recursos al build.
- **Configuración:** sin cambios en Gradle, el manifiesto ni los permisos.
- **Fuera de este plan:** una herramienta de conversión SVG → vector (Vector Asset
  Studio o un script); la conversión se hace a mano y se verifica visualmente.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, UI o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Instrumentado nuevo `BrandIdentityTest`: `getApplicationIcon` devuelve un `AdaptiveIconDrawable` con fondo y primer plano propios. Manual: captura del cajón de apps del lanzador tras instalar. | AVD API 37; APK de debug | Test en verde + captura sin robot ni cuadrícula |
| CA-02 | Instrumentado nuevo: dibuja la capa de primer plano de `ic_launcher` y de `ic_launcher_round` a 4 px/dp y comprueba que todo píxel no transparente está a ≤ 33 dp del centro; además aplica máscaras circular, cuadrada redondeada y de gota y guarda los renders para revisarlos (`adb pull`). | AVD (no hay overlays de forma de icono: las máscaras las aplica el propio test) | Test en verde + renders revisados |
| CA-03 | Instrumentado nuevo: `monochrome` no es nulo (API 33+), su render tiene área visible y cabe en la zona segura. Manual: activar iconos temáticos y fondo de pantalla en el emulador y capturar. | AVD con Pixel Launcher; si no tiñe el icono, queda como no verificado y se apoya en el render de la capa | Test + captura o nota de limitación |
| CA-04 | Instrumentado nuevo: `getApplicationLabel` y la etiqueta de `MainActivity` valen "FamilyFin AI". Manual: nombre en lanzador, Ajustes > Apps y recientes. | AVD | Test + capturas |
| CA-05 | Instrumentado nuevo: resuelve `ic_brand_logo` con configuración diurna y nocturna y comprueba que son distintos (luminosidad del fondo: claro > oscuro). Existente: `AuthScreenTest` sigue verde. Manual: capturas de la pantalla de acceso con `cmd uimode night no/yes`, y con texto escrito antes de cambiar de tema. | AVD, datos borrados | Test + capturas claro y oscuro + lo escrito conservado |
| CA-06 | Manual: arranque en frío con capturas de pantalla en ráfaga (`screencap` en bucle) o `screenrecord`. | AVD; el arranque dura menos de un segundo | Captura del arranque o nota de no verificado |
| CA-07 | Búsqueda en el repositorio (`3DDC84`, `*.webp` de `mipmap-*dpi`, referencias a `ic_launcher_foreground` en código de pantallas) y recorrido visual de lanzador, recientes, ajustes y acceso. | Repositorio y AVD | Salida de las búsquedas + capturas |
| CA-08 | Suite automática completa: `testDebugUnitTest` y `connectedDebugAndroidTest` sin modificar lo que comprueban los tests existentes. | AVD | Recuento de tests y 0 fallos |

**Comprobaciones de regresión:** tests existentes de la 003 y la 002
(`AuthScreenTest`, `AppRootTest`, `MainScreenTest`, `MainScreenBehaviorTest`,
`SessionStoreTest`) y los 153 tests JVM, sin cambios. Revisar en `lintDebug` los
avisos nuevos de iconos (por ejemplo, forma o capa monocromática) y el tamaño del APK.

**Comandos verificados para compilar y ejecutar tests:** (de `AGENTS.md`, ya usados
en la 003) `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`,
`./gradlew :app:lintDebug` y `./gradlew :app:connectedDebugAndroidTest` con el
emulador; para una clase,
`-Pandroid.testInstrumentationRunnerArguments.class=com.rocioplata.familyfinaiapp.BrandIdentityTest`.
Para el emulador: `adb shell cmd uimode night yes|no`, `adb shell settings put secure
themed_icon_enabled 1` y `adb exec-out screencap -p`.

**Pruebas en dispositivo, emulador o simulador:** AVD `Medium_Phone_API_37.0`: icono
en el cajón de apps, nombre en lanzador, ajustes y recientes, pantalla de acceso en
tema claro y oscuro (también cambiando de tema con la pantalla abierta), arranque en
frío e iconos temáticos. No hace falta hardware real, pero distintos fabricantes
aplican máscaras propias: solo se prueban las del test y las del lanzador del AVD.

**Limitaciones del entorno:** (1) el AVD no trae overlays de forma de icono, así
que CA-02 se apoya en un test que aplica las máscaras; (2) que el lanzador tiña el
icono en modo temático (CA-03) depende de la imagen del emulador y puede quedar sin
verificar; (3) la pantalla de arranque dura menos de un segundo y CA-06 puede
requerir varios intentos o quedar sin verificar; (4) no se prueban lanzadores de
terceros ni de otros fabricantes.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. **Nombre** (RF-03): `app_name`. Comprobación: `assembleDebug` y etiqueta del APK
   con `aapt2 dump badging`.
2. **Logo claro y oscuro** (RF-04, solo recursos): `ic_brand_logo` en `drawable/` y
   `drawable-night/`. Comprobación: `assembleDebug` y render comparado con los SVG.
3. **Logo en la pantalla de acceso** (RF-04): `AuthContent` y preview nocturna.
   Comprobación: tests de UI de acceso en verde y capturas en tema claro y oscuro.
4. **Icono adaptativo y temático** (RF-01, RF-02): fondo, primer plano, capa
   monocromática y los dos XML de `mipmap-anydpi`. Comprobación: `assembleDebug` y
   captura del cajón de apps.
5. **Limpieza** (RF-05): se retiran los `.webp` y las referencias a la plantilla.
   Comprobación: búsquedas del CA-07 y el icono sigue viéndose igual.
6. **Tests automáticos** (CA-01 a CA-05): `BrandIdentityTest`. Comprobación:
   `connectedDebugAndroidTest` en el emulador.
7. **Validación completa y documentación**: `assembleDebug`, `testDebugUnitTest`,
   `lintDebug` y `connectedDebugAndroidTest`; pruebas manuales; evidencias en
   `TASKS.md`; estado en `docs/ROADMAP.md`.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - **Marca recortada por la máscara:** si la escala 0,165 resultara demasiado
    grande o pequeña, se ajusta tras medirla; el test de zona segura lo detecta y la
    revisión de máscaras lo confirma visualmente.
  - **Conversión manual de degradados:** una coordenada mal calculada cambia el
    aspecto sin dar error de compilación. Medida: renderizar cada recurso en el
    emulador y compararlo con el render del SVG (`qlmanage`) antes de continuar.
  - **Fidelidad:** sin la sombra, y con el halo oscuro aproximado por degradados, el
    resultado no será idéntico a los SVG. Medida: documentar en `TASKS.md` cualquier
    diferencia visible.
  - **Retirada de los `.webp`:** se supone que `anydpi` se impone con `minSdk 34`. Se
    confirma al retirarlos con `assembleDebug` y el icono visible en el AVD; si no
    fuera así, se restituyen y se documenta.
  - **Caché de iconos del lanzador:** tras actualizar puede mostrarse el icono
    anterior. Se valida con una instalación nueva y con una actualización.
  - **Modo temático y arranque:** pueden quedar sin verificar por límites del AVD
    (ver *Limitaciones del entorno*); se registrará como tal, sin darlos por buenos.
- **Decisiones pendientes:** Ninguna bloqueante. Propuestas incluidas en este plan
  para tu revisión:
  1. Elegir el logo oscuro por el calificador de recursos `-night` y no en código
     (decisión técnica 1).
  2. Escala 0,165 del primer plano, que deja la marca a ≈31,7 dp del centro, dentro
     de la zona segura de 33 dp.
  3. Interpretación de RF-04 ("se recompone al cambiarlo, sin reiniciar la
     pantalla"): al cambiar de tema el sistema recrea la `Activity`, como con
     cualquier otra pantalla, y lo escrito se conserva. No se propone declarar
     `uiMode` en `configChanges` para evitar la recreación. Si la spec exigiera
     que no haya recreación alguna, habría que volver a ella.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
