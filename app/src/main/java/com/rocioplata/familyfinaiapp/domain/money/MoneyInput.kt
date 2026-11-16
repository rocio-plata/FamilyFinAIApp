package com.rocioplata.familyfinaiapp.domain.money

/**
 * Texto del campo de monto del formulario (RF-05): decide qué se puede escribir y lo convierte a la unidad
 * menor y al revés, siempre con aritmética entera. El separador decimal puede ser coma o punto; se conserva
 * el que escribió el usuario. El formato de salida para mostrar es de la interfaz (`MoneyFormat`).
 */
object MoneyInput {
    /** Dígitos máximos de la parte entera (el entero en la unidad menor sigue cabiendo en un `Long`). */
    const val MAX_INTEGER_DIGITS = 12

    private fun isSeparator(c: Char) = c == ',' || c == '.'

    /**
     * Deja solo lo escribible: dígitos, como mucho un separador (y solo si la moneda tiene decimales), la
     * parte entera con [MAX_INTEGER_DIGITS] dígitos como máximo y la fraccionaria con [fractionDigits].
     * Los ceros a la izquierda sobrantes se quitan ("007" → "7", "00" → "0").
     */
    fun filter(raw: String, fractionDigits: Int): String {
        val integer = StringBuilder()
        val fraction = StringBuilder()
        var separator: Char? = null
        for (c in raw) {
            when {
                c in '0'..'9' -> if (separator == null) integer.append(c) else fraction.append(c)
                isSeparator(c) && fractionDigits > 0 && separator == null -> separator = c
            }
        }
        val trimmed = integer.toString().trimStart('0')
        // Solo ceros ("000") queda un "0"; sin ningún dígito entero queda vacío (",5" sigue siendo editable).
        val integerPart = (if (trimmed.isEmpty() && integer.isNotEmpty()) "0" else trimmed).take(MAX_INTEGER_DIGITS)
        val fractionPart = fraction.take(fractionDigits)
        return if (separator == null) integerPart else "$integerPart$separator$fractionPart"
    }

    /**
     * Texto → unidad menor, o `null` si no hay un monto (vacío o solo el separador). La parte fraccionaria
     * se completa con ceros ("12,5" en USD son 1250) y una parte entera vacía cuenta como cero (",5").
     * Devuelve `null` también si el texto no es un número válido para esos decimales.
     */
    fun parse(text: String, fractionDigits: Int): Long? {
        val filtered = filter(text, fractionDigits)
        val separatorAt = filtered.indexOfFirst(::isSeparator)
        val integer = if (separatorAt < 0) filtered else filtered.substring(0, separatorAt)
        val fraction = if (separatorAt < 0) "" else filtered.substring(separatorAt + 1)
        if (integer.isEmpty() && fraction.isEmpty()) return null
        val integerValue = if (integer.isEmpty()) 0L else integer.toLong()
        var scale = 1L
        repeat(fractionDigits) { scale *= 10 }
        val fractionValue = if (fraction.isEmpty()) 0L else fraction.padEnd(fractionDigits, '0').toLong()
        return integerValue * scale + fractionValue
    }

    /** Unidad menor → texto editable, sin separador de miles: 125000 con 2 decimales y coma → "1250,00". */
    fun format(amountMinor: Long, fractionDigits: Int, separator: Char = ','): String {
        require(amountMinor >= 0) { "El monto de un campo no es negativo" }
        require(isSeparator(separator)) { "Separador no válido: $separator" }
        if (fractionDigits == 0) return amountMinor.toString()
        val digits = amountMinor.toString().padStart(fractionDigits + 1, '0')
        val cut = digits.length - fractionDigits
        return digits.substring(0, cut) + separator + digits.substring(cut)
    }
}
