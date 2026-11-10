package com.rocioplata.familyfinaiapp.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.auth.AuthFailure
import com.rocioplata.familyfinaiapp.domain.auth.AuthRepository
import com.rocioplata.familyfinaiapp.domain.auth.AuthResult
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val EMAIL = "Correo electrónico"
private const val PASSWORD = "Contraseña"
private const val CONFIRMATION = "Repetir contraseña"
private const val NAME = "Nombre visible"
private const val LOGIN = "Iniciar sesión"
private const val REGISTER = "Crear cuenta"
private const val REQUIRED = "Este campo es obligatorio."

/** Pantalla de acceso con el `AuthViewModel` real y un repositorio falso, sin red (CA-01, 04–06, 16–18, 22, 23). */
@RunWith(AndroidJUnit4::class)
class AuthScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private class FakeAuthRepository : AuthRepository {
        var result: AuthResult = AuthResult.Success
        var gate: CompletableDeferred<Unit>? = null
        val calls = mutableListOf<String>()

        override suspend fun login(email: String, password: String): AuthResult {
            calls += "login"
            gate?.await()
            return result
        }

        override suspend fun register(displayName: String, email: String, password: String): AuthResult {
            calls += "register"
            gate?.await()
            return result
        }
    }

    private val repository = FakeAuthRepository()

    @Composable
    private fun Host(viewModel: AuthViewModel) {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        FamilyFinAIAppTheme {
            AuthContent(
                state = state,
                onTabSelected = viewModel::onTabSelected,
                onFieldChanged = viewModel::onFieldChanged,
                onFieldBlur = viewModel::onFieldBlur,
                onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
                onSubmit = viewModel::onSubmit,
            )
        }
    }

    private fun show(endReason: SessionEndReason? = null): AuthViewModel {
        val viewModel = AuthViewModel(repository, endReason)
        rule.setContent { Host(viewModel) }
        return viewModel
    }

    private fun field(label: String) = rule.onNode(hasSetTextAction() and hasText(label))

    // Un campo deshabilitado pierde la acción de escribir, pero conserva su etiqueta.
    private fun anyField(label: String) = rule.onNode(hasText(label))

    // Las pestañas son seleccionables; el botón de envío y el enlace del pie, no.
    private fun tab(label: String) = rule.onNode(hasText(label) and isSelectable())

    private fun action(label: String) = rule.onNode(hasText(label) and hasClickAction() and !isSelectable())

    private fun SemanticsNodeInteraction.assertValue(expected: String) = assert(
        SemanticsMatcher("valor «$expected»") { it.config.getOrNull(SemanticsProperties.EditableText)?.text == expected },
    )

    private fun SemanticsNodeInteraction.assertError(message: String) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, message))

    private fun SemanticsNodeInteraction.assertNoError() =
        assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))

    // `Password` depende del tipo de teclado, no de si el texto se ve; se comprueba lo que se dibuja.
    private fun SemanticsNodeInteraction.shownText(): String {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.first().layoutInput.text.text
    }

    private fun SemanticsNodeInteraction.assertMasked(plain: String) {
        val shown = shownText()
        assertEquals(plain.length, shown.length)
        assertTrue("La contraseña se ve en claro", shown != plain && shown.toSet().size == 1)
    }

    private fun SemanticsNodeInteraction.assertRevealed(plain: String) = assertEquals(plain, shownText())

    // En el registro, el botón y el enlace del pie quedan fuera de la ventana de prueba: hay que desplazarse.
    private fun SemanticsNodeInteraction.press() = performScrollTo().performClick()

    private fun fillLogin(email: String = "ana@mail.com", password: String = "secreto") {
        field(EMAIL).performTextInput(email)
        field(PASSWORD).performTextInput(password)
    }

    private fun fillRegister(name: String = "Ana", email: String = "ana@mail.com", password: String = "secreto123") {
        field(NAME).performTextInput(name)
        field(EMAIL).performTextInput(email)
        field(PASSWORD).performTextInput(password)
        field(CONFIRMATION).performTextInput(password)
    }

    // La tecla Atrás del sistema (no una llamada directa al dispatcher), como la pulsa una persona.
    private fun pressBack() = Espresso.pressBack()

    // Si Atrás cierra la app, `pressBack` lanzaría NoActivityResumedException.
    private fun pressBackToExit() = Espresso.pressBackUnconditionally()

    // Al terminar la actividad desaparece la jerarquía de Compose y `rule.activity` deja de poder leerse
    // (falla con "Activity has been destroyed already"): se conserva la referencia antes de pulsar Atrás.
    private fun hasFinished(activity: ComponentActivity) = activity.isFinishing || activity.isDestroyed

    private fun assertActivityFinishes(activity: ComponentActivity) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!hasFinished(activity) && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue("La actividad debía terminar", hasFinished(activity))
    }

    // CA-01
    @Test
    fun primerUso_muestraElLoginVacioConAccesoAlRegistro() {
        show()

        rule.onNodeWithText("FamilyFin AI").assertIsDisplayed()
        rule.onNodeWithText("Smarter family finances").assertIsDisplayed()
        tab(LOGIN).assertIsSelected()
        tab(REGISTER).assertIsNotSelected()
        field(EMAIL).assertIsDisplayed().assertValue("")
        field(PASSWORD).assertIsDisplayed().assertValue("")
        action(LOGIN).assertIsDisplayed()
        rule.onNodeWithText("¿No tienes cuenta?").assertIsDisplayed()
        action(REGISTER).assertIsDisplayed()
    }

    // CA-04
    @Test
    fun enviarVacio_muestraLosErroresNoLlamaAlRepositorioYLlevaElFocoAlPrimero() {
        show()

        action(LOGIN).press()

        rule.onAllNodesWithText(REQUIRED).assertCountEquals(2)
        field(EMAIL).assertError(REQUIRED).assertIsFocused()
        field(PASSWORD).assertError(REQUIRED)
        assertEquals(emptyList<String>(), repository.calls)
    }

    // CA-04
    @Test
    fun emailInvalido_muestraElErrorAlPerderElFocoYDesapareceAlCorregirlo() {
        show()
        field(EMAIL).press()
        field(EMAIL).performTextInput("no-es-un-correo")
        field(EMAIL).assertNoError()

        field(PASSWORD).press()

        field(EMAIL).assertError("Escribe un correo electrónico válido.")
        rule.onNodeWithText("Escribe un correo electrónico válido.").assertIsDisplayed()

        field(EMAIL).performTextReplacement("ana@mail.com")

        field(EMAIL).assertNoError()
        rule.onAllNodesWithText("Escribe un correo electrónico válido.").assertCountEquals(0)
    }

    // CA-04
    @Test
    fun registroConDatosInvalidos_muestraCadaErrorYElFocoVaAlNombre() {
        show()
        tab(REGISTER).press()
        field(EMAIL).performTextInput("ana@")
        field(PASSWORD).performTextInput("corta")
        field(CONFIRMATION).performTextInput("otra")

        action(REGISTER).press()

        field(NAME).assertError(REQUIRED).assertIsFocused()
        field(EMAIL).assertError("Escribe un correo electrónico válido.")
        field(PASSWORD).assertError("La contraseña debe tener al menos 8 caracteres.")
        field(CONFIRMATION).assertError("Las contraseñas no coinciden.")
        assertEquals(emptyList<String>(), repository.calls)
    }

    @Test
    fun registro_muestraLaAyudaDeLaContrasenaYLasEtiquetasDeCadaCampo() {
        show()
        tab(REGISTER).press()

        listOf(NAME, EMAIL, PASSWORD, CONFIRMATION).forEach { field(it).assertIsDisplayed() }
        rule.onNodeWithText("Mínimo 8 caracteres").assertIsDisplayed()
    }

    // CA-05
    @Test
    fun credencialesIncorrectas_muestranElMensajeSinMarcarNingunCampo() {
        repository.result = AuthResult.Failure(AuthFailure.InvalidCredentials)
        show()
        fillLogin()

        action(LOGIN).press()

        rule.onNodeWithText("Correo o contraseña incorrectos.").assertIsDisplayed()
        field(EMAIL).assertNoError().assertValue("ana@mail.com")
        field(PASSWORD).assertNoError()
        action(LOGIN).assertIsEnabled()
    }

    // CA-06
    @Test
    fun emailYaRegistrado_muestraElMensajeNeutroSinMarcarElEmail() {
        repository.result = AuthResult.Failure(AuthFailure.RegistrationRejected)
        show()
        tab(REGISTER).press()
        fillRegister()

        action(REGISTER).press()

        rule.onNodeWithText("No pudimos crear la cuenta con esos datos. Si ya tienes una cuenta, inicia sesión.")
            .assertIsDisplayed()
        field(EMAIL).assertNoError().assertValue("ana@mail.com")
        assertEquals(listOf("register"), repository.calls)
    }

    // CA-17
    @Test
    fun sinConexion_muestraElMensajeYConservaLosDatos() {
        repository.result = AuthResult.Failure(AuthFailure.Network)
        show()
        fillLogin(email = "ana@mail.com")

        action(LOGIN).press()

        rule.onNodeWithText("Sin conexión. Revisa tu red e inténtalo de nuevo.").assertIsDisplayed()
        field(EMAIL).assertValue("ana@mail.com")
        action(LOGIN).assertIsEnabled()
    }

    @Test
    fun errorGenerico_muestraElMensajeConLaOpcionDeReintentar() {
        repository.result = AuthResult.Failure(AuthFailure.Unknown)
        show()
        fillLogin()

        action(LOGIN).press()
        rule.onNodeWithText("No pudimos completar la operación. Inténtalo de nuevo.").assertIsDisplayed()
        repository.result = AuthResult.Failure(AuthFailure.InvalidCredentials)
        action(LOGIN).press()

        rule.onNodeWithText("Correo o contraseña incorrectos.").assertIsDisplayed()
        assertEquals(listOf("login", "login"), repository.calls)
    }

    // CA-16
    @Test
    fun mientrasSeEnvia_elBotonYLosCamposQuedanDeshabilitadosYSoloHayUnEnvio() {
        repository.gate = CompletableDeferred()
        show()
        fillLogin()

        action(LOGIN).press()
        action(LOGIN).press()
        action(LOGIN).press()

        action(LOGIN).assertIsNotEnabled()
        anyField(EMAIL).assertIsNotEnabled()
        anyField(PASSWORD).assertIsNotEnabled()
        tab(REGISTER).assertIsNotEnabled()
        assertEquals(listOf("login"), repository.calls)
    }

    // RF-07
    @Test
    fun cierreForzado_muestraElMensajeSegunElMotivoYSeOcultaAlCambiarDePestana() {
        show(SessionEndReason.Expired)

        val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        rule.onNode(hasText("Tu sesión expiró. Inicia sesión de nuevo.") and hasAnyAncestor(polite)).assertIsDisplayed()

        tab(REGISTER).press()

        rule.onAllNodesWithText("Tu sesión expiró. Inicia sesión de nuevo.").assertCountEquals(0)
    }

    // RF-07
    @Test
    fun posibleRobo_muestraElMensajeDeSeguridad() {
        show(SessionEndReason.Security)

        rule.onNodeWithText("Cerramos tu sesión por seguridad. Inicia sesión de nuevo.").assertIsDisplayed()
    }

    // RF-09
    @Test
    fun trasCerrarSesionPorElUsuario_noSeMuestraNingunMensaje() {
        show(SessionEndReason.LoggedOut)

        rule.onAllNodesWithText("Tu sesión expiró. Inicia sesión de nuevo.").assertCountEquals(0)
        rule.onAllNodesWithText("Cerramos tu sesión por seguridad. Inicia sesión de nuevo.").assertCountEquals(0)
        field(EMAIL).assertValue("")
    }

    // CA-22
    @Test
    fun mostrarOcultarContrasena_alternaElTextoYAnunciaSuEstado() {
        show()
        field(PASSWORD).performTextInput("secreto")
        field(PASSWORD).assertMasked("secreto")
        rule.onNodeWithContentDescription("Mostrar contraseña").assertIsDisplayed()

        rule.onNodeWithContentDescription("Mostrar contraseña").press()

        field(PASSWORD).assertRevealed("secreto")
        rule.onNodeWithContentDescription("Ocultar contraseña").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Mostrar contraseña").assertCountEquals(0)

        rule.onNodeWithContentDescription("Ocultar contraseña").press()

        field(PASSWORD).assertMasked("secreto")
        rule.onNodeWithContentDescription("Mostrar contraseña").assertIsDisplayed()
    }

    // CA-22
    @Test
    fun enElRegistro_cadaContrasenaTieneSuPropioControl() {
        show()
        tab(REGISTER).press()
        field(PASSWORD).performTextInput("clave-uno")
        field(CONFIRMATION).performTextInput("clave-dos")
        field(PASSWORD).assertMasked("clave-uno")
        field(CONFIRMATION).assertMasked("clave-dos")
        rule.onAllNodesWithContentDescription("Mostrar contraseña").assertCountEquals(2)

        rule.onAllNodesWithContentDescription("Mostrar contraseña")[1].press()

        field(PASSWORD).assertMasked("clave-uno")
        field(CONFIRMATION).assertRevealed("clave-dos")
    }

    // CA-23
    @Test
    fun alternarPorPestanasYEnlaces_conservaLoEscritoYAnunciaLaPestanaActiva() {
        show()
        field(EMAIL).performTextInput("login@mail.com")

        tab(REGISTER).press()
        tab(REGISTER).assertIsSelected()
        tab(LOGIN).assertIsNotSelected()
        field(NAME).performTextInput("Ana")
        field(EMAIL).performTextInput("registro@mail.com")

        action(LOGIN).press() // enlace del pie del registro
        tab(LOGIN).assertIsSelected()
        field(EMAIL).assertValue("login@mail.com")

        action(REGISTER).press() // enlace del pie del login
        tab(REGISTER).assertIsSelected()
        field(NAME).assertValue("Ana")
        field(EMAIL).assertValue("registro@mail.com")

        tab(LOGIN).press()
        tab(LOGIN).assertIsSelected()
        field(EMAIL).assertValue("login@mail.com")
    }

    // CA-18
    @Test
    fun alRecrearLaPantalla_seConservanLaPestanaLosDatosYLosErrores() {
        val viewModel = AuthViewModel(repository)
        val restoration = StateRestorationTester(rule)
        restoration.setContent { Host(viewModel) }
        tab(REGISTER).press()
        field(NAME).performTextInput("Ana")
        field(EMAIL).performTextInput("ana@")
        action(REGISTER).press()
        field(EMAIL).assertError("Escribe un correo electrónico válido.")

        restoration.emulateSavedInstanceStateRestore()

        tab(REGISTER).assertIsSelected()
        field(NAME).assertValue("Ana")
        field(EMAIL).assertValue("ana@").assertError("Escribe un correo electrónico válido.")
        field(PASSWORD).assertError(REQUIRED)
    }

    // CA-18
    @Test
    fun atrasEnElRegistro_vuelveAlLoginSinSalirDeLaApp() {
        show()
        tab(REGISTER).press()
        field(NAME).performTextInput("Ana")

        // Con el teclado abierto, el primer Atrás solo lo cierra (comportamiento estándar de Android).
        Espresso.closeSoftKeyboard()
        pressBack()
        rule.waitForIdle()

        tab(LOGIN).assertIsSelected()
        assertTrue("Atrás en el registro no debe salir de la app", !rule.activity.isFinishing)
        tab(REGISTER).press()
        field(NAME).assertValue("Ana")
    }

    // CA-18
    @Test
    fun atrasEnElLogin_saleDeLaApp() {
        show()

        val activity = rule.activity

        pressBackToExit()

        assertActivityFinishes(activity)
    }

    // CA-18
    @Test
    fun atrasEnRegistroYLuegoEnLogin_vuelveYDespuesSale() {
        show()
        tab(REGISTER).press()

        pressBack()
        rule.waitForIdle()
        tab(LOGIN).assertIsSelected()

        val activity = rule.activity
        pressBackToExit()
        assertActivityFinishes(activity)
    }

    // CA-18: igual, pero habiendo escrito antes en el registro.
    @Test
    fun atrasHabiendoEscritoEnElRegistro_vuelveYDespuesSale() {
        show()
        tab(REGISTER).press()
        field(NAME).performTextInput("Ana")

        // Con el teclado abierto, el primer Atrás solo lo cierra (comportamiento estándar de Android).
        Espresso.closeSoftKeyboard()
        pressBack()
        rule.waitForIdle()
        tab(LOGIN).assertIsSelected()
        tab(REGISTER).press()
        tab(LOGIN).press()

        val activity = rule.activity
        pressBackToExit()
        assertActivityFinishes(activity)
    }
}
