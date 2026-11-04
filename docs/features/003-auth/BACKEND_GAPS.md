# Brechas de backend — spec 003-auth

Verificado contra `docs/backend/api-contract.md` (§2.1, §3.2, §4). No se ha
verificado el código del backend.

| ID | Brecha | Estado | Evidencia |
| --- | --- | --- | --- |
| BG-01 | `POST /auth/register` permite enumerar cuentas: responde `409 IDENTITY.EMAIL_ALREADY_REGISTERED` cuando el email existe. La app muestra un mensaje neutro (RF-04), pero quien llame a la API directamente sigue sabiendo si el email está registrado. Protegerlo requiere un flujo con respuesta uniforme y verificación o aviso por email. | Abierta, no bloqueante | Contrato §3.2 (`POST /auth/register`, errores) y §4 |
