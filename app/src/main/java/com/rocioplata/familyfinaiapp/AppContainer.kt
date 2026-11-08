package com.rocioplata.familyfinaiapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.rocioplata.familyfinaiapp.data.auth.AuthRemoteDataSource
import com.rocioplata.familyfinaiapp.data.auth.AuthRepositoryImpl
import com.rocioplata.familyfinaiapp.data.header.HeaderLocalDataSource
import com.rocioplata.familyfinaiapp.data.header.HeaderRemoteDataSource
import com.rocioplata.familyfinaiapp.data.header.HeaderRepositoryImpl
import com.rocioplata.familyfinaiapp.data.network.AndroidConnectivityObserver
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.data.session.EncryptedSessionStore
import com.rocioplata.familyfinaiapp.data.session.KeystoreSessionCipher
import com.rocioplata.familyfinaiapp.domain.auth.AuthRepository
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import com.rocioplata.familyfinaiapp.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private val Context.headerCacheStore: DataStore<Preferences> by preferencesDataStore(name = HEADER_CACHE_NAME)

// El nombre debe coincidir con la exclusión de backups (backup_rules.xml, data_extraction_rules.xml).
private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = SESSION_STORE_NAME)

const val HEADER_CACHE_NAME = "header_cache"
const val SESSION_STORE_NAME = "session"
private const val CONNECT_TIMEOUT_MS = 10_000L
private const val REQUEST_TIMEOUT_MS = 15_000L

class AppContainer(context: Context) {
    val headerCacheStore: DataStore<Preferences> = context.applicationContext.headerCacheStore

    /** Cliente sin Bearer, para `/auth/login`, `/auth/register` y `/auth/refresh`. */
    val publicClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    /**
     * Cliente de la API: comparte motor y configuración con [publicClient] y añade el Bearer y la
     * renovación única ante un 401. Resuelve [sessionManager] al llamarse, lo que rompe el ciclo
     * de construcción entre ambos.
     */
    val apiClient: HttpClient = publicClient.config {
        install(SessionAuth) {
            accessToken = { sessionManager.accessToken() }
            refresh = { used -> sessionManager.refreshAfter(used) }
        }
    }

    val connectivityObserver: ConnectivityObserver = AndroidConnectivityObserver(context)

    private val authRemote = AuthRemoteDataSource(publicClient, apiClient, BuildConfig.BACKEND_BASE_URL)

    val sessionManager: SessionManager by lazy {
        SessionManager(
            store = EncryptedSessionStore(context.applicationContext.sessionStore, KeystoreSessionCipher()),
            remote = authRemote,
            onSessionEnded = { headerRepository.clear() },
        )
    }

    val authRepository: AuthRepository by lazy { AuthRepositoryImpl(authRemote, sessionManager) }

    val headerRepository: HeaderRepository by lazy {
        HeaderRepositoryImpl(
            remote = HeaderRemoteDataSource(apiClient, BuildConfig.BACKEND_BASE_URL),
            local = HeaderLocalDataSource(headerCacheStore),
            sessionProvider = sessionManager,
        )
    }
}
