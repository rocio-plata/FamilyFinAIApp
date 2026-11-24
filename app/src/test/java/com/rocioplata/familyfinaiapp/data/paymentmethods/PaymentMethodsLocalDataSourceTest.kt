package com.rocioplata.familyfinaiapp.data.paymentmethods

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rocioplata.familyfinaiapp.data.header.Cached
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PaymentMethodsLocalDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val methods = listOf(
        PaymentMethod("m-2", "Transferencia", PaymentMethodStatus.ACTIVE),
        PaymentMethod("m-1", "Efectivo", PaymentMethodStatus.ACTIVE),
        PaymentMethod("m-3", "Tarjeta vieja", PaymentMethodStatus.DEPRECATED),
    )

    private fun TestScope.newStore(file: File = File(folder.root, "payment_methods_cache.preferences_pb")) =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { file }

    private val keyF1 = stringPreferencesKey("payment_methods_f-1")

    @Test
    fun guardarYLeer_devuelveLoMismoYConservaElOrden() = runTest {
        val source = PaymentMethodsLocalDataSource(newStore())

        source.save("f-1", "u-1", 1_000L, methods)

        assertEquals(Cached("u-1", 1_000L, methods), source.read("f-1"))
    }

    @Test
    fun listaVacia_seGuardaYSeLeeComoVacia_noComoSinDatos() = runTest {
        val source = PaymentMethodsLocalDataSource(newStore())

        source.save("f-1", "u-1", 1L, emptyList())

        assertEquals(Cached("u-1", 1L, emptyList<PaymentMethod>()), source.read("f-1"))
    }

    @Test
    fun sinDatos_devuelveNulo() = runTest {
        assertNull(PaymentMethodsLocalDataSource(newStore()).read("f-1"))
    }

    @Test
    fun dosFamilias_seGuardanDeFormaIndependiente() = runTest {
        val source = PaymentMethodsLocalDataSource(newStore())
        val other = listOf(PaymentMethod("m-9", "Otra", PaymentMethodStatus.ACTIVE))

        source.save("f-1", "u-1", 1L, methods)
        source.save("f-2", "u-1", 2L, other)

        assertEquals(Cached("u-1", 1L, methods), source.read("f-1"))
        assertEquals(Cached("u-1", 2L, other), source.read("f-2"))
        assertNull(source.read("f-3"))
    }

    @Test
    fun guardarDeNuevo_reemplazaLoAnteriorDeEsaFamilia() = runTest {
        val source = PaymentMethodsLocalDataSource(newStore())
        source.save("f-1", "u-1", 1L, methods)

        source.save("f-1", "u-1", 5L, emptyList())

        assertEquals(Cached("u-1", 5L, emptyList<PaymentMethod>()), source.read("f-1"))
    }

    @Test
    fun conservaElUsuarioParaQueElRepositorioDecida() = runTest {
        val source = PaymentMethodsLocalDataSource(newStore())

        source.save("f-1", "u-2", 7L, methods)

        assertEquals("u-2", source.read("f-1")?.userId)
    }

    @Test
    fun versionDeEsquemaDesconocida_seDescartaComoSinDatos() = runTest {
        val store = newStore()
        store.edit { it[keyF1] = """{"schemaVersion":99,"userId":"u-1","savedAtMillis":1,"value":[]}""" }

        assertNull(PaymentMethodsLocalDataSource(store).read("f-1"))
    }

    @Test
    fun jsonIlegible_seDescartaComoSinDatos() = runTest {
        val store = newStore()
        store.edit { it[keyF1] = "no es json" }

        assertNull(PaymentMethodsLocalDataSource(store).read("f-1"))
    }

    @Test
    fun formaInesperada_seDescartaComoSinDatos() = runTest {
        val store = newStore()
        store.edit { it[keyF1] = """{"schemaVersion":1,"userId":"u-1","savedAtMillis":1,"value":"no es una lista"}""" }

        assertNull(PaymentMethodsLocalDataSource(store).read("f-1"))
    }

    @Test
    fun estadoDesconocidoGuardado_seDescartaLaListaCompleta() = runTest {
        val store = newStore()
        store.edit {
            it[keyF1] =
                """{"schemaVersion":1,"userId":"u-1","savedAtMillis":1,"value":[{"id":"m","name":"N","status":"???"}]}"""
        }

        assertNull(PaymentMethodsLocalDataSource(store).read("f-1"))
    }

    @Test
    fun camposNuevosEnElJson_seIgnoran() = runTest {
        val store = newStore()
        store.edit {
            it[keyF1] =
                """{"schemaVersion":1,"userId":"u-1","savedAtMillis":1,"extra":true,"value":[{"id":"m","name":"N","status":"ACTIVE","color":"x"}]}"""
        }

        assertEquals(
            Cached("u-1", 1L, listOf(PaymentMethod("m", "N", PaymentMethodStatus.ACTIVE))),
            PaymentMethodsLocalDataSource(store).read("f-1"),
        )
    }

    @Test
    fun borrar_vaciaTodasLasFamilias() = runTest {
        val source = PaymentMethodsLocalDataSource(newStore())
        source.save("f-1", "u-1", 1L, methods)
        source.save("f-2", "u-1", 1L, methods)

        source.clear()

        assertNull(source.read("f-1"))
        assertNull(source.read("f-2"))
    }

    @Test
    fun descartarUnaFamilia_noAlteraLasDemas() = runTest {
        val source = PaymentMethodsLocalDataSource(newStore())
        source.save("f-1", "u-1", 1L, methods)
        source.save("f-2", "u-1", 2L, methods)

        source.remove("f-1")

        assertNull(source.read("f-1"))
        assertEquals(Cached("u-1", 2L, methods), source.read("f-2"))
    }

    @Test
    fun conservarFamilias_borraLasQueNoEstan_yConservaLasDemas() = runTest {
        val source = PaymentMethodsLocalDataSource(newStore())
        source.save("f-1", "u-1", 1L, methods)
        source.save("f-2", "u-1", 2L, methods)
        source.save("f-3", "u-1", 3L, methods)

        source.retainFamilies(setOf("f-2", "f-3", "f-9"))

        assertNull(source.read("f-1"))
        assertEquals(Cached("u-1", 2L, methods), source.read("f-2"))
        assertEquals(Cached("u-1", 3L, methods), source.read("f-3"))
    }

    @Test
    fun conservarFamilias_conjuntoVacio_borraTodasLasFamilias() = runTest {
        val source = PaymentMethodsLocalDataSource(newStore())
        source.save("f-1", "u-1", 1L, methods)
        source.save("f-2", "u-1", 2L, methods)

        source.retainFamilies(emptySet())

        assertNull(source.read("f-1"))
        assertNull(source.read("f-2"))
    }

    @Test
    fun conservarFamilias_noTocaClavesQueNoSonDeUnaFamilia() = runTest {
        val store = newStore()
        val source = PaymentMethodsLocalDataSource(store)
        source.save("f-1", "u-1", 1L, methods)
        store.edit { it[stringPreferencesKey("otra_clave")] = "x" }

        source.retainFamilies(emptySet())

        assertEquals("x", store.data.first()[stringPreferencesKey("otra_clave")])
    }
}
