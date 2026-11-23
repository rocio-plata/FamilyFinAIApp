package com.rocioplata.familyfinaiapp.data.movements

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rocioplata.familyfinaiapp.data.header.Cached
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class MovementsLocalDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val november = YearMonth.of(2026, 11)
    private val october = YearMonth.of(2026, 10)

    private val movements = listOf(
        Movement(
            id = "i-1",
            type = MovementType.EXPENSE,
            amount = Money(25990, "CLP"),
            categoryId = "c-1",
            tagId = "t-1",
            paymentMethodId = "m-1",
            title = "Súper",
            note = "nota",
            date = LocalDate.of(2026, 11, 10),
            recordedBy = "u-1",
            createdAt = Instant.parse("2026-11-10T15:05:00.123Z"),
        ),
        Movement(
            id = "i-2",
            type = MovementType.INCOME,
            amount = Money(150000, "USD"),
            categoryId = "c-2",
            tagId = null,
            paymentMethodId = null,
            title = "Sueldo",
            note = null,
            date = LocalDate.of(2026, 11, 1),
            recordedBy = "u-2",
            createdAt = Instant.parse("2026-11-01T09:00:00Z"),
        ),
    )

    private fun TestScope.newStore(file: File = File(folder.root, "movements_cache.preferences_pb")) =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { file }

    @Test
    fun guardarYLeerUnMes_devuelveLoMismoConTodosLosCampos() = runTest {
        val source = MovementsLocalDataSource(newStore())

        source.save("f-1", november, "u-1", 1_000L, movements)

        assertEquals(Cached("u-1", 1_000L, movements), source.read("f-1", november))
    }

    @Test
    fun mesVacio_seGuardaYSeLeeComoListaVacia() = runTest {
        val source = MovementsLocalDataSource(newStore())

        source.save("f-1", november, "u-1", 1L, emptyList())

        assertEquals(emptyList<Movement>(), source.read("f-1", november)?.value)
    }

    @Test
    fun sinDatos_devuelveNulo() = runTest {
        assertNull(MovementsLocalDataSource(newStore()).read("f-1", november))
    }

    @Test
    fun losMesesNoSeMezclan() = runTest {
        val source = MovementsLocalDataSource(newStore())

        source.save("f-1", november, "u-1", 1L, movements)
        source.save("f-1", october, "u-1", 2L, movements.take(1))

        assertEquals(movements, source.read("f-1", november)?.value)
        assertEquals(movements.take(1), source.read("f-1", october)?.value)
        assertEquals(2L, source.read("f-1", october)?.savedAtMillis)
    }

    @Test
    fun lasFamiliasNoSeMezclan() = runTest {
        val source = MovementsLocalDataSource(newStore())

        source.save("f-1", november, "u-1", 1L, movements)

        assertNull(source.read("f-2", november))
    }

    @Test
    fun quitar_descartaSoloEseMesDeEsaFamilia() = runTest {
        val source = MovementsLocalDataSource(newStore())
        source.save("f-1", november, "u-1", 1L, movements)
        source.save("f-1", october, "u-1", 1L, movements)
        source.save("f-2", november, "u-1", 1L, movements)

        source.remove("f-1", november)

        assertNull(source.read("f-1", november))
        assertEquals(movements, source.read("f-1", october)?.value)
        assertEquals(movements, source.read("f-2", november)?.value)
    }

    @Test
    fun versionDesconocida_sinDatos() = runTest {
        val store = newStore()
        store.edit {
            it[stringPreferencesKey("movements_f-1_2026-11")] =
                """{"schemaVersion":99,"userId":"u-1","savedAtMillis":1,"value":[]}"""
        }

        assertNull(MovementsLocalDataSource(store).read("f-1", november))
    }

    @Test
    fun jsonIlegible_sinDatos() = runTest {
        val store = newStore()
        store.edit { it[stringPreferencesKey("movements_f-1_2026-11")] = "no es json" }

        assertNull(MovementsLocalDataSource(store).read("f-1", november))
    }

    @Test
    fun tipoMonedaOFechaInvalidos_sinDatos() = runTest {
        val store = newStore()
        val source = MovementsLocalDataSource(store)
        val base = """{"id":"i","type":"%s","amountMinor":1,"currency":"%s","categoryId":"c","title":"t",
            |"date":"%s","recordedBy":"u","createdAtMillis":1}""".trimMargin()
        for ((type, currency, date) in listOf(
            Triple("TRANSFER", "CLP", "2026-11-10"),
            Triple("EXPENSE", "ZZZZ", "2026-11-10"),
            Triple("EXPENSE", "CLP", "ayer"),
        )) {
            store.edit {
                it[stringPreferencesKey("movements_f-1_2026-11")] =
                    """{"schemaVersion":1,"userId":"u-1","savedAtMillis":1,"value":[${base.format(type, currency, date)}]}"""
            }
            assertNull("$type $currency $date", source.read("f-1", november))
        }
    }

    @Test
    fun ultimoMedioDePago_sinGuardar_esNulo() = runTest {
        assertNull(MovementsLocalDataSource(newStore()).lastUsedPaymentMethodId("u-1", "f-1"))
    }

    @Test
    fun ultimoMedioDePago_seGuardaPorUsuarioYFamilia() = runTest {
        val source = MovementsLocalDataSource(newStore())

        source.saveLastUsedPaymentMethodId("u-1", "f-1", "m-1")
        source.saveLastUsedPaymentMethodId("u-1", "f-2", "m-2")
        source.saveLastUsedPaymentMethodId("u-2", "f-1", "m-3")

        assertEquals("m-1", source.lastUsedPaymentMethodId("u-1", "f-1"))
        assertEquals("m-2", source.lastUsedPaymentMethodId("u-1", "f-2"))
        assertEquals("m-3", source.lastUsedPaymentMethodId("u-2", "f-1"))
        assertNull(source.lastUsedPaymentMethodId("u-2", "f-2"))
    }

    @Test
    fun ultimoMedioDePago_seSobrescribe() = runTest {
        val source = MovementsLocalDataSource(newStore())

        source.saveLastUsedPaymentMethodId("u-1", "f-1", "m-1")
        source.saveLastUsedPaymentMethodId("u-1", "f-1", "m-9")

        assertEquals("m-9", source.lastUsedPaymentMethodId("u-1", "f-1"))
    }

    @Test
    fun borrar_dejaTodoVacio() = runTest {
        val source = MovementsLocalDataSource(newStore())
        source.save("f-1", november, "u-1", 1L, movements)
        source.saveLastUsedPaymentMethodId("u-1", "f-1", "m-1")

        source.clear()

        assertNull(source.read("f-1", november))
        assertNull(source.lastUsedPaymentMethodId("u-1", "f-1"))
    }

    @Test
    fun conservarFamilias_borraMesesYUltimoMedioDeLaPerdida_yConservaLasDemas() = runTest {
        val source = MovementsLocalDataSource(newStore())
        source.save("f-1", november, "u-1", 1L, movements)
        source.save("f-1", october, "u-1", 1L, movements)
        source.save("f-2", november, "u-1", 2L, movements)
        source.saveLastUsedPaymentMethodId("u-1", "f-1", "m-1")
        source.saveLastUsedPaymentMethodId("u-2", "f-1", "m-3")
        source.saveLastUsedPaymentMethodId("u-1", "f-2", "m-2")

        source.retainFamilies(setOf("f-2"))

        assertNull(source.read("f-1", november))
        assertNull(source.read("f-1", october))
        assertEquals(Cached("u-1", 2L, movements), source.read("f-2", november))
        assertNull(source.lastUsedPaymentMethodId("u-1", "f-1"))
        assertNull(source.lastUsedPaymentMethodId("u-2", "f-1"))
        assertEquals("m-2", source.lastUsedPaymentMethodId("u-1", "f-2"))
    }

    @Test
    fun conservarFamilias_conjuntoVacio_borraTodasLasFamilias() = runTest {
        val source = MovementsLocalDataSource(newStore())
        source.save("f-1", november, "u-1", 1L, movements)
        source.saveLastUsedPaymentMethodId("u-1", "f-2", "m-2")

        source.retainFamilies(emptySet())

        assertNull(source.read("f-1", november))
        assertNull(source.lastUsedPaymentMethodId("u-1", "f-2"))
    }

    @Test
    fun conservarFamilias_conIdentificadoresDeVerdad_noConfundeFamiliaYUsuario() = runTest {
        val source = MovementsLocalDataSource(newStore())
        val keep = "7d0d9a1e-6c54-4b01-90e6-d701748f0851"
        val lost = "a1a2a3a4-5555-4666-8777-123456789abc"
        val user = "d290f1ee-6c54-4b01-90e6-d701748f0851"
        source.save(keep, november, user, 1L, movements)
        source.save(lost, november, user, 1L, movements)
        source.saveLastUsedPaymentMethodId(user, keep, "m-1")
        source.saveLastUsedPaymentMethodId(user, lost, "m-2")

        source.retainFamilies(setOf(keep))

        assertEquals(Cached(user, 1L, movements), source.read(keep, november))
        assertNull(source.read(lost, november))
        assertEquals("m-1", source.lastUsedPaymentMethodId(user, keep))
        assertNull(source.lastUsedPaymentMethodId(user, lost))
    }

    @Test
    fun conservarFamilias_noTocaClavesQueNoSonDeUnaFamilia() = runTest {
        val store = newStore()
        val source = MovementsLocalDataSource(store)
        store.edit { it[stringPreferencesKey("otra_clave")] = "x" }

        source.retainFamilies(emptySet())

        assertEquals("x", store.data.first()[stringPreferencesKey("otra_clave")])
    }
}
