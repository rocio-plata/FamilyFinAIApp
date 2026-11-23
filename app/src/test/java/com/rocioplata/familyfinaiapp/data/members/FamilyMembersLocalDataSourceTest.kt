package com.rocioplata.familyfinaiapp.data.members

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rocioplata.familyfinaiapp.data.header.Cached
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FamilyMembersLocalDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val members = listOf(
        FamilyMember("u-1", "Valentina Plata", FamilyRole.OWNER),
        FamilyMember("u-2", null, FamilyRole.MEMBER),
        FamilyMember("u-3", "", FamilyRole.MEMBER),
    )

    private fun TestScope.newStore(file: File = File(folder.root, "members_cache.preferences_pb")) =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { file }

    @Test
    fun guardarYLeer_devuelveLoMismo() = runTest {
        val source = FamilyMembersLocalDataSource(newStore())

        source.save("f-1", "u-1", 1_000L, members)

        assertEquals(Cached("u-1", 1_000L, members), source.read("f-1"))
    }

    @Test
    fun sinDatos_devuelveNulo() = runTest {
        assertNull(FamilyMembersLocalDataSource(newStore()).read("f-1"))
    }

    @Test
    fun lasFamiliasNoSeMezclan() = runTest {
        val source = FamilyMembersLocalDataSource(newStore())
        val other = listOf(FamilyMember("u-9", "Otra", FamilyRole.MEMBER))

        source.save("f-1", "u-1", 1L, members)
        source.save("f-2", "u-1", 2L, other)

        assertEquals(members, source.read("f-1")?.value)
        assertEquals(other, source.read("f-2")?.value)
    }

    @Test
    fun quitar_descartaSoloEsaFamilia() = runTest {
        val source = FamilyMembersLocalDataSource(newStore())
        source.save("f-1", "u-1", 1L, members)
        source.save("f-2", "u-1", 1L, members)

        source.remove("f-1")

        assertNull(source.read("f-1"))
        assertEquals(members, source.read("f-2")?.value)
    }

    @Test
    fun borrar_dejaTodoVacio() = runTest {
        val source = FamilyMembersLocalDataSource(newStore())
        source.save("f-1", "u-1", 1L, members)
        source.save("f-2", "u-1", 1L, members)

        source.clear()

        assertNull(source.read("f-1"))
        assertNull(source.read("f-2"))
    }

    @Test
    fun versionDesconocida_sinDatos() = runTest {
        val store = newStore()
        store.edit {
            it[stringPreferencesKey("members_f-1")] =
                """{"schemaVersion":99,"userId":"u-1","savedAtMillis":1,"value":[{"userId":"u-1","role":"OWNER"}]}"""
        }

        assertNull(FamilyMembersLocalDataSource(store).read("f-1"))
    }

    @Test
    fun jsonIlegible_sinDatos() = runTest {
        val store = newStore()
        store.edit { it[stringPreferencesKey("members_f-1")] = "no es json" }

        assertNull(FamilyMembersLocalDataSource(store).read("f-1"))
    }

    @Test
    fun rolDesconocido_sinDatos() = runTest {
        val store = newStore()
        store.edit {
            it[stringPreferencesKey("members_f-1")] =
                """{"schemaVersion":1,"userId":"u-1","savedAtMillis":1,"value":[{"userId":"u-1","role":"ADMIN"}]}"""
        }

        assertNull(FamilyMembersLocalDataSource(store).read("f-1"))
    }

    @Test
    fun noGuardaCorreosNiOtrosDatos() = runTest {
        val store = newStore()
        FamilyMembersLocalDataSource(store).save("f-1", "u-1", 1L, members)

        val raw = store.data.first()[stringPreferencesKey("members_f-1")].orEmpty()

        assertFalse(raw.contains("email"))
        assertFalse(raw.contains("@"))
        assertEquals(true, raw.contains("Valentina Plata"))
    }

    @Test
    fun conservarFamilias_borraLasQueNoEstan_yConservaLasDemas() = runTest {
        val source = FamilyMembersLocalDataSource(newStore())
        source.save("f-1", "u-1", 1L, members)
        source.save("f-2", "u-1", 2L, members)
        source.save("f-3", "u-1", 3L, members)

        source.retainFamilies(setOf("f-2", "f-3", "f-9"))

        assertNull(source.read("f-1"))
        assertEquals(Cached("u-1", 2L, members), source.read("f-2"))
        assertEquals(Cached("u-1", 3L, members), source.read("f-3"))
    }

    @Test
    fun conservarFamilias_conjuntoVacio_borraTodasLasFamilias() = runTest {
        val source = FamilyMembersLocalDataSource(newStore())
        source.save("f-1", "u-1", 1L, members)
        source.save("f-2", "u-1", 2L, members)

        source.retainFamilies(emptySet())

        assertNull(source.read("f-1"))
        assertNull(source.read("f-2"))
    }

    @Test
    fun conservarFamilias_noTocaClavesQueNoSonDeUnaFamilia() = runTest {
        val store = newStore()
        val source = FamilyMembersLocalDataSource(store)
        source.save("f-1", "u-1", 1L, members)
        store.edit { it[stringPreferencesKey("otra_clave")] = "x" }

        source.retainFamilies(emptySet())

        assertEquals("x", store.data.first()[stringPreferencesKey("otra_clave")])
    }
}
