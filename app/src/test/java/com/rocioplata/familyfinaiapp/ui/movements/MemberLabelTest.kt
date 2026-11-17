package com.rocioplata.familyfinaiapp.ui.movements

import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemberLabelTest {
    private val rocio = FamilyMember("u-1", "Rocío Plata", FamilyRole.OWNER)
    private val luis = FamilyMember("u-2", "Luis", FamilyRole.MEMBER)
    private val sinNombre = FamilyMember("u-3", "  ", FamilyRole.MEMBER)
    private val nulo = FamilyMember("u-4", null, FamilyRole.MEMBER)
    private val members = listOf(rocio, luis, sinNombre, nulo)
    private val strings = mapOf(
        R.string.movements_recorded_by_you to "Tú",
        R.string.movements_recorded_by_former to "Ex-miembro",
        R.string.movements_recorded_by_generic to "Miembro de la familia",
    )
    private fun text(label: MemberLabel) = label.text { strings.getValue(it) }

    @Test
    fun elPropioUsuarioEsTuConSuRol() {
        val label = resolveMemberLabel("u-1", currentUserId = "u-1", members = members)

        assertEquals(MemberLabel.You("Rocío Plata", FamilyRole.OWNER), label)
        assertEquals("Tú", text(label))
        assertEquals("RP", label.initials)
        assertEquals(FamilyRole.OWNER, label.role)
    }

    @Test
    fun elPropioUsuarioEsTuAunqueLaListaNoEsteDisponible() {
        val label = resolveMemberLabel("u-1", currentUserId = "u-1", members = null)

        assertEquals("Tú", text(label))
        assertNull(label.role)
        assertNull(label.initials)
    }

    @Test
    fun otroMiembroSeMuestraConNombreIncialesYRol() {
        val label = resolveMemberLabel("u-2", currentUserId = "u-1", members = members)

        assertEquals(MemberLabel.Named("Luis", FamilyRole.MEMBER), label)
        assertEquals("Luis", text(label))
        assertEquals("L", label.initials)
        assertEquals(FamilyRole.MEMBER, label.role)
    }

    @Test
    fun alguienQueYaNoEsMiembroEsExMiembroSinRol() {
        val label = resolveMemberLabel("u-9", currentUserId = "u-1", members = members)

        assertEquals(MemberLabel.FormerMember, label)
        assertEquals("Ex-miembro", text(label))
        assertNull(label.role)
        assertNull(label.initials)
    }

    @Test
    fun unNombreVacioONuloEsElTextoGenerico() {
        listOf("u-3", "u-4").forEach {
            val label = resolveMemberLabel(it, currentUserId = "u-1", members = members)

            assertEquals(it, MemberLabel.NoName(FamilyRole.MEMBER), label)
            assertEquals(it, "Miembro de la familia", text(label))
            assertNull(it, label.initials)
            assertEquals(it, FamilyRole.MEMBER, label.role)
        }
    }

    @Test
    fun sinListaDeMiembrosNoHayTextoParaOtrosUsuarios() {
        val label = resolveMemberLabel("u-2", currentUserId = "u-1", members = null)

        assertEquals(MemberLabel.Unavailable, label)
        assertNull(text(label))
        assertNull(label.role)
    }

    @Test
    fun conListaVaciaQuienRegistroYaNoEsMiembro() {
        assertEquals(MemberLabel.FormerMember, resolveMemberLabel("u-2", "u-1", emptyList()))
    }

    @Test
    fun sinSesionNadieEsTu() {
        assertEquals(
            MemberLabel.Named("Rocío Plata", FamilyRole.OWNER),
            resolveMemberLabel("u-1", currentUserId = null, members = members),
        )
    }

    @Test
    fun inicialesDeUnoYDosNombres() {
        assertEquals("R", initialsOf("rocío"))
        assertEquals("RP", initialsOf("rocío plata"))
        assertEquals("AB", initialsOf("  ana   beatriz  de la cruz "))
        assertEquals("É", initialsOf("émile"))
    }

    @Test
    fun inicialesSinNombre() {
        assertNull(initialsOf(null))
        assertNull(initialsOf(""))
        assertNull(initialsOf("   "))
    }

    @Test
    fun cadaRolTieneSuTexto() {
        assertEquals(R.string.role_owner, FamilyRole.OWNER.labelRes())
        assertEquals(R.string.role_member, FamilyRole.MEMBER.labelRes())
    }
}
