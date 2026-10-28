package com.rocioplata.familyfinaiapp.data.header

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HeaderLocalDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val families = listOf(
        Family("f-1", "Familia Plata", FamilyRole.OWNER),
        Family("f-2", "Depto", FamilyRole.MEMBER),
    )

    private fun TestScope.newStore(file: File = File(folder.root, "header_cache.preferences_pb")) =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { file }

    @Test
    fun guardarYLeer_devuelveLoMismo() = runTest {
        val source = HeaderLocalDataSource(newStore())

        source.saveFamilies("u-1", 1_000L, families)
        source.savePendingInvitations("u-1", 2_000L, 3)
        source.saveDisplayName("u-1", 3_000L, "rocío")

        assertEquals(Cached("u-1", 1_000L, families), source.readFamilies())
        assertEquals(Cached("u-1", 2_000L, 3), source.readPendingInvitations())
        assertEquals(Cached("u-1", 3_000L, "rocío"), source.readDisplayName())
    }

    @Test
    fun sinDatos_devuelveNulo() = runTest {
        val source = HeaderLocalDataSource(newStore())

        assertNull(source.readFamilies())
        assertNull(source.readPendingInvitations())
        assertNull(source.readDisplayName())
    }

    @Test
    fun borrar_dejaTodoVacio() = runTest {
        val source = HeaderLocalDataSource(newStore())
        source.saveFamilies("u-1", 1L, families)
        source.savePendingInvitations("u-1", 1L, 1)
        source.saveDisplayName("u-1", 1L, "A")

        source.clear()

        assertNull(source.readFamilies())
        assertNull(source.readPendingInvitations())
        assertNull(source.readDisplayName())
    }

    @Test
    fun guardarUnRecurso_noAlteraLosDemas() = runTest {
        val source = HeaderLocalDataSource(newStore())
        source.saveFamilies("u-1", 1L, families)

        source.savePendingInvitations("u-1", 2L, 5)

        assertEquals(Cached("u-1", 1L, families), source.readFamilies())
    }

    @Test
    fun versionDeEsquemaDesconocida_seDescartaComoSinDatos() = runTest {
        val store = newStore()
        store.edit {
            it[stringPreferencesKey("pending_invitations")] =
                """{"schemaVersion":99,"userId":"u-1","savedAtMillis":1,"value":3}"""
        }

        assertNull(HeaderLocalDataSource(store).readPendingInvitations())
    }

    @Test
    fun jsonIlegible_seDescartaComoSinDatos() = runTest {
        val store = newStore()
        store.edit { it[stringPreferencesKey("profile")] = "no es json" }

        assertNull(HeaderLocalDataSource(store).readDisplayName())
    }

    @Test
    fun rolDesconocidoGuardado_seDescartaLaListaCompleta() = runTest {
        val store = newStore()
        store.edit {
            it[stringPreferencesKey("families")] =
                """{"schemaVersion":1,"userId":"u-1","savedAtMillis":1,"value":[{"id":"f","name":"N","role":"ADMIN"}]}"""
        }

        assertNull(HeaderLocalDataSource(store).readFamilies())
    }
}
