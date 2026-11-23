# Brechas de backend — spec 008-dashboard

Verificado contra `docs/backend/api-contract.md` (§3.4.2, §3.6 y §6.2) y
`docs/backend/openapi.yaml`. No se ha verificado el código del backend.

| ID | Brecha | Estado | Evidencia |
| --- | --- | --- | --- |
| BG-01 | La categoría no tiene color (BG-02 de la 005). La 008 lo necesita para el gráfico y las tarjetas (RF-14) y para elegirlo en el alta y la edición (RF-15). Se pide un campo opcional `color` en la categoría: una clave de la paleta de la app de 1 a 40 caracteres que el backend solo guarda, igual que `icon`. Se acepta en `POST` y `PATCH /families/:familyId/categories`, se devuelve en `GET` y se puede quitar enviando `null`. | Resuelta en el backend (informado por la persona el 2026-11-19; no verificado en ejecución) | Contrato §3.4.2 actualizado: `color` en `POST` (1 a 40 caracteres), `newColor` (o `null`) en `PATCH`, `color` en `GET` y esquema `Category` de `openapi.yaml`; nuevo error `FINANCIAL_TRACKING.INVALID_CATEGORY_COLOR` (§9) |
| BG-02 | `GET /families/:familyId/dashboard` solo traía el desglose de gastos, con `categoryId` y sin las categorías en $0 (B1 y B2 del contrato). Ya trae `expenseByCategory` e `incomeByCategory`, con las categorías en $0, las archivadas con movimientos, `icon`, `color` y `percentage`, ordenado por monto. Aun así la 008 no lo usa (decisión del 2026-11-19): calcula el dashboard en la app a partir de los movimientos del mes (RF-13), porque así coincide siempre con "Movimientos", funciona con la copia local y se actualiza al instante tras un cambio. El contrato no indica con qué zona horaria delimita `period`. | Resuelta en el backend (informado por la persona el 2026-11-19); no se usa en la 008 | Contrato §3.6 y nota de estado de §6.2 (B1–B3 implementadas) |
| BG-03 | La respuesta de `GET /reports/comparison` no coincide entre documentos: el contrato da `expenseVariation`/`incomeVariation` como número y `openapi.yaml` como `{ absolute, percentage }`. La 008 no la usa (calcula la variación en la app, RF-06). | Abierta, no bloqueante (documental) | Contrato §3.6 frente al esquema `PeriodComparison`/`PeriodVariation` de `openapi.yaml` |

## Observado en ejecución (2026-11-22)

- **BG-01:** con el backend local, el color de una categoría se guarda y se lee en la app (selector de 16 colores y
  dashboard). Quitarlo con `null` y el comportamiento sin conexión los probó la persona en la T-16 sin
  diferencias reportadas; el error `FINANCIAL_TRACKING.INVALID_CATEGORY_COLOR` no se provocó.
- **BG-02 y BG-03:** no se usan en la 008, así que no se observaron.
- **Montos por medio de pago (RF-16 a RF-18):** usa el listado de medios de pago y los movimientos que ya existen; no hay brechas nuevas.
