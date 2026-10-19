# Reglas generales para agentes de programación

Reglas de trabajo reutilizables para planificar, revisar, implementar y
validar cambios de software. Aplícalas junto con el archivo `AGENTS.md`
del repositorio y cualquier instrucción específica aplicable. Las
convenciones propias del proyecto precisan estos valores
predeterminados; las instrucciones explícitas del usuario y las
instrucciones de mayor prioridad del agente siguen siendo la autoridad.

## Comprender antes de cambiar

-   Lee el código, las instrucciones y la documentación de
    funcionalidades pertinentes antes de proponer o implementar un
    cambio. Cuando sea necesario, rastrea quién llama al código y cuáles
    son sus dependencias.
-   Distingue entre hechos verificados, decisiones propuestas y
    preguntas sin resolver.
-   Nunca inventes requisitos, archivos del repositorio, API, versiones
    de dependencias, resultados de ejecución ni capacidades que no hayas
    verificado.
-   Usa el código y la documentación existentes para resolver los
    detalles técnicos habituales. Haz preguntas concretas cuando la
    falta de información afecte de forma significativa al
    comportamiento, los criterios de aceptación, el alcance o una
    decisión irreversible.
-   Si falta una referencia necesaria, indícalo. No inventes
    silenciosamente su contenido ni afirmes haberla leído.

## Mantener claro el alcance

-   Implementa el comportamiento solicitado y los cambios necesarios
    para hacerlo posible.
-   Respeta el alcance acordado y los criterios de aceptación. No añadas
    funcionalidades especulativas, refactorizaciones no relacionadas ni
    actualizaciones de dependencias que no se hayan solicitado.
-   Si aparece un requisito nuevo o una contradicción, aclara qué parte
    se ve afectada y actualiza los documentos pertinentes antes de
    implementar ese comportamiento.
-   Continúa el trabajo autorizado sin pedir permiso repetidamente.
    Respeta la etapa solicitada por el usuario, por ejemplo, solo
    revisión, solo planificación o implementación.

## Escribir código mantenible

-   Sigue la arquitectura, las convenciones de nombres, el formato y los
    patrones establecidos en el repositorio, después de comprobar que
    son aplicables al problema actual.
-   Prefiere la solución más sencilla que cumpla los requisitos. Evita
    abstracciones especulativas, configuraciones innecesarias y
    optimizaciones prematuras.
-   Mantén claras las responsabilidades y concéntrate en funciones con
    un propósito definido. Reutiliza el código existente cuando su
    comportamiento sea adecuado; no fuerces comportamientos no
    relacionados dentro de una abstracción compartida.
-   No ocultes errores mediante bloques `catch` vacíos, resultados de
    éxito inventados ni alternativas que incumplan el comportamiento
    especificado.
-   Comenta la intención, las restricciones y las decisiones que no sean
    evidentes. Evita comentarios que se limiten a repetir lo que hace el
    código.
-   Elimina el código muerto introducido por el cambio. Conserva el
    trabajo no relacionado del usuario.

## Usar herramientas y dependencias de forma deliberada

-   Da preferencia a los envoltorios (*wrappers*), scripts y archivos de
    configuración proporcionados por el repositorio.
-   Comprueba las versiones instaladas antes de utilizar API cuyo uso
    dependa de la versión. Consulta la documentación oficial cuando
    exista incertidumbre sobre la compatibilidad o el comportamiento.
-   Añade dependencias solo cuando sean necesarias para la solución
    solicitada. Explica su propósito y las ventajas e inconvenientes
    relevantes; evita incorporar bibliotecas duplicadas para la misma
    tarea.
-   Nunca modifiques pruebas, verificaciones ni configuraciones con el
    único fin de ocultar un fallo. Corrige la causa o informa del fallo
    preexistente aportando evidencias.

## Proteger los datos y el trabajo existente

-   Revisa los cambios existentes que sean pertinentes antes de editar.
    No sobrescribas, reviertas ni elimines trabajo no relacionado.
-   No incluyas secretos, credenciales, tokens ni datos personales
    innecesarios en el código fuente, los registros, los datos de
    prueba, la documentación o los informes de finalización.
-   Utiliza el mecanismo de configuración establecido por el proyecto
    para los valores sensibles.
-   No realices operaciones destructivas, publiques cambios ni
    modifiques sistemas externos, salvo que la tarea o las instrucciones
    del usuario autoricen esa acción.

## Validar el comportamiento

-   Elige un método de validación adecuado al cambio real y a sus
    criterios de aceptación.
-   Añade o actualiza pruebas significativas para los comportamientos
    modificados, las correcciones de errores y los casos límite
    pertinentes. Da preferencia a las pruebas del comportamiento
    observable frente a las que reproducen la implementación.
-   Los cambios que solo afecten a la documentación, o que tengan un
    impacto igualmente bajo, no requieren pruebas artificiales. Cumple
    los requisitos de validación aplicables del repositorio.
-   Ejecuta las comprobaciones pertinentes. Distingue entre las que se
    superaron, fallaron, se omitieron o quedaron bloqueadas por el
    entorno.
-   El nombre de una prueba o un comando que no se haya ejecutado no
    constituyen evidencia. Registra la ejecución y sus resultados. Usa
    capturas de pantalla para respaldar afirmaciones visuales y pasos
    manuales reproducibles para comportamientos que requieran
    interacción directa.
-   No des por hecho que una captura de pantalla demuestra persistencia,
    conectividad, gestión del ciclo de vida, accesibilidad u otros
    comportamientos que no puede mostrar.
-   Nunca afirmes que una comprobación se superó si no la ejecutaste y
    observaste su resultado.
-   Si la validación está bloqueada, indica la causa, qué aspectos
    siguen sin verificarse y la comprobación concreta necesaria para
    completarla.

## Mantener la coherencia de la documentación

-   Mantén la coherencia entre los requisitos, las decisiones técnicas,
    el estado de las tareas y el comportamiento implementado, siempre
    que existan documentos sobre ellos.
-   No reescribas silenciosamente los requisitos ni los criterios de
    aceptación para que coincidan con una implementación defectuosa.
    Aclara con el usuario los cambios de comportamiento propuestos.
-   Marca el trabajo como completado solo cuando se hayan satisfecho los
    requisitos aplicables de implementación y validación. Registra los
    avances parciales y las comprobaciones pendientes.

## Comunicar con claridad

-   Utiliza el idioma del usuario para la conversación y los informes de
    finalización. Respeta las convenciones lingüísticas del repositorio
    para el código y la documentación que se incorpore mediante
    *commit*.
-   Explica de forma concisa las decisiones importantes, los bloqueos y
    las desviaciones.
-   En el informe final, indica qué ha cambiado, qué se ha verificado y
    qué queda incompleto o sujeto a incertidumbre. Incluye las rutas de
    archivos pertinentes y los siguientes pasos concretos solo cuando
    sean necesarios.
