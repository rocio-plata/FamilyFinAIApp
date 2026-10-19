# FamilyFin AI — Prompts para Google Stitch (pantalla por pantalla)

## Cómo usarlo

1. **Paso 1:** pega el **BLOQUE DE CONTEXTO** + el **Prompt 0 (esqueleto + Dashboard)**. Esto fija paleta, tipografía, componentes y navegación. No generes nada más hasta que el Dashboard te convenza.
2. **Paso 2 en adelante:** para cada pantalla pega **solo su prompt corto**, en el mismo proyecto de Stitch, para que herede el estilo. Si Stitch pierde el estilo, antepón una línea: *"Keep exactly the same visual style, colors, typography and components as the Dashboard screen."*
3. Genera una pantalla por prompt (cuida tus generaciones gratuitas; revisa los límites vigentes en Stitch, pueden cambiar).
4. Los prompts están en inglés (Stitch rinde mejor), pero los textos de la UI van en español.
5. **Stitch no exporta Jetpack Compose** (exporta Figma/HTML/Tailwind). Úsalo como referencia visual y luego pídele a Claude que traduzca cada pantalla a Compose + Material 3.

Orden recomendado: 0 → 1 → 2 → 4 → 5 → 6 → 7 → 3 → 8 → 9 → 10 → 11.

---

## BLOQUE DE CONTEXTO (pegar solo en el primer prompt)

```
Design a mobile app (Android, Material Design 3, portrait) called "FamilyFin AI" — tagline "Smarter family finances". It is a collaborative family finance app: several family members use their own phones and share one family economy. AI helps register and analyze expenses, but follows this principle: "AI proposes, the user reviews and confirms."

UI language: Spanish. Currency: Chilean pesos (CLP), formatted like $85.450 (no decimals, dot as thousands separator).

Visual style: clean, trustworthy, friendly. Soft rounded cards (16dp radius), generous spacing, one primary color (a calm teal/green), semantic colors: red/coral for expenses, green for income, amber for warnings. Light theme first. Clear typography hierarchy with large numbers for amounts. Bottom navigation with: Inicio, Movimientos, Reportes, Presupuestos, Asistente. Profile is reached from the top bar avatar.

Global rules for every screen:
- Everything is scoped to the ACTIVE FAMILY, shown in a family selector in the top bar (dropdown with the family name; user can belong to several families).
- Expenses and Income are always visually separated (own categories, own color, own sections).
- Roles: Owner and Member. Owner-only actions are hidden or disabled for Members.
- Categories have an icon (from a catalog) and a name. Deprecated categories remain visible in history, marked as "Archivada".
- Any AI-generated suggestion is shown as an editable proposal with a clear "Confirmar" button and a small "Sugerido por IA" badge. Never auto-save.
- Include empty states, loading and error states when relevant.
```

---

## Prompt 0 — Esqueleto + Dashboard (Inicio)

```
[PASTE THE CONTEXT BLOCK ABOVE]

Create the main screen "Inicio" (Dashboard) for the active family.
- Top bar: family selector dropdown on the left (e.g. "Familia Plata"), notifications icon and user avatar on the right.
- Period selector below (default: current month, e.g. "Octubre 2026") with prev/next arrows and an option to change the period.
- Balance card: income, expenses and balance for the period, large numbers.
- Section "Gastos por categoría": grid of category tiles (3 columns). Each tile shows the category icon, name, and the amount spent below. Categories: Comestibles, Salud, Restaurantes, Servicios, Compras, Regalos, Familia, Tiempo Libre, Transporte. Show $0 for categories with no movements.
- Section "Ingresos por categoría": same grid style, green accents, with 3 example income categories (Sueldo, Honorarios, Otros).
- A donut chart of expense distribution by category.
- Floating action button "+" that expands into three options: "Registro manual", "Escribir con IA", "Foto de recibo".
- Bottom navigation bar with 5 items (Inicio selected).
```

---

## Prompt 1 — Autenticación

```
Same style as the Dashboard. Create three screens: (1) Login with email + password, "Iniciar sesión" button, link to register; (2) Register with display name, email, password, confirm password; (3) Forgot password placeholder is NOT needed. App logo and tagline "Smarter family finances" on top. Clean, minimal. After login/register the user lands directly on the Dashboard of their default family.
```

## Prompt 2 — Selector de familias

```
Same style as the Dashboard. Bottom sheet opened from the family selector in the top bar: list of the user's families (name, role badge Owner/Member, member count), the active one checked, drag handles to reorder, and buttons "Crear nueva familia" and "Unirme con invitación". Also show a screen for "Crear familia" (name + default currency CLP) and a list of pending invitations with Aceptar / Rechazar.
```

## Prompt 3 — Gestión de familia

```
Same style as the Dashboard. "Mi familia" screen: family name and currency, list of members (avatar, name, email, role badge). Owner sees actions per member: change role, remove. Section "Invitaciones pendientes" with email, status, and "Revocar". Button "Invitar miembro" opens a form (email + role). Members see the same screen read-only, plus a "Salir de la familia" option.
```

## Prompt 4 — Categorías (gasto / ingreso)

```
Same style as the Dashboard. "Categorías" screen with a segmented control: Gastos | Ingresos. List of categories with icon, name, and a "Archivada" chip when deprecated. Tapping a category opens an edit screen: name, icon picker (grid of icons), status (activa/archivada), and its tags (list with drag to reorder, add tag). Button "Nueva categoría" asks for type (fixed by the active tab), name and icon. Archived categories are shown in a collapsed section at the bottom.
```

## Prompt 5 — Registro manual de movimiento

```
Same style as the Dashboard. "Nuevo movimiento" form. Toggle at top: Gasto (default, selected) | Ingreso. Fields: amount (large numeric input), title, category (picker showing only categories of the selected type, with icons), optional tag (depends on category), payment method (picker, pre-filled with the user's default), date (default today), observation (multiline). Primary button "Guardar". Changing the toggle resets the category.
```

## Prompt 6 — Registro con texto (IA)

```
Same style as the Dashboard. "Escribir con IA" screen: a text field with placeholder "Ej: Gasté $25.000 en bencina ayer" and a "Interpretar" button. Below, an AI proposal card labeled "Sugerido por IA" with editable fields: type, amount, date, category, tag, title, payment method. Buttons "Confirmar y guardar" and "Descartar". Show a loading state and an error state ("No pude interpretar el texto").
```

## Prompt 7 — Registro con foto de recibo (IA)

```
Same style as the Dashboard. "Foto de recibo" flow: (1) camera/gallery selection screen with a framing guide; (2) processing state; (3) review screen showing the receipt thumbnail and an editable AI proposal: merchant, total amount, date, title, category, tag, payment method — exactly one expense per receipt. Badge "Sugerido por IA", buttons "Confirmar y guardar" and "Reintentar". Mention when the category came from history ("Basado en tus compras anteriores").
```

## Prompt 8 — Listado y detalle de movimientos

```
Same style as the Dashboard. "Movimientos" screen: filter chips (período, tipo Gasto/Ingreso, categoría, tag, medio de pago), list grouped by day, each row with category icon, title, category · tag, who registered it (avatar) and amount in red/green. Search bar on top. Tapping opens a detail screen with all fields, registered-by user and date, and actions "Editar" and "Eliminar" (with confirmation). Include an empty state.
```

## Prompt 9 — Reportes

```
Same style as the Dashboard. "Reportes" screen: period selector, tabs Gastos | Ingresos | Comparativa. Charts: bar chart of income vs expenses over time, donut by category, comparison against the previous period with % change. Drill-down: tapping a category shows its tags, tapping a tag shows the individual movements. Add a section "Por medio de pago". Breadcrumb at top for the drill-down (Gastos > Comestibles > Supermercado).
```

## Prompt 10 — Presupuestos

```
Same style as the Dashboard. "Presupuestos" screen for the current month: list by category with budget, spent, remaining, and a progress bar (green, amber over 80%, red when exceeded, showing negative remaining like -$30.000). Summary card with total budget vs total spent. Tapping a category opens an edit sheet: monthly amount for this month only, or default amount for upcoming months. Button "Nuevo presupuesto". Empty state explaining the feature (it is optional and can be enabled from Ajustes).
```

## Prompt 11 — Asistente e Insights (IA)

```
Same style as the Dashboard. "Asistente" screen with two tabs: Chat | Insights. Chat: conversation UI where the user asks things like "¿Cuánto gastamos en restaurantes los últimos tres meses?"; answers include a small summary card with the figure and a "Ver movimientos" link. Suggested question chips. Insights: cards such as "Tus gastos en restaurantes subieron 35% respecto al mes pasado" with a mini chart and a "Ver detalle" action, plus a button "¿Dónde podemos reducir gastos?". Always show a note that insights are based on the family's real data.
```

## Prompt 12 — Perfil y ajustes

```
Same style as the Dashboard. "Perfil" screen: avatar, display name (editable), email, change password, default payment method per family (picker), toggle "Mostrar sección Presupuestos", manage payment methods of the family, language/currency info, and "Cerrar sesión". Version label at the bottom.
```

---

## Consejos

- Si una pantalla sale muy distinta, regenera añadiendo: *"Match the Dashboard's style exactly."*
- Para cambios pequeños usa el chat de edición de Stitch en lugar de regenerar todo.
- Los íconos de categoría vendrán de la API (campo `icon`, string de un catálogo); en Stitch usa íconos Material genéricos.
- Cuando tengas el Dashboard aprobado, avísame y pasamos pantalla por pantalla a Compose.
