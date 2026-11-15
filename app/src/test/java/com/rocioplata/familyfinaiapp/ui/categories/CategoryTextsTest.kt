package com.rocioplata.familyfinaiapp.ui.categories

import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CategoryTextsTest {
    @Test
    fun cadaFalloConMensajePropioTieneSuTexto() {
        assertEquals(R.string.categories_error_offline, CategoryFailure.Offline.messageRes())
        assertEquals(R.string.categories_error_network, CategoryFailure.Network.messageRes())
        assertEquals(R.string.categories_error_insufficient_role, CategoryFailure.InsufficientRole.messageRes())
        assertEquals(R.string.categories_error_category_not_active, CategoryFailure.CategoryNotActive.messageRes())
        assertEquals(R.string.categories_error_invalid_data, CategoryFailure.InvalidData.messageRes())
    }

    @Test
    fun elDuplicadoYElNoEncontradoTienenVarianteDeCategoriaYDeTag() {
        assertEquals(R.string.categories_error_duplicate_category, CategoryFailure.DuplicateName.messageRes())
        assertEquals(R.string.categories_error_duplicate_tag, CategoryFailure.DuplicateName.messageRes(forTag = true))
        assertEquals(R.string.categories_error_category_not_found, CategoryFailure.NotFound.messageRes())
        assertEquals(R.string.categories_error_tag_not_found, CategoryFailure.NotFound.messageRes(forTag = true))
    }

    @Test
    fun losFallosSinMensajePropioUsanElGenerico() {
        listOf(CategoryFailure.HasAssociatedItems, CategoryFailure.Unauthorized, CategoryFailure.Unknown).forEach {
            assertEquals(it.toString(), R.string.categories_error_generic, it.messageRes())
        }
    }

    @Test
    fun losMensajesDeLosFallosConMotivoDistintoSonDistintos() {
        val specific = listOf(
            CategoryFailure.Offline, CategoryFailure.Network, CategoryFailure.InsufficientRole,
            CategoryFailure.DuplicateName, CategoryFailure.NotFound, CategoryFailure.CategoryNotActive,
            CategoryFailure.InvalidData,
        ).map { it.messageRes() }
        assertEquals(specific.size, specific.toSet().size)
        assertNotEquals(R.string.categories_error_generic, specific.first())
        assertEquals(false, R.string.categories_error_generic in specific)
    }
}
