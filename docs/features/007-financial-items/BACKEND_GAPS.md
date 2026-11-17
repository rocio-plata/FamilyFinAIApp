# Brechas de backend — spec 007-financial-items

Verificado contra `docs/backend/api-contract.md` (§3.4.3 y §6.3) y, el
2026-11-15, leyendo el código del backend (`familyFinAI-backend`,
`src/contexts/financial-tracking`). El 2026-11-17 se probó en ejecución, con el
emulador y el backend local, una parte de lo anterior (ver «Observado en
ejecución»); lo que no figura ahí sigue sin comprobarse.

| ID | Brecha | Estado | Evidencia |
| --- | --- | --- | --- |
| BG-01 | El listado no tiene paginación ni orden: devuelve todos los movimientos de la familia que cumplen el filtro, sin `ORDER BY` (I2 del contrato). La app debe acotar siempre por período y ordenar ella misma. | Abierta, a resolver en la app | `drizzle-financial-item.repository.ts` (`findByFamilyId`: `select().where(...)` sin orden ni límite) |
| BG-02 | El contrato dice que `from` y `to` deben enviarse juntos; el backend los acepta por separado. La app los enviará siempre juntos. | Abierta, no bloqueante (documental) | `financial-tracking.routes.ts` (`GET …/items`) y `findByFamilyId` |
| BG-03 | `recordedBy` es un UUID sin nombre (I3 del contrato). La app lo resuelve cruzándolo con `GET /families/:familyId/members` (`userId`, `displayName`), con dos límites: quien ya no es miembro no aparece en esa lista (se muestra "Ex-miembro") y `displayName` puede venir vacío (texto genérico). | Abierta, resuelta en la app con esos límites | `GET …/items` devuelve `recordedBy` sin nombre; `get-family-members.query.ts` devuelve `displayName: string \| null` solo de los miembros actuales |
| BG-04 | El listado devuelve `paymentMethodId` (puede ser `null`), pero el ejemplo del contrato no lo incluye. | Abierta, no bloqueante (documental) | `financial-tracking.routes.ts` (`GET …/items`) |
| BG-05 | El listado no filtra por medio de pago ni por texto; solo por `from`, `to`, `categoryId`, `tagId` y `type`. Esos filtros, si se quieren, tendría que aplicarlos la app sobre lo descargado. | Abierta, no bloqueante | Esquema `querystring` de `GET …/items` |
| BG-06 | No existe `GET` de un movimiento individual: el detalle solo puede salir del listado. | Abierta, a resolver en la app | Contrato §3.4.3 y rutas del backend |
| BG-07 | Los errores llegan como `FINANCIAL_TRACKING.*`, no `FINANCIAL.*` (igual que BG-04 de la 006), y hay códigos que el contrato no lista para movimientos: `PAYMENT_METHOD_NOT_ACTIVE`, `TAG_NOT_ACTIVE`, `SHARED.CURRENCY_MISMATCH` y los de título u observación inválidos. | Abierta, a resolver en la app | `domain/errors/*.error.ts`, `create-financial-item.usecase.ts`, `update-financial-item.usecase.ts` |
| BG-08 | La app no puede leer el medio de pago predeterminado del usuario (BG-01 de la 006). Si se omite `paymentMethodId`, el backend usa el predeterminado o falla con `NO_DEFAULT_PAYMENT_METHOD_SET`, sin que la app pueda mostrar cuál se usará. | Abierta; condiciona el formulario | `create-financial-item.usecase.ts` (`resolvePaymentMethodId`) |
| BG-09 | La fecha no puede ser futura respecto al instante del servidor, y una fecha sin hora (`YYYY-MM-DD`) se interpreta como medianoche UTC. Según la zona horaria del dispositivo, "hoy" podría rechazarse o guardarse en el día equivocado si la app no envía un instante bien formado. | Abierta, a resolver en la app (PLAN) | `transaction-date.ts` (`date > new Date()`) y `new Date(occurredOn)` en las rutas |
| BG-10 | La moneda del movimiento debe ser la de la familia (`SHARED.CURRENCY_MISMATCH`). `GET /me/families` ya devuelve `defaultCurrency`, pero la app todavía no lo lee. | Abierta, a resolver en la app | `create-financial-item.usecase.ts`; respuesta de `GET /me/families` |
| BG-11 | No se pueden registrar movimientos con fecha futura: el backend los rechaza al crear y al editar. La persona quiere poder registrarlos; requiere un cambio del backend (y decidir cómo cuentan en dashboard, presupuestos y reportes). Mientras tanto la app no permite elegir fechas futuras. | Abierta, a pedir al backend; no bloqueante para la 007 | Contrato §3.4.3 (`occurredOn`: "No puede ser una fecha futura") y `transaction-date.ts` (`FUTURE_TRANSACTION_DATE`) |

## Observado en ejecución (2026-11-17)

Emulador con el backend local, cuenta de desarrollo (Propietario), familia «Mis finanzas personales» con moneda CLP y zona horaria `America/Santiago` (T-23 de `TASKS.md`).

| ID | Observado | Sin comprobar |
| --- | --- | --- |
| BG-01 | El listado del mes con `from` y `to` devuelve los movimientos del período y la app los ordena (día y `createdAt` descendentes). Un mes sin movimientos devuelve lista vacía. | El orden sin que la app lo corrija |
| BG-03 | `recordedBy` se resuelve con la lista de miembros: el movimiento propio muestra el nombre visible del usuario, sus iniciales y su rol «Propietario». | Otro miembro, «Ex-miembro» y nombre vacío (no hay una segunda cuenta en la familia) |
| BG-06 | Sin consulta individual, el detalle sale de los meses cargados y funciona al abrirlo desde el listado. Consecuencia en la app (corregida): una edición que movía el movimiento a un mes no cargado dejaba el detalle diciendo que ya no existía; ahora se vuelve al listado. | |
| BG-09 | Un día pasado elegido en el dispositivo (15 de octubre) se guarda y se vuelve a mostrar como el mismo día, en la zona `America/Santiago`; el día de hoy y el de ayer también. El selector no permite días futuros. | Otras zonas horarias y el caso cercano a la medianoche |
| BG-10 | La moneda de la familia (CLP) llega por `GET /me/families` y la muestra y usa el formulario. | Un cambio de moneda de la familia (`SHARED.CURRENCY_MISMATCH`) |
| BG-11 | La app no ofrece fechas futuras. | La respuesta real del backend a una fecha futura (la app nunca la envía) |
| BG-07 | No se observó ningún error de dominio del backend, así que el prefijo real (`FINANCIAL_TRACKING.*` o `FINANCIAL.*`) de los movimientos sigue sin comprobarse; la app acepta ambos. | El prefijo y los códigos `PAYMENT_METHOD_NOT_ACTIVE`, `TAG_NOT_ACTIVE` y los de título u observación |
| BG-02, BG-04, BG-05, BG-08 | No se comprobaron en ejecución: la app siempre envía `from` y `to` juntos, siempre envía `paymentMethodId` y no filtra por medio de pago ni por texto. | La presencia de `paymentMethodId` en el listado de una sesión nueva |

No aparecieron brechas nuevas del backend en la ejecución.

