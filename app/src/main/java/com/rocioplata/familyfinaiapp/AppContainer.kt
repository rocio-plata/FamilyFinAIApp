package com.rocioplata.familyfinaiapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.rocioplata.familyfinaiapp.data.auth.AuthRemoteDataSource
import com.rocioplata.familyfinaiapp.data.auth.AuthRepositoryImpl
import com.rocioplata.familyfinaiapp.data.categories.CategoriesLocalDataSource
import com.rocioplata.familyfinaiapp.data.categories.CategoriesRemoteDataSource
import com.rocioplata.familyfinaiapp.data.categories.CategoriesRepositoryImpl
import com.rocioplata.familyfinaiapp.data.header.HeaderLocalDataSource
import com.rocioplata.familyfinaiapp.data.header.HeaderRemoteDataSource
import com.rocioplata.familyfinaiapp.data.header.HeaderRepositoryImpl
import com.rocioplata.familyfinaiapp.data.members.FamilyMembersLocalDataSource
import com.rocioplata.familyfinaiapp.data.members.FamilyMembersRemoteDataSource
import com.rocioplata.familyfinaiapp.data.members.FamilyMembersRepositoryImpl
import com.rocioplata.familyfinaiapp.data.movements.MovementsLocalDataSource
import com.rocioplata.familyfinaiapp.data.movements.MovementsRemoteDataSource
import com.rocioplata.familyfinaiapp.data.movements.MovementsRepositoryImpl
import com.rocioplata.familyfinaiapp.data.network.AndroidConnectivityObserver
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.data.paymentmethods.PaymentMethodsLocalDataSource
import com.rocioplata.familyfinaiapp.data.paymentmethods.PaymentMethodsRemoteDataSource
import com.rocioplata.familyfinaiapp.data.paymentmethods.PaymentMethodsRepositoryImpl
import com.rocioplata.familyfinaiapp.data.session.EncryptedSessionStore
import com.rocioplata.familyfinaiapp.data.session.KeystoreSessionCipher
import com.rocioplata.familyfinaiapp.domain.auth.AuthRepository
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersRepository
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
import com.rocioplata.familyfinaiapp.domain.movements.MovementsRepository
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsRepository
import com.rocioplata.familyfinaiapp.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private val Context.headerCacheStore: DataStore<Preferences> by preferencesDataStore(name = HEADER_CACHE_NAME)

// Los nombres deben coincidir con las exclusiones de backups (backup_rules.xml, data_extraction_rules.xml).
private val Context.categoriesCacheStore: DataStore<Preferences> by preferencesDataStore(name = CATEGORIES_CACHE_NAME)

private val Context.paymentMethodsCacheStore: DataStore<Preferences> by preferencesDataStore(name = PAYMENT_METHODS_CACHE_NAME)

private val Context.movementsCacheStore: DataStore<Preferences> by preferencesDataStore(name = MOVEMENTS_CACHE_NAME)

private val Context.membersCacheStore: DataStore<Preferences> by preferencesDataStore(name = MEMBERS_CACHE_NAME)

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = SESSION_STORE_NAME)

const val HEADER_CACHE_NAME = "header_cache"
const val CATEGORIES_CACHE_NAME = "categories_cache"
const val PAYMENT_METHODS_CACHE_NAME = "payment_methods_cache"
const val MOVEMENTS_CACHE_NAME = "movements_cache"
const val MEMBERS_CACHE_NAME = "members_cache"
const val SESSION_STORE_NAME = "session"
private const val CONNECT_TIMEOUT_MS = 10_000L
private const val REQUEST_TIMEOUT_MS = 15_000L

class AppContainer(context: Context) {
    val headerCacheStore: DataStore<Preferences> = context.applicationContext.headerCacheStore
    val categoriesCacheStore: DataStore<Preferences> = context.applicationContext.categoriesCacheStore
    val paymentMethodsCacheStore: DataStore<Preferences> = context.applicationContext.paymentMethodsCacheStore
    val movementsCacheStore: DataStore<Preferences> = context.applicationContext.movementsCacheStore
    val membersCacheStore: DataStore<Preferences> = context.applicationContext.membersCacheStore

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
            onSessionEnded = {
                headerRepository.clear()
                categoriesRepository.clear()
                paymentMethodsRepository.clear()
                movementsRepository.clear()
                membersRepository.clear()
            },
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

    val categoriesRepository: CategoriesRepository by lazy {
        CategoriesRepositoryImpl(
            remote = CategoriesRemoteDataSource(apiClient, BuildConfig.BACKEND_BASE_URL),
            local = CategoriesLocalDataSource(categoriesCacheStore),
            sessionProvider = sessionManager,
            connectivity = connectivityObserver,
        )
    }

    val paymentMethodsRepository: PaymentMethodsRepository by lazy {
        PaymentMethodsRepositoryImpl(
            remote = PaymentMethodsRemoteDataSource(apiClient, BuildConfig.BACKEND_BASE_URL),
            local = PaymentMethodsLocalDataSource(paymentMethodsCacheStore),
            sessionProvider = sessionManager,
            connectivity = connectivityObserver,
        )
    }

    /** Fechas de los movimientos en la zona del dispositivo (RF-16). */
    val movementDates = MovementDates()

    val movementsRepository: MovementsRepository by lazy {
        MovementsRepositoryImpl(
            remote = MovementsRemoteDataSource(apiClient, BuildConfig.BACKEND_BASE_URL, movementDates),
            local = MovementsLocalDataSource(movementsCacheStore),
            sessionProvider = sessionManager,
            connectivity = connectivityObserver,
            dates = movementDates,
        )
    }

    val membersRepository: FamilyMembersRepository by lazy {
        FamilyMembersRepositoryImpl(
            remote = FamilyMembersRemoteDataSource(apiClient, BuildConfig.BACKEND_BASE_URL),
            local = FamilyMembersLocalDataSource(membersCacheStore),
            sessionProvider = sessionManager,
        )
    }
}
