package com.rocioplata.familyfinaiapp.data.family

import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.HeaderData
import com.rocioplata.familyfinaiapp.domain.header.HeaderRefreshResult
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import com.rocioplata.familyfinaiapp.domain.header.Timed
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersData
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersRepository
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersResult
import com.rocioplata.familyfinaiapp.session.Session
import com.rocioplata.familyfinaiapp.session.SessionFailure
import com.rocioplata.familyfinaiapp.session.SessionProvider
import com.rocioplata.familyfinaiapp.session.SessionUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

/** Falsos compartidos por los tests de los repositorios de gestión de familias e invitaciones. */

/** Cabecera con la misma semántica que `HeaderRepositoryImpl` para los cambios locales, sin red ni caché. */
internal class FakeHeaderRepository(
    families: List<Family>? = null,
    pendingInvitations: Int? = null,
) : HeaderRepository {
    val data = MutableStateFlow(
        HeaderData(
            families = families?.let { Timed(it, 1L) },
            pendingInvitations = pendingInvitations?.let { Timed(it, 1L) },
        ),
    )
    var refreshes = 0

    /** Resultado de cada refresco; por defecto, todo correcto. */
    var refreshResult: HeaderRefreshResult = HeaderRefreshResult()

    /** Se ejecuta al refrescar: permite comprobar qué había ya en la lista en ese momento. */
    var onRefresh: () -> Unit = {}
    override val header: Flow<HeaderData> = data

    val families: List<Family>? get() = data.value.families?.value
    val pending: Int? get() = data.value.pendingInvitations?.value

    override suspend fun refresh(): HeaderRefreshResult {
        refreshes++
        onRefresh()
        return refreshResult
    }

    override suspend fun clear() {
        data.value = HeaderData()
    }

    override suspend fun addFamily(family: Family) = change { list ->
        if (list.any { it.id == family.id }) list.map { if (it.id == family.id) family else it } else list + family
    }

    override suspend fun removeFamily(familyId: String) = change { list -> list.filterNot { it.id == familyId } }

    override suspend fun setFamilyOrder(orderedIds: List<String>) = change { list ->
        val ordered = orderedIds.mapNotNull { id -> list.firstOrNull { it.id == id } }
        ordered + list.filter { f -> ordered.none { it.id == f.id } }
    }

    override suspend fun updateFamily(familyId: String, change: (Family) -> Family) =
        change { list -> list.map { if (it.id == familyId) change(it) else it } }

    override suspend fun setPendingInvitations(count: Int) {
        data.value = data.value.copy(pendingInvitations = Timed(count, 1L))
    }

    private fun change(transform: (List<Family>) -> List<Family>) {
        val current = data.value.families ?: return
        data.value = data.value.copy(families = Timed(transform(current.value), current.savedAtMillis))
    }
}

/** Miembros que solo registran los cambios aplicados. */
internal class RecordingMembersRepository : FamilyMembersRepository {
    val roles = mutableListOf<Triple<String, String, FamilyRole>>()
    val removals = mutableListOf<Pair<String, String>>()
    override fun members(familyId: String): Flow<FamilyMembersData> = flowOf(FamilyMembersData())
    override suspend fun refresh(familyId: String) = FamilyMembersResult.Success(Unit)
    override suspend fun refreshWithEmails(familyId: String) = FamilyMembersResult.Success(emptyMap<String, String>())
    override suspend fun applyRole(familyId: String, userId: String, role: FamilyRole) {
        roles += Triple(familyId, userId, role)
    }

    override suspend fun applyRemoval(familyId: String, userId: String) {
        removals += familyId to userId
    }

    override suspend fun clear() = Unit
    override suspend fun retainFamilies(familyIds: Set<String>) = Unit
}

internal class FakeConnectivity(var online: Boolean = true) : ConnectivityObserver {
    override val isOnline: Flow<Boolean> get() = flowOf(online)
}

internal class FixedSession(var userId: String = "u-1", var failure: SessionFailure? = null) : SessionProvider {
    override suspend fun current(): Session {
        failure?.let { throw SessionUnavailableException(it) }
        return Session(userId, "tok")
    }
}

/** Backend simulado: responde por método y ruta y cuenta las llamadas. */
internal class FakeBackend {
    val calls = AtomicInteger()
    val requests = mutableListOf<Pair<HttpMethod, String>>()
    var offline = false
    private val responses = mutableMapOf<Pair<HttpMethod, String>, Pair<HttpStatusCode, String>>()

    fun on(method: HttpMethod, path: String, status: HttpStatusCode, body: String = "") {
        responses[method to path] = status to body
    }

    fun client(): HttpClient {
        val engine = MockEngine { request ->
            calls.incrementAndGet()
            requests += request.method to request.url.encodedPath
            if (offline) throw java.io.IOException("sin red")
            val (status, body) = responses[request.method to request.url.encodedPath]
                ?: (HttpStatusCode.NotFound to """{"error":"SIN_RESPUESTA"}""")
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
            install(SessionAuth) { accessToken = { "tok" } }
        }
    }
}
