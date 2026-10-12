# Instrucciones para agentes

## Alcance y prioridad

Estas instrucciones aplican a todo el repositorio. Las instrucciones más
específicas dentro de subdirectorios tienen prioridad para sus archivos. Las
indicaciones explícitas del usuario tienen prioridad sobre este documento.

Antes de planificar, revisar o cambiar código, leer íntegramente
`docs/GENERIC_RULES.md` y las reglas móviles relevantes en
`docs/MOBILE_GUIDELINES.md`, además de la especificación o plan asociado si
existe. No inventar requisitos; consultar cuando una decisión afecte el
comportamiento observable, el alcance o la persistencia.

## Proyecto

- Es una aplicación Android llamada `FamilyFinAIApp`.
- El módulo principal es `app/`.
- El código de producción está escrito en Kotlin y usa Jetpack Compose con
  Material 3.
- La configuración de dependencias se centraliza en
  `gradle/libs.versions.toml`; usar el Gradle Wrapper (`./gradlew`) en lugar de
  una instalación global de Gradle.
- Mantener el `namespace` y el `applicationId`
  `com.rocioplata.familyfinaiapp`, salvo que exista un requisito explícito para
  cambiarlos.
- Respetar los SDK y versiones configurados en los archivos Gradle. No
  actualizar dependencias o herramientas sin una necesidad concreta.
- No asumir que una librería o una arquitectura descrita en documentación
  externa ya está configurada: verificar primero el código y
  `gradle/libs.versions.toml`.

## Convenciones de implementación

- Seguir la estructura existente de paquetes bajo
  `app/src/main/java/com/rocioplata/familyfinaiapp/`.
- Preferir APIs de Compose, Material 3 y AndroidX ya configuradas antes de
  introducir nuevas dependencias.
- Mantener funciones pequeñas, estado explícito y tipos seguros. No ocultar
  errores con `catch` vacíos, valores ficticios o fallbacks silenciosos.
- Para nuevas pantallas o flujos, definir los estados de carga, contenido,
  vacío, error y éxito cuando correspondan.
- Considerar ciclo de vida, conservación de estado, conectividad, accesibilidad,
  tamaños de pantalla, permisos, privacidad y rendimiento según
  `docs/MOBILE_GUIDELINES.md`.
- No incluir secretos, credenciales ni datos personales innecesarios en código,
  logs, fixtures o documentación.
- Usar corrutinas estructuradas y `viewModelScope` cuando se incorporen
  ViewModels. No capturar ni ocultar `CancellationException`; si se captura
  una excepción amplia, relanzar la cancelación antes de manejar otros
  errores.

## Arquitectura y UI

- Mantener separadas las capas de presentación, dominio y datos cuando el
  tamaño de la funcionalidad lo justifique. El dominio no debe depender de
  Android, Compose, DTOs ni implementaciones de datos.
- Mantener el acceso a datos y la lógica de negocio fuera de los composables.
  Preferir una entrada de pantalla con estado y una función de contenido
  stateless que reciba estado y callbacks.
- Para pantallas con estado observable, usar `StateFlow` y
  `collectAsStateWithLifecycle()` cuando las dependencias configuradas lo
  permitan.
- Definir explícitamente los estados válidos de cada pantalla (por ejemplo,
  carga, contenido, vacío y error) y conservar el patrón establecido por la
  funcionalidad existente.
- Colocar colores y tipografías reutilizables en `app/src/main/.../ui/theme/`;
  evitar valores hexadecimales hardcodeados en las pantallas.
- Antes de agregar navegación, revisar la solución existente y no introducir
  dos stacks de navegación diferentes sin una decisión documentada.

## Documentación y especificaciones

- Leer las instrucciones incluidas en cada plantilla y preservar su estructura
  y comentarios al crear documentos derivados.
- Usar `docs/SPEC_TEMPLATE.md` para especificaciones funcionales y
  `docs/PLAN_TEMPLATE.md` para planes de trabajo.
- Mantener los documentos de cada funcionalidad juntos en
  `docs/features/<feature-name>/`: `SPEC.md`, `PLAN.md` y `TASKS.md`.
- Seguir esta secuencia: completar la especificación, esperar la aprobación
  explícita del usuario, crear el plan, esperar su aprobación, derivar las
  tareas y recién entonces implementar. El agente no cambia por sí solo el
  estado de aprobación.
- `TASKS.md` debe contener tareas pequeñas, ordenadas y verificables, con
  identificador, objetivo, alcance, dependencias, criterios de aceptación que
  resuelve y método de validación. Actualizar su estado durante el trabajo y
  registrar allí la evidencia de validación.
- Mantener requisitos, decisiones, criterios de aceptación y estado del trabajo
  consistentes con la implementación.
- Actualizar la documentación directamente relacionada cuando cambie el
  comportamiento o una decisión técnica.

## Validación

Ejecutar desde la raíz el conjunto más pequeño de comprobaciones que cubra el
cambio:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Para una prueba local concreta, usar:

```bash
./gradlew :app:testDebugUnitTest --tests "com.rocioplata.familyfinaiapp.SomeTest"
```

Para cambios que requieran pruebas instrumentadas, ejecutar
`./gradlew :app:connectedDebugAndroidTest` con un dispositivo o emulador
disponible.

No hay configuración separada de ktlint o detekt en el repositorio base; no
introducir una herramienta de calidad como cambio no relacionado. Para cambios
solo documentales, verificar el contenido y sus referencias sin exigir una
compilación Android.

Si cambia la configuración de variantes, inspeccionar las tareas Gradle
disponibles y usar la variante correspondiente.

## Informe de finalización

Resumir el comportamiento implementado, los documentos de funcionalidad
afectados y los resultados de validación. Vincular los criterios de aceptación
con la evidencia registrada en `TASKS.md` e identificar claramente cualquier
parte incompleta o no verificada.
Informar claramente qué comprobaciones se ejecutaron, cuáles fallaron y cuáles
no pudieron ejecutarse por limitaciones del entorno.

## Cambios y control de versiones

- Revisar el estado del repositorio antes de editar y preservar cambios ajenos.
- Mantener los cambios enfocados; no hacer refactors o actualizaciones
  no relacionados.
- No modificar ni eliminar archivos generados o cambios existentes sin
  autorización explícita.
- Usar formato Kotlin/Gradle consistente con los archivos existentes y evitar
  comentarios que solo repitan el código.
