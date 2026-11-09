# TASKS: Identidad visual de la app (icono, nombre y logo)

**SPEC:** `docs/features/004-brand-identity/SPEC.md` (Aprobada)
**PLAN:** `docs/features/004-brand-identity/PLAN.md` (Aprobado)

<!-- Tareas de máx. 20-30 min, en orden de dependencia. Marcar [x] solo tras
ejecutar la validación y registrar en "Evidencia" el resultado real. Si una
tarea queda bloqueada, anotar el motivo en "Evidencia". -->

## Nombre

- [ ] **T-01 · Nombre mostrado "FamilyFin AI"**
  - **Cubre:** RF-03 (CA-04) · **Depende de:** —
  - **Alcance:** `res/values/strings.xml`: `app_name` pasa de "FamilyFinAIApp" a "FamilyFin AI". El manifiesto no cambia (ya usa `@string/app_name` en la aplicación y en `MainActivity`).
  - **Validación:** `./gradlew :app:assembleDebug` y `aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk` (etiqueta de la aplicación y de la actividad).
  - **Hecho cuando:** compila y el APK declara "FamilyFin AI" como etiqueta de la aplicación y del lanzador.
  - **Evidencia:** —

## Logo de la pantalla de acceso

- [ ] **T-02 · Logo claro en la pantalla de acceso**
  - **Cubre:** RF-04 (CA-05, CA-07 parcial) · **Depende de:** —
  - **Alcance:** `res/drawable/ic_brand_logo.xml`: conversión a mano de `docs/ui/snapshots/familyfin-ai-icon.svg` según las reglas del plan (tarjeta con `rx` 112 como `path` con arcos, degradado de fondo, borde blanco al 80 %, tejado, tres barras, base y dos chispas con degradados en coordenadas absolutas; sin la sombra; aplicando el `translate(0 4)` a las coordenadas; viewport 512; cabecera con el SVG de origen). `ui/auth/AuthContent.kt` (`AuthHeader`): `Image(R.drawable.ic_brand_logo)` de 88 dp en lugar del `Box` con fondo propio y el robot; retirar los imports sin uso. El resto de la pantalla no cambia.
  - **Validación:** `./gradlew :app:assembleDebug`; `connectedDebugAndroidTest` con `AuthScreenTest` y `AppRootTest` (regresión); captura de la pantalla de acceso en el emulador comparada con el render del SVG (`qlmanage -t -s 1024`).
  - **Hecho cuando:** compila, los tests de acceso y raíz pasan sin cambios y la captura muestra el logo claro sin diferencias visibles salvo la sombra omitida.
  - **Evidencia:** —

- [ ] **T-03 · Logo oscuro y su preview**
  - **Cubre:** RF-04 (CA-05) · **Depende de:** T-02
  - **Alcance:** `res/drawable-night/ic_brand_logo.xml`: conversión de `familyfin-ai-icon-dark.svg` (tarjeta, borde teal, halo y degradado de fondo, tejado como trazo de extremos redondeados con degradado, barras con reflejo, base, chispas con su halo radial; sin filtros). En `AuthContent.kt`, una preview con `uiMode` nocturno.
  - **Validación:** `./gradlew :app:assembleDebug`; en el emulador, `cmd uimode night yes` y captura de la pantalla de acceso comparada con el render del SVG oscuro; escribir texto, cambiar de tema con la pantalla abierta y comprobar que lo escrito se conserva.
  - **Hecho cuando:** compila, la captura nocturna muestra el logo oscuro, el claro vuelve con `night no` y el texto escrito se conserva al cambiar de tema.
  - **Evidencia:** —

## Icono del lanzador

- [ ] **T-04 · Fondo y primer plano del icono adaptativo**
  - **Cubre:** RF-01 (CA-01, CA-02) · **Depende de:** —
  - **Alcance:** `res/drawable/ic_launcher_background.xml`: degradado menta (`#E8F7F4` → `#D4ECE6`) sobre los 108 dp, sin esquinas ni borde. `res/drawable/ic_launcher_foreground.xml`: la marca (tejado, barras, base, chispas, sin fondo ni sombra) dentro de un `group` con escala 0,165 y traslación (11,7; 12,75), que lleva el centro (256,5; 250) al (54; 54); viewport 108 con coordenadas de 512 dentro del grupo. Sin tocar aún la capa `monochrome` de los XML de `mipmap-anydpi`.
  - **Validación:** `./gradlew :app:assembleDebug`; instalar en el emulador y capturar el cajón de apps del lanzador.
  - **Hecho cuando:** compila y el lanzador muestra el icono de FamilyFin AI, completo y sin el robot ni la cuadrícula.
  - **Evidencia:** —

- [ ] **T-05 · Capa monocromática (icono temático)**
  - **Cubre:** RF-02 (CA-03) · **Depende de:** T-04
  - **Alcance:** `res/drawable/ic_launcher_monochrome.xml`: siluetas del tejado, las barras, la base (alfa atenuado) y las chispas (la pequeña atenuada), en un solo color y sin degradados, con la misma escala y posición que el primer plano. `res/mipmap-anydpi/ic_launcher.xml` e `ic_launcher_round.xml`: la capa `monochrome` apunta al drawable nuevo.
  - **Validación:** `./gradlew :app:assembleDebug`; `aapt2 dump xmltree` de `ic_launcher.xml` e `ic_launcher_round.xml` (las tres capas); en el emulador, `settings put secure themed_icon_enabled 1` y captura del lanzador.
  - **Hecho cuando:** compila, ambos XML declaran las tres capas propias y la captura muestra el icono temático reconocible, o queda registrado que el AVD no lo tiñe.
  - **Evidencia:** —

## Limpieza

- [ ] **T-06 · Retirar los recursos de la plantilla**
  - **Cubre:** RF-05 (CA-07) · **Depende de:** T-02, T-04, T-05
  - **Alcance:** eliminar los 10 `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp` e `ic_launcher_round.webp`; comprobar que no queda ninguna referencia a los recursos de la plantilla (robot, cuadrícula, `#3DDC84`) y que ningún código de pantalla usa `ic_launcher_foreground`.
  - **Validación:** `./gradlew :app:assembleDebug :app:lintDebug`; `grep -rn "3DDC84" app/src`; `find app/src/main/res -name "*.webp"`; `grep -rn "ic_launcher_foreground" app/src/main/java`; instalación nueva en el emulador y captura del lanzador.
  - **Hecho cuando:** compila, las búsquedas no devuelven restos, el lint no añade errores y el icono se sigue viendo igual sin los `.webp` (si no fuera así, se restituyen y se documenta).
  - **Evidencia:** —

## Tests automáticos

- [ ] **T-07 · Test de etiqueta, icono adaptativo y capa monocromática**
  - **Cubre:** RF-01, RF-02, RF-03 (CA-01, CA-03, CA-04) · **Depende de:** T-01, T-05
  - **Alcance:** `app/src/androidTest/java/com/rocioplata/familyfinaiapp/BrandIdentityTest.kt`: `getApplicationLabel` y la etiqueta de `MainActivity` valen "FamilyFin AI"; `getApplicationIcon` es un `AdaptiveIconDrawable` con fondo y primer plano; `monochrome` no es nulo y su render tiene área visible.
  - **Validación:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rocioplata.familyfinaiapp.BrandIdentityTest` en el emulador.
  - **Hecho cuando:** los tests pasan (leídos del XML de resultados, sin omitidos).
  - **Evidencia:** —

- [ ] **T-08 · Test de zona segura y máscaras**
  - **Cubre:** RF-01, RF-02 (CA-02, CA-03) · **Depende de:** T-04, T-05
  - **Alcance:** en `BrandIdentityTest`: dibujar la capa de primer plano de `ic_launcher` y de `ic_launcher_round` a 4 px/dp y comprobar que todo píxel no transparente está a ≤ 33 dp del centro; lo mismo para la capa monocromática; aplicar máscaras circular, cuadrada redondeada y de gota y guardar los renders en el almacenamiento de la app para revisarlos con `adb pull`.
  - **Validación:** misma clase de la tarea anterior, con `connectedDebugAndroidTest`; revisar los renders descargados.
  - **Hecho cuando:** los tests pasan y los renders muestran la marca completa con las tres máscaras (si la escala 0,165 hiciera falta ajustarla, se corrige en T-04 y se documenta).
  - **Evidencia:** —

- [ ] **T-09 · Test de las variantes del logo**
  - **Cubre:** RF-04 (CA-05) · **Depende de:** T-02, T-03
  - **Alcance:** en `BrandIdentityTest`: resolver `R.drawable.ic_brand_logo` con configuración diurna y nocturna (`createConfigurationContext`), dibujar ambas y comprobar que son distintas (luminosidad del fondo: claro > oscuro) y que ninguna es transparente.
  - **Validación:** misma clase, con `connectedDebugAndroidTest`.
  - **Hecho cuando:** los tests pasan.
  - **Evidencia:** —

## Validación

- [ ] **T-10 · Comprobaciones automáticas completas**
  - **Cubre:** todos (CA-08) · **Depende de:** T-01 a T-09
  - **Alcance:** suite completa, incluida la regresión de la 003 y la 002 (`AuthScreenTest`, `AppRootTest`, `MainScreenTest`, `MainScreenBehaviorTest`, `SessionStoreTest`, los 153 tests JVM). Revisar los avisos nuevos del lint (iconos, capa monocromática) y el tamaño del APK.
  - **Validación:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` y `./gradlew :app:connectedDebugAndroidTest`.
  - **Hecho cuando:** todo pasa sin modificar lo que comprueban los tests existentes, o cada fallo queda registrado con su causa.
  - **Evidencia:** —

- [ ] **T-11 · Pruebas manuales en el emulador**
  - **Cubre:** CA-01 a CA-07 (parte manual) · **Depende de:** T-10
  - **Alcance:** instalación nueva y actualización sobre la anterior (caché de iconos del lanzador); icono en el cajón de apps; nombre en el lanzador, en Ajustes > Apps y en recientes; iconos temáticos; pantalla de acceso con tema claro y oscuro, incluido el cambio de tema con texto escrito; arranque en frío con capturas en ráfaga (`screencap` en bucle) o `screenrecord`; recorrido de las cuatro superficies del CA-07 sin ver el robot.
  - **Validación:** pasos manuales registrados con resultado observado y capturas.
  - **Hecho cuando:** cada escenario tiene resultado o motivo de bloqueo (iconos temáticos y arranque pueden quedar sin verificar por límites del AVD, y así se anota).
  - **Evidencia:** —

- [ ] **T-12 · Documentación y revisión final**
  - **Cubre:** CA-01 a CA-08 · **Depende de:** T-11
  - **Alcance:** `docs/ROADMAP.md` (paso 4 en "Hecha"); tabla de cada criterio con su evidencia y diferencias visibles frente a los SVG (sombra omitida, halo aproximado); nota en la T-17 de `docs/features/003-auth/TASKS.md` que indique que el logo provisional se sustituyó en esta funcionalidad; confirmar que no se implementó nada fuera de alcance (sin pantalla de arranque propia, ni material de tienda, ni cambio de `applicationId`).
  - **Validación:** revisión documental.
  - **Hecho cuando:** cada criterio tiene evidencia o está marcado como no verificado, y SPEC, PLAN y TASKS son coherentes.
  - **Evidencia:** —
