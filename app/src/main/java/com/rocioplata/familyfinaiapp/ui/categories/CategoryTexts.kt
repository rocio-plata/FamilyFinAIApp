package com.rocioplata.familyfinaiapp.ui.categories

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure

/**
 * Texto de un fallo de categoría o de tag (RF-08, RF-13, RF-21). [forTag] elige la variante de duplicado
 * y de «ya no existe». `HasAssociatedItems` no tiene mensaje propio porque se resuelve con un diálogo
 * (RF-13, RF-18) y `Unauthorized` lo resuelve la sesión (003); ambos caen en el mensaje genérico.
 */
@StringRes
fun CategoryFailure.messageRes(forTag: Boolean = false): Int = when (this) {
    CategoryFailure.Offline -> R.string.categories_error_offline
    CategoryFailure.Network -> R.string.categories_error_network
    CategoryFailure.InsufficientRole -> R.string.categories_error_insufficient_role
    CategoryFailure.DuplicateName ->
        if (forTag) R.string.categories_error_duplicate_tag else R.string.categories_error_duplicate_category
    CategoryFailure.NotFound ->
        if (forTag) R.string.categories_error_tag_not_found else R.string.categories_error_category_not_found
    CategoryFailure.CategoryNotActive -> R.string.categories_error_category_not_active
    CategoryFailure.InvalidData -> R.string.categories_error_invalid_data
    CategoryFailure.HasAssociatedItems,
    CategoryFailure.Unauthorized,
    CategoryFailure.Unknown,
    -> R.string.categories_error_generic
}

/** Los límites los pasa quien lo usa desde `CategoryValidation`, el único lugar donde se definen. */
@Composable
fun NameFieldError.message(maxLength: Int, forTag: Boolean = false): String = when (this) {
    NameFieldError.REQUIRED -> stringResource(R.string.categories_error_name_required)
    NameFieldError.TOO_LONG -> stringResource(R.string.categories_error_name_too_long, maxLength)
    NameFieldError.DUPLICATE -> stringResource(CategoryFailure.DuplicateName.messageRes(forTag))
}
