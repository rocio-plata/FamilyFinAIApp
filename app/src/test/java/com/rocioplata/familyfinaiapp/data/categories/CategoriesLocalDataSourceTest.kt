package com.rocioplata.familyfinaiapp.data.categories

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rocioplata.familyfinaiapp.data.header.Cached
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CategoriesLocalDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val categories = listOf(
        Category(
            "c-1", CategoryType.EXPENSE, "Alimentación", "shopping_cart", CategoryStatus.ACTIVE,
            listOf(Tag("t-1", "Súper", TagStatus.ACTIVE, 0), Tag("t-2", "Viejo", TagStatus.DEPRECATED, 1)),
        ),
        Category("c-2", CategoryType.INCOME, "Sueldo", null, CategoryStatus.DEPRECATED, emptyList()),
    )

    private fun TestScope.newStore(file: File = File(folder.root, "categories_cache.preferences_pb")) =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { file }

    private val keyF1 = stringPreferencesKey("categories_f-1")

    @Test
    fun guardarYLeer_devuelveLoMismo() = runTest {
        val source = CategoriesLocalDataSource(newStore())

        source.save("f-1", "u-1", 1_000L, categories)

        assertEquals(Cached("u-1", 1_000L, categories), source.read("f-1"))
    }

    @Test
    fun listaVacia_seGuardaYSeLeeComoVacia_noComoSinDatos() = runTest {
        val source = CategoriesLocalDataSource(newStore())

        source.save("f-1", "u-1", 1L, emptyList())

        assertEquals(Cached("u-1", 1L, emptyList<Category>()), source.read("f-1"))
    }

    @Test
    fun sinDatos_devuelveNulo() = runTest {
        assertNull(CategoriesLocalDataSource(newStore()).read("f-1"))
    }

    @Test
    fun dosFamilias_seGuardanDeFormaIndependiente() = runTest {
        val source = CategoriesLocalDataSource(newStore())
        val other = listOf(Category("c-9", CategoryType.EXPENSE, "Otra", "home", CategoryStatus.ACTIVE, emptyList()))

        source.save("f-1", "u-1", 1L, categories)
        source.save("f-2", "u-1", 2L, other)

        assertEquals(Cached("u-1", 1L, categories), source.read("f-1"))
        assertEquals(Cached("u-1", 2L, other), source.read("f-2"))
        assertNull(source.read("f-3"))
    }

    @Test
    fun guardarDeNuevo_reemplazaLoAnteriorDeEsaFamilia() = runTest {
        val source = CategoriesLocalDataSource(newStore())
        source.save("f-1", "u-1", 1L, categories)

        source.save("f-1", "u-1", 5L, emptyList())

        assertEquals(Cached("u-1", 5L, emptyList<Category>()), source.read("f-1"))
    }

    @Test
    fun conservaElUsuarioParaQueElRepositorioDecida()  = runTest {
        val source = CategoriesLocalDataSource(newStore())

        source.save("f-1", "u-2", 7L, categories)

        assertEquals("u-2", source.read("f-1")?.userId)
    }

    @Test
    fun versionDeEsquemaDesconocida_seDescartaComoSinDatos() = runTest {
        val store = newStore()
        store.edit { it[keyF1] = """{"schemaVersion":99,"userId":"u-1","savedAtMillis":1,"value":[]}""" }

        assertNull(CategoriesLocalDataSource(store).read("f-1"))
    }

    @Test
    fun jsonIlegible_seDescartaComoSinDatos() = runTest {
        val store = newStore()
        store.edit { it[keyF1] = "no es json" }

        assertNull(CategoriesLocalDataSource(store).read("f-1"))
    }

    @Test
    fun formaInesperada_seDescartaComoSinDatos() = runTest {
        val store = newStore()
        store.edit { it[keyF1] = """{"schemaVersion":1,"userId":"u-1","savedAtMillis":1,"value":"no es una lista"}""" }

        assertNull(CategoriesLocalDataSource(store).read("f-1"))
    }

    @Test
    fun valorDesconocidoGuardado_seDescartaLaListaCompleta() = runTest {
        val store = newStore()
        val unknownType =
            """{"schemaVersion":1,"userId":"u-1","savedAtMillis":1,"value":[{"id":"c","type":"OTRO","name":"N","status":"ACTIVE","tags":[]}]}"""
        val unknownStatus =
            """{"schemaVersion":1,"userId":"u-1","savedAtMillis":1,"value":[{"id":"c","type":"EXPENSE","name":"N","status":"???","tags":[]}]}"""
        val unknownTagStatus =
            """{"schemaVersion":1,"userId":"u-1","savedAtMillis":1,"value":[{"id":"c","type":"EXPENSE","name":"N","status":"ACTIVE","tags":[{"id":"t","name":"x","status":"???","displayOrder":0}]}]}"""
        listOf(unknownType, unknownStatus, unknownTagStatus).forEach { raw ->
            store.edit { it[keyF1] = raw }
            assertNull(raw, CategoriesLocalDataSource(store).read("f-1"))
        }
    }

    @Test
    fun camposNuevosEnElJson_seIgnoran() = runTest {
        val store = newStore()
        store.edit {
            it[keyF1] =
                """{"schemaVersion":1,"userId":"u-1","savedAtMillis":1,"extra":true,"value":[{"id":"c","type":"EXPENSE","name":"N","status":"ACTIVE","color":"x","tags":[]}]}"""
        }

        assertEquals(
            Cached("u-1", 1L, listOf(Category("c", CategoryType.EXPENSE, "N", null, CategoryStatus.ACTIVE, emptyList()))),
            CategoriesLocalDataSource(store).read("f-1"),
        )
    }

    @Test
    fun borrar_vaciaTodasLasFamilias() = runTest {
        val source = CategoriesLocalDataSource(newStore())
        source.save("f-1", "u-1", 1L, categories)
        source.save("f-2", "u-1", 1L, categories)

        source.clear()

        assertNull(source.read("f-1"))
        assertNull(source.read("f-2"))
    }

    @Test
    fun descartarUnaFamilia_noAlteraLasDemas() = runTest {
        val source = CategoriesLocalDataSource(newStore())
        source.save("f-1", "u-1", 1L, categories)
        source.save("f-2", "u-1", 2L, categories)

        source.remove("f-1")

        assertNull(source.read("f-1"))
        assertEquals(Cached("u-1", 2L, categories), source.read("f-2"))
    }
}
