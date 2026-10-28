package com.rocioplata.familyfinaiapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.rocioplata.familyfinaiapp.data.header.HeaderLocalDataSource
import com.rocioplata.familyfinaiapp.data.header.HeaderRemoteDataSource
import com.rocioplata.familyfinaiapp.data.header.HeaderRepositoryImpl
import com.rocioplata.familyfinaiapp.data.network.AndroidConnectivityObserver
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import com.rocioplata.familyfinaiapp.session.SessionProvider
import com.rocioplata.familyfinaiapp.session.StaticSessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private val Context.headerCacheStore: DataStore<Preferences> by preferencesDataStore(name = HEADER_CACHE_NAME)

const val HEADER_CACHE_NAME = "header_cache"
private const val CONNECT_TIMEOUT_MS = 10_000L
private const val REQUEST_TIMEOUT_MS = 15_000L

class AppContainer(context: Context) {
    val headerCacheStore: DataStore<Preferences> = context.applicationContext.headerCacheStore

    val httpClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    val connectivityObserver: ConnectivityObserver = AndroidConnectivityObserver(context)

    // Temporal hasta que exista una spec de autenticación.
    private val sessionProvider: SessionProvider = StaticSessionProvider(
        client = httpClient,
        baseUrl = BuildConfig.BACKEND_BASE_URL,
        email = BuildConfig.DEV_EMAIL,
        password = BuildConfig.DEV_PASSWORD,
    )

    val headerRepository: HeaderRepository = HeaderRepositoryImpl(
        remote = HeaderRemoteDataSource(httpClient, BuildConfig.BACKEND_BASE_URL),
        local = HeaderLocalDataSource(headerCacheStore),
        sessionProvider = sessionProvider,
    )
}
