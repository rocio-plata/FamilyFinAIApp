package com.rocioplata.familyfinaiapp.data.network

import com.rocioplata.familyfinaiapp.session.RefreshOutcome
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.IOException

/** La renovación falló por falta de conexión: los data sources lo tratan como error de red. */
class SessionRefreshOfflineException : IOException("No se pudo renovar la sesión sin conexión")

/** La renovación falló por un error del servidor: los data sources lo tratan como error desconocido. */
class SessionRefreshFailedException : IllegalStateException("No se pudo renovar la sesión")

class SessionAuthConfig {
    /** Token vigente o null sin sesión. Se consulta en cada petición. */
    var accessToken: () -> String? = { null }

    /** Renueva tras un 401 obtenido con el token indicado (RF-06). */
    var refresh: suspend (usedAccessToken: String) -> RefreshOutcome = { RefreshOutcome.SessionEnded }
}

/**
 * Añade el Bearer a cada petición y, ante un 401, renueva una vez y reintenta (RF-06). Si el backend
 * rechaza la renovación, la sesión ya está cerrada y se devuelve el 401 original (RF-07). Nunca
 * registra tokens. Las funciones se resuelven al llamarse, lo que evita el ciclo de construcción
 * entre este cliente y `SessionManager`.
 */
val SessionAuth = createClientPlugin("SessionAuth", ::SessionAuthConfig) {
    val accessToken = pluginConfig.accessToken
    val refresh = pluginConfig.refresh

    on(Send) { request ->
        val token = accessToken() ?: return@on proceed(request)
        request.bearer(token)
        val call = proceed(request)
        if (call.response.status != HttpStatusCode.Unauthorized) return@on call

        when (val outcome = refresh(token)) {
            is RefreshOutcome.Refreshed -> {
                // Un segundo 401 se devuelve tal cual: no se renueva de nuevo.
                request.bearer(outcome.accessToken)
                proceed(request)
            }
            RefreshOutcome.SessionEnded -> call
            is RefreshOutcome.Unavailable ->
                if (outcome.offline) throw SessionRefreshOfflineException() else throw SessionRefreshFailedException()
        }
    }
}

private fun HttpRequestBuilder.bearer(token: String) {
    headers[HttpHeaders.Authorization] = "Bearer $token"
}
