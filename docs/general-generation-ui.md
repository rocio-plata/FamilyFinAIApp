# Prompt para generar la UI de FamilyFin AI

> Copia y pega este prompt completo en la herramienta de generación de UI que estés usando (v0, Lovable, Figma AI, Galileo, Uizard, o directamente en Claude/otro asistente de código). Ajusta la sección "Plataforma objetivo" si tu herramienta genera código real en vez de mockups.

---

## Contexto del producto

Estoy construyendo **FamilyFin AI** ("Smarter family finances"), una aplicación móvil para la gestión colaborativa de finanzas familiares. Varios miembros de una misma familia registran gastos e ingresos, consultan una economía compartida, y reciben asistencia de inteligencia artificial para simplificar el registro. La filosofía central del producto es: **"la IA propone, el usuario confirma"** — la IA nunca guarda nada automáticamente, siempre muestra una propuesta editable que el usuario debe confirmar.

El backend ya está construido (Node.js + TypeScript + Fastify + PostgreSQL, arquitectura DDD). Necesito que diseñes las **pantallas de la app móvil** que consumen esa API.

## Plataforma objetivo

App móvil nativa Android, construida con **Kotlin + Jetpack Compose**. Diseña pensando en un layout mobile-first (una sola columna, componentes táctiles grandes, navegación por bottom navigation bar o drawer). Si tu herramienta no genera Compose directamente, genera el diseño/mockup en un lenguaje visual equivalente (Material Design 3 es un buen default para este ecosistema).

---

## Modelo mental clave: multi-familia

Un mismo usuario puede pertenecer a **varias familias independientes entre sí** — por ejemplo: su familia actual, la familia de un hijo que vive con el otro progenitor, y sus propias finanzas personales (que técnicamente es solo una familia con un único miembro). El usuario **elige qué familia está viendo** en cada momento; hay una familia por defecto (la primera de una lista ordenable) que se abre al entrar a la app.

**Implicación de diseño**: necesito un **selector de familia** siempre accesible (por ejemplo, en la barra superior, mostrando el nombre de la familia activa y permitiendo cambiar con un tap), y prácticamente toda otra pantalla de datos financieros está "scoped" a la familia actualmente seleccionada.

---

## Funcionalidades a cubrir (pantallas)

### 1. Autenticación
- **Login**: email + contraseña.
- **Registro**: email, contraseña, nombre. Al registrarse, se crea automáticamente una familia personal ("Mis finanzas personales") — el usuario entra directo a la app con al menos una familia ya lista, sin pantallas intermedias de "crea tu primera familia".

### 2. Selector de familias
- Lista de todas las familias del usuario, en el orden que él definió (la primera es la que se abre por defecto).
- Cada ítem muestra: nombre de la familia, y quizás un ícono/color distintivo.
- Permite **reordenar** (drag & drop) para cambiar cuál es la default.
- Acceso para crear una familia nueva.
- Acceso para entrar a la gestión de miembros de la familia seleccionada.

### 3. Gestión de familia (dentro de una familia específica)
- Lista de miembros, con su rol (`Owner`/`Member`).
- Invitar a un nuevo miembro por email, eligiendo su rol.
- Lista de invitaciones pendientes, con opción de revocar.
- Cambiar el rol de un miembro (solo visible/editable si el usuario actual es `Owner`).
- Remover a un miembro.
- Configuración de la familia: nombre, moneda por defecto (`CLP` u otras).

### 4. Dashboard (pantalla principal)
- Vista del **período actual** (mes en curso) por defecto, con selector para cambiar de período.
- Resumen: total de gastos, total de ingresos, balance.
- Gráfico de distribución de gastos por categoría (torta o barras).
- Acceso rápido para **agregar un movimiento nuevo** (botón flotante, prominente).
- Acceso rápido a los tres métodos de registro: manual, por texto libre (IA), por foto de recibo (IA).

### 5. Categorías — dos pantallas separadas
**Importante**: el tipo (Gasto/Ingreso) pertenece a la categoría, no al movimiento — por eso son dos listados separados, no uno con un filtro:
- **Pantalla "Categorías de gasto"**: lista de categorías tipo `Expense`, con sus tags. Crear/renombrar/deprecar/eliminar categoría. Ver y reordenar tags dentro de cada categoría (drag & drop).
- **Pantalla "Categorías de ingreso"**: mismo comportamiento, para categorías tipo `Income`.
- Las categorías "deprecadas" se muestran diferenciadas visualmente (atenuadas/con badge) y no aparecen como opción al crear un movimiento nuevo, pero sí en el histórico.

### 6. Registro de movimientos
- **Formulario manual**: monto, categoría (selector filtrado por si es gasto o ingreso — el tipo del movimiento se define al elegir la categoría, no antes), tag opcional (dependiente de la categoría elegida), título, nota opcional, fecha. Por defecto se asume gasto, así que el flujo más rápido debería priorizar categorías de gasto.
- **Registro por texto libre (IA)**: un campo de texto donde el usuario escribe algo como *"gasté 25000 en bencina ayer"*, y la IA devuelve una **propuesta editable** (monto, categoría, fecha, título ya completados) que el usuario revisa, corrige si hace falta, y confirma antes de guardar.
- **Registro por foto de recibo (IA)**: cámara/selector de imagen, la IA analiza el recibo y devuelve una propuesta editable (comercio, monto, categoría sugerida), igual que el anterior — siempre un único movimiento por recibo, nunca se divide automáticamente.

### 7. Listado y detalle de movimientos
- Lista filtrable por período, categoría, tag, y tipo (gasto/ingreso).
- Cada ítem muestra: título, categoría/tag, monto, fecha.
- Al tocar un movimiento: detalle completo, con opciones de editar (monto, fecha, título, nota) o reclasificar (cambiar de categoría — solo hacia otra categoría del mismo tipo, gasto→gasto o ingreso→ingreso) o eliminar.

### 8. Reportes
- **Distribución por categoría**: gráfico de torta/barras para el período seleccionado.
- **Comparación entre períodos**: mes actual vs. mes anterior (u otro rango), con variación porcentual.
- **Tendencia/evolución**: gráfico de línea de ingresos y gastos a lo largo de varios meses.
- **Drill-down**: desde el resumen general, tocar una categoría lleva a ver sus tags, y tocar un tag lleva a los movimientos individuales que lo componen — navegación en profundidad, tipo breadcrumb.

### 9. Presupuestos (diseñado, aún no implementado en el backend — igual diséñalo)
- Lista de presupuestos por categoría de gasto, mostrando: categoría, presupuesto, gastado, disponible (con barra de progreso, y alerta visual si se supera el límite).
- Crear presupuesto: elegir categoría de gasto, monto mensual por defecto.
- Los presupuestos son **recurrentes** (se definen una vez y aplican cada mes), con posibilidad de sobrescribir el monto para un mes específico puntual.

### 10. Asistente / Insights (diseñado, aún no implementado en el backend — igual diséñalo)
- Pantalla de preguntas en lenguaje natural sobre las propias finanzas (ej. *"¿cuánto gasté en restaurantes este mes?"*), con respuesta redactada a partir de datos reales.
- Feed de insights/recomendaciones automáticas (ej. *"tus gastos en restaurantes subieron 35% este mes"*), presentado como tarjetas dentro del dashboard o en una pantalla propia.

### 11. Perfil de usuario
- Ver/editar nombre para mostrar.
- Cambiar contraseña.
- Cerrar sesión.

---

## Principios de diseño a respetar

1. **"La IA propone, el usuario confirma"**: cualquier pantalla que involucre IA (texto libre, recibo, insights) debe mostrar claramente que es una **sugerencia editable**, nunca un dato ya guardado — usa un estado visual distinto (por ejemplo, un borde punteado o una etiqueta "sugerido por IA") hasta que el usuario confirme.
2. **Gasto/Ingreso separados desde la categoría**, no como un toggle en el formulario de movimiento — la elección de categoría ya implica el tipo.
3. **Todo está "scoped" a una familia activa** — el selector de familia debe sentirse persistente y accesible, no enterrado en un menú.
4. **Multi-rol**: algunas acciones (invitar, remover miembros, cambiar roles, moneda) solo deberían ser accionables si el usuario es `Owner` de la familia activa — el diseño debe contemplar estados deshabilitados/ocultos para `Member`.
5. **Categorías/tags deprecados** se preservan visualmente en el histórico pero no deben ofrecerse como opción activa al crear algo nuevo.

## Qué necesito que generes

Para cada una de las 11 pantallas/flujos de arriba: un diseño de UI mobile (wireframe o mockup de alta fidelidad, según lo que tu herramienta soporte), con los componentes, jerarquía visual y estados (vacío, cargando, con datos, error) que correspondan. Si puedes proponer una paleta de colores y tipografía coherente con un producto financiero confiable pero cercano (no corporativo/frío), inclúyela también.
