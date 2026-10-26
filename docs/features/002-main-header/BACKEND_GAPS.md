# Brechas de backend — spec 002-main-header

Verificado contra `docs/backend/api-contract.md` y `docs/backend/openapi.yaml` actualizados.
Estado: **todas resueltas en la documentación del contrato**. No se ha verificado el código del backend.

| ID | Brecha | Estado | Evidencia |
| --- | --- | --- | --- |
| BG-01 | `GET /me/invitations` (A1), RF-04 | Resuelta | Contrato (§3.3) y openapi (`MyPendingInvitation`): pendientes no expiradas, con `familyName`, `invitedBy`; `401` tipado |
| BG-02 | `userId` en `GET /me/profile` (A6) | Resuelta | Contrato y `Profile` en openapi (`userId` obligatorio) |
| BG-03 | `GET /me/families` (A5), RF-01/RF-02 | Resuelta | `familyId`, `name`, `role`, `defaultCurrency`, `memberCount` obligatorios en `FamilySummary` |
| BG-04 | Familia personal garantizada | Resuelta | `POST /auth/register` la crea ("Mis finanzas personales", OWNER) y devuelve `defaultFamilyId` |
| BG-05 | Errores 401 tipados | Resuelta | `AUTH.MISSING_TOKEN` / `AUTH.INVALID_TOKEN` en el contrato |
| BG-06 | Sincronizar contrato y openapi | Resuelta | Mapa pantalla→endpoints sin bloqueos |

Observaciones menores no bloqueantes:
- En openapi, `/me/families` y `/me/invitations` no declaran la respuesta `401` (el contrato sí).
- El esquema `Invitation` genérico de openapi no incluye `familyName` ni `invitedBy`; los usa solo `MyPendingInvitation`.
