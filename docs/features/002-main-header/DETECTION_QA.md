# Detección QA — SPEC 002-main-header

Revisión de `SPEC.md` (Borrador). Solo detección; sin soluciones.

## 1. Ambigüedades restantes
1. «Familia favorita» = primera de `GET /me/families`; el contrato no garantiza que sea una preferencia explícita y puede cambiar tras reordenar o unirse a otra familia.
2. (Resuelto) RF-03 distingue proceso vivo (se mantiene la activa) de proceso terminado (vuelve la favorita).
3. (Resuelto) Al cambiar la familia activa se actualizan la cabecera (RF-02) y el contenido de la pestaña activa (RF-08); el contador y el avatar son del usuario y no cambian.
4. Estado de carga del selector sin definir (qué se ve y cuánto dura); RF-07 menciona carga y error pero no contenido/éxito.
5. (Resuelto) Datos guardados caducan a los 7 días; con más de 24 h se alerta de posible desactualización (CA-16, CA-17).
6. «Se actualizan al volver» sin definir si siempre o con umbral, ni qué ve el usuario durante el refresco.
7. Inicial del avatar sin regla para espacios, emojis, signos, tildes o alfabetos no latinos.
8. Formato de la «lista» del selector sin definir (hoja, menú, diálogo).
9. «Marcando la activa» sin definir el medio (no solo color).
10. Textos accesibles dados como «p. ej.»; sin texto definitivo ni plural/singular.
11. Tiempo máximo de espera PENDIENTE, aunque es comportamiento observable y afecta a CA-09.
12. (Resuelto) RF-06 ya no navega al perfil: el icono solo muestra la inicial y no ejecuta acción; el perfil queda para una spec posterior.
13. (Resuelto) El rol solo puede ser OWNER o MEMBER (`Role` en el contrato); no hay otros valores.

## 2. Contradicciones entre requisitos
14. (Parcial) La tabla y CA-11 cubren el fallo solo del contador o del perfil; RF-04 y RF-05 siguen sin mencionar estados de error, mientras RF-07 habla solo de familias.
15. (Parcial) RF-07 dice que se conservan los comportamientos de la 001; perfil y notificaciones siguen sin acción, pero el selector sí ejecuta una (la 001 RF-01 dice que los tres no ejecutan acciones).
16. (Resuelto) Perfil fuera de alcance vs. RF-06: RF-06 ya no define su contenido ni navegación.
17. (Resuelto) La spec parte de una sesión ya iniciada con usuario conocido; los datos guardados de otro usuario se eliminan (login, cierre de sesión y 401/403 siguen en otra spec).
18. (Resuelto) El usuario autenticado se conoce antes de mostrar datos, sin depender de la red; los datos guardados solo se muestran si son suyos.
19. (Resuelto) Sin conexión con datos de otro usuario: se tratan como sin datos, error con reintento (CA-18).
20. RF-03 (proceso vivo) vs. fila «Recrear la pantalla» (opcional conservar la lista abierta: no comprobable).
21. (Resuelto) Los identificadores RF y CA estaban fuera de orden; se renumeraron en secuencia (RF-01..08, CA-01..18).
22. Contador «sin límite superior» vs. CA-13 (nada cortado con texto grande).
23. CA-05 exige conservar la familia al recrear; la 001 (RF-08) reinicia la pestaña a Inicio; no se aclara la relación.

## 3. Casos límite no cubiertos
24. (Resuelto) Una sola familia: no hay selector ni lista; la cabecera solo muestra nombre y rol (RF-02, CA-19).
25. Lista muy larga de familias.
26. (Parcial) Nombres repetidos, vacíos y con emojis quedan descartados (se validan en la spec de creación de familias) y los muy largos se truncan; sigue abierto el texto RTL.
27. La familia activa desaparece (expulsión/eliminación) tras un refresco.
28. La familia elegida no existe en los datos nuevos o guardados.
29. (Parcial) Si el rol cambia, el backend rechaza la acción con `FAMILY.INSUFFICIENT_ROLE` y se vuelve a consultar el rol (regla en Datos y reglas; criterio en las specs con acciones restringidas); sigue abierto el cambio de nombre de familia con la app abierta.
30. Cambio de `displayName` desde otra pantalla: inicial desactualizada.
31. Perfil con `displayName` vacío o nulo.
32. Invitaciones expiradas entre refresco y visualización.
33. Transiciones del contador 0↔N y su anuncio por lector de pantalla.
34. Respuestas parciales (una llamada falla y otras no) en todas las combinaciones.
35. `GET /me/families` vacío inesperado: sin comportamiento.
36. Red lenta o corte durante el cambio de familia.
37. Cambio de familia con carga en curso o dos elecciones seguidas.
38. (Resuelto) Doble pulsación del icono de notificaciones: no ejecuta acción, no hay efecto duplicable.
39. Errores 5xx, respuesta malformada o campos fuera de contrato.
40. (Resuelto) Cambio de usuario en el mismo dispositivo: los datos del anterior nunca se muestran (CA-18).
41. Cambio de idioma, zona horaria o tamaño de fuente con la lista abierta.
42. Modo oscuro, contraste y tablets/plegables para la lista.
43. Reintentos repetidos del error.
44. Datos guardados corruptos o de una versión anterior de la app.

## 4. Conflictos con MOBILE_GUIDELINES.md y GENERIC_RULES.md
45. (Parcial) Persistencia y consistencia: se definió la regla de actualizar backend y memoria local a la vez, con icono de «solo local» junto a notificaciones si falla la red (RF-09, oculto en esta spec); los conflictos con datos remotos, la sincronización y la compatibilidad con versiones anteriores quedan para la spec de sincronización.
46. Conectividad: sin recuperación automática ni red lenta.
47. Estados de interfaz: «vacío» del selector no definido (el «éxito» del cambio de familia lo define RF-08).
48. Navegación e interrupciones: volver desde otra app con la lista abierta.
49. Adaptación visual: áreas del sistema y orientación de la lista solo heredadas de la 001.
50. Accesibilidad: faltan orden de foco, contraste del contador y no depender solo del color para la activa.
51. (Parcial) Los datos guardados se eliminan a los 7 días o cuando otro usuario accede a la app; sigue abierto el criterio de «sin datos personales en logs».
52. Idiomas y formatos: «Propietario»/«Integrante» fijados en español; longitud en otros idiomas y plural del contador.
53. Rendimiento: sin criterio medible ni consumo de datos/batería por refrescar al volver.
54. Validación mobile: faltan pruebas de cambio de conectividad en operación, tamaños de pantalla y TalkBack con lista abierta; CA-13 mezcla varios casos.
55. Ciclo de vida: «una carga en curso no duplica llamadas» sin criterio de aceptación; llamada en curso al pasar a segundo plano sin definir.
56. GENERIC_RULES (distinguir hechos, decisiones propuestas y pendientes): decisiones confirmadas y propuestas mezcladas sin marcar; línea «Resuelto» dentro de Decisiones pendientes.
57. GENERIC_RULES (no inventar requisitos): 48 dp, truncado de textos, lista desplazable, sin datos personales en logs, no duplicar llamadas y cerrar lista con Atrás no están marcados como propuesta o heredados.
58. GENERIC_RULES (documentos consistentes): marcador PENDIENTE sin resolver antes de pedir aprobación.
59. GENERIC_RULES (validar comportamiento): sin criterio de comprobación para privacidad, rendimiento, idioma, caché entre usuarios, refresco al volver y cambio de usuario (la familia activa al recrear la cubre CA-05).
60. SPEC_TEMPLATE: «Restricciones del pedido» incluye hechos y riesgos que no son restricciones; nombres técnicos (endpoints, `userId`) en requisitos y reglas que la plantilla reserva para PLAN.md.
