package com.rocioplata.familyfinaiapp.domain.header

import java.util.Locale

/** Primer carácter visible del nombre en mayúscula, o null si no hay nombre (RF-05). */
fun avatarInitial(displayName: String?): String? {
    val trimmed = displayName?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val firstCodePoint = trimmed.codePointAt(0)
    return String(Character.toChars(firstCodePoint)).uppercase(Locale.ROOT)
}
