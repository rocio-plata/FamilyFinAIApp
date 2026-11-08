package com.rocioplata.familyfinaiapp.ui.auth

import com.rocioplata.familyfinaiapp.domain.auth.AuthFailure
import com.rocioplata.familyfinaiapp.domain.auth.AuthRepository
import com.rocioplata.familyfinaiapp.domain.auth.AuthResult
import com.rocioplata.familyfinaiapp.domain.auth.FieldError
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private class FakeRepository : AuthRepository {
        data class Login(val email: String, val password: String)
        data class Register(val name: String, val email: String, val password: String)

        val logins = mutableListOf<Login>()
        val registers = mutableListOf<Register>()
        var result: AuthResult = AuthResult.Success
        var crash: Exception? = null
        var gate: CompletableDeferred<Unit>? = null

        val calls get() = logins.size + registers.size

        override suspend fun login(email: String, password: String): AuthResult {
            logins += Login(email, password)
            return answer()
        }

        override suspend fun register(displayName: String, email: String, password: String): AuthResult {
            registers += Register(displayName, email, password)
            return answer()
        }

        private suspend fun answer(): AuthResult {
            gate?.await()
            crash?.let { throw it }
            return result
        }
    }

    private val repository = FakeRepository()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(endReason: SessionEndReason? = null) = AuthViewModel(repository, endReason)

    private fun AuthViewModel.type(field: AuthField, value: String) = onFieldChanged(field, value)

    private fun AuthViewModel.fillLogin(email: String = "ana@mail.com", password: String = "secreto") {
        type(AuthField.LoginEmail, email)
        type(AuthField.LoginPassword, password)
    }

    private fun AuthViewModel.fillRegister(
        name: String = "Ana",
        email: String = "ana@mail.com",
        password: String = "secreto123",
        confirmation: String = password,
    ) {
        onTabSelected(AuthTab.Register)
        type(AuthField.RegisterName, name)
        type(AuthField.RegisterEmail, email)
        type(AuthField.RegisterPassword, password)
        type(AuthField.RegisterPasswordConfirmation, confirmation)
    }

    private fun AuthViewModel.error(field: AuthField) = uiState.value.field(field).error

    @Test
    fun estadoInicial_esLoginVacioSinErrores() {
        val state = viewModel().uiState.value

        assertEquals(AuthTab.Login, state.tab)
        assertTrue(AuthField.entries.all { state.field(it) == FieldState() })
        assertTrue(state.visiblePasswords.isEmpty())
        assertFalse(state.submitting)
        assertNull(state.generalError)
        assertNull(state.endReason)
    }

    @Test
    fun motivoDeCierre_seMuestraDesdeElInicio() {
        assertEquals(SessionEndReason.Expired, viewModel(SessionEndReason.Expired).uiState.value.endReason)
    }

    @Test
    fun escribirUnValorInvalido_noMuestraErrorHastaPerderElFoco() {
        val vm = viewModel()

        vm.type(AuthField.LoginEmail, "no-es-un-correo")
        assertNull(vm.error(AuthField.LoginEmail))

        vm.onFieldBlur(AuthField.LoginEmail)
        assertEquals(FieldError.INVALID_EMAIL, vm.error(AuthField.LoginEmail))
    }

    @Test
    fun alPerderElFoco_cadaCampoMuestraSuError() {
        val vm = viewModel()
        vm.onTabSelected(AuthTab.Register)
        vm.type(AuthField.RegisterName, "   ")
        vm.type(AuthField.RegisterPassword, "corta")
        vm.type(AuthField.RegisterPasswordConfirmation, "otra")

        AuthTab.Register.fields().forEach(vm::onFieldBlur)

        assertEquals(FieldError.REQUIRED, vm.error(AuthField.RegisterName))
        assertEquals(FieldError.REQUIRED, vm.error(AuthField.RegisterEmail))
        assertEquals(FieldError.PASSWORD_TOO_SHORT, vm.error(AuthField.RegisterPassword))
        assertEquals(FieldError.PASSWORDS_DO_NOT_MATCH, vm.error(AuthField.RegisterPasswordConfirmation))
    }

    @Test
    fun nombreDeMasDe60_esError_yElEspacioFinalNoCuenta() {
        val vm = viewModel()
        vm.onTabSelected(AuthTab.Register)

        vm.type(AuthField.RegisterName, "a".repeat(61))
        vm.onFieldBlur(AuthField.RegisterName)
        assertEquals(FieldError.NAME_TOO_LONG, vm.error(AuthField.RegisterName))

        vm.type(AuthField.RegisterName, "a".repeat(60) + "   ")
        assertNull(vm.error(AuthField.RegisterName))
    }

    @Test
    fun elErrorDesapareceAlCorregirElValor() {
        val vm = viewModel()
        vm.type(AuthField.LoginEmail, "mal")
        vm.onFieldBlur(AuthField.LoginEmail)

        vm.type(AuthField.LoginEmail, "ana@mail.com")

        assertNull(vm.error(AuthField.LoginEmail))
    }

    @Test
    fun enElLogin_laContrasenaSoloDebeNoEstarVacia() {
        val vm = viewModel()
        vm.type(AuthField.LoginPassword, "123")
        vm.onFieldBlur(AuthField.LoginPassword)
        assertNull(vm.error(AuthField.LoginPassword))

        vm.onTabSelected(AuthTab.Register)
        vm.type(AuthField.RegisterPassword, "123")
        vm.onFieldBlur(AuthField.RegisterPassword)
        assertEquals(FieldError.PASSWORD_TOO_SHORT, vm.error(AuthField.RegisterPassword))
    }

    @Test
    fun cambiarLaContrasena_revalidaLaConfirmacionYaMostrada() {
        val vm = viewModel()
        vm.onTabSelected(AuthTab.Register)
        vm.type(AuthField.RegisterPassword, "secreto123")
        vm.type(AuthField.RegisterPasswordConfirmation, "secreto123")
        vm.onFieldBlur(AuthField.RegisterPasswordConfirmation)
        assertNull(vm.error(AuthField.RegisterPasswordConfirmation))

        vm.type(AuthField.RegisterPassword, "secreto1234")

        assertEquals(FieldError.PASSWORDS_DO_NOT_MATCH, vm.error(AuthField.RegisterPasswordConfirmation))
    }

    @Test
    fun enviarConErrores_noLlamaAlRepositorio_muestraTodosYPideElFoco() = runTest {
        val vm = viewModel()

        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(0, repository.calls)
        assertEquals(FieldError.REQUIRED, vm.error(AuthField.LoginEmail))
        assertEquals(FieldError.REQUIRED, vm.error(AuthField.LoginPassword))
        assertEquals(1, vm.uiState.value.invalidSubmitCount)
        assertFalse(vm.uiState.value.submitting)

        vm.onSubmit()
        assertEquals(2, vm.uiState.value.invalidSubmitCount)
    }

    @Test
    fun enviarConErrores_soloValidaLosCamposDeLaPestanaActiva() = runTest {
        val vm = viewModel()

        vm.onSubmit()

        assertNull(vm.error(AuthField.RegisterName))
        assertFalse(vm.uiState.value.field(AuthField.RegisterName).touched)
    }

    @Test
    fun login_enviaElEmailRecortadoYLaContrasenaTalCual() = runTest {
        val vm = viewModel()
        vm.fillLogin(email = "  ana@mail.com ", password = " con espacios ")

        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf(FakeRepository.Login("ana@mail.com", " con espacios ")), repository.logins)
    }

    @Test
    fun registro_enviaNombreYEmailRecortados() = runTest {
        val vm = viewModel()
        vm.fillRegister(name = "  Ana Plata ", email = " ana@mail.com  ")

        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf(FakeRepository.Register("Ana Plata", "ana@mail.com", "secreto123")), repository.registers)
        assertEquals(0, repository.logins.size)
    }

    @Test
    fun envioCorrecto_noRehabilitaElBoton() = runTest {
        val vm = viewModel()
        vm.fillLogin()

        vm.onSubmit()
        advanceUntilIdle()

        // La raíz sustituye la pantalla al iniciarse la sesión.
        assertTrue(vm.uiState.value.submitting)
        assertNull(vm.uiState.value.generalError)
    }

    @Test
    fun dosEnviosDuranteUnoEnCurso_llamanUnaSolaVez() = runTest {
        repository.gate = CompletableDeferred()
        val vm = viewModel()
        vm.fillLogin()

        vm.onSubmit()
        vm.onSubmit()
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(1, repository.calls)
        assertTrue(vm.uiState.value.submitting)
    }

    @Test
    fun mientrasSeEnvia_seIgnoranEdicionesYCambiosDePestana() = runTest {
        repository.gate = CompletableDeferred()
        val vm = viewModel()
        vm.fillLogin()
        vm.onSubmit()

        vm.type(AuthField.LoginEmail, "otra@mail.com")
        vm.onTabSelected(AuthTab.Register)

        assertEquals("ana@mail.com", vm.uiState.value.field(AuthField.LoginEmail).value)
        assertEquals(AuthTab.Login, vm.uiState.value.tab)
    }

    @Test
    fun cadaFallo_seInformaConservaLosValoresYRehabilitaElEnvio() = runTest {
        val failures = listOf(
            AuthFailure.InvalidCredentials,
            AuthFailure.RegistrationRejected,
            AuthFailure.Network,
            AuthFailure.Unknown,
        )
        for (failure in failures) {
            repository.result = AuthResult.Failure(failure)
            val vm = viewModel()
            vm.fillLogin(password = "mi-clave")

            vm.onSubmit()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(failure, state.generalError)
            assertFalse(state.submitting)
            assertEquals("ana@mail.com", state.field(AuthField.LoginEmail).value)
            assertEquals("mi-clave", state.field(AuthField.LoginPassword).value)
            // Un rechazo del backend no marca ningún campo (RF-04).
            assertTrue(AuthTab.Login.fields().all { state.field(it).error == null })
        }
    }

    @Test
    fun errorDeRed_permiteReintentarConLosMismosDatos() = runTest {
        repository.result = AuthResult.Failure(AuthFailure.Network)
        val vm = viewModel()
        vm.fillLogin()
        vm.onSubmit()
        advanceUntilIdle()

        repository.result = AuthResult.Success
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(2, repository.logins.size)
        assertEquals(repository.logins[0], repository.logins[1])
        assertNull(vm.uiState.value.generalError)
    }

    @Test
    fun excepcionInesperadaDelRepositorio_esErrorGenericoYNoCierraLaApp() = runTest {
        repository.crash = IllegalStateException("no se pudo guardar la sesión")
        val vm = viewModel()
        vm.fillLogin()

        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(AuthFailure.Unknown, vm.uiState.value.generalError)
        assertFalse(vm.uiState.value.submitting)
    }

    @Test
    fun alternarPestana_conservaLoEscritoEnAmbosFormularios() {
        val vm = viewModel()
        vm.fillLogin(email = "login@mail.com", password = "clave-login")
        vm.fillRegister(name = "Ana", email = "registro@mail.com", password = "clave-registro")

        vm.onTabSelected(AuthTab.Login)
        vm.onTabSelected(AuthTab.Register)

        val state = vm.uiState.value
        assertEquals("login@mail.com", state.field(AuthField.LoginEmail).value)
        assertEquals("clave-login", state.field(AuthField.LoginPassword).value)
        assertEquals("Ana", state.field(AuthField.RegisterName).value)
        assertEquals("registro@mail.com", state.field(AuthField.RegisterEmail).value)
        assertEquals("clave-registro", state.field(AuthField.RegisterPassword).value)
    }

    @Test
    fun cambiarDePestana_ocultaElMensajeDeCierreYElErrorGeneral() = runTest {
        val vm = viewModel(SessionEndReason.Security)
        vm.onTabSelected(AuthTab.Register)
        assertNull(vm.uiState.value.endReason)

        repository.result = AuthResult.Failure(AuthFailure.RegistrationRejected)
        vm.fillRegister()
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(AuthFailure.RegistrationRejected, vm.uiState.value.generalError)

        vm.onTabSelected(AuthTab.Login)
        assertNull(vm.uiState.value.generalError)
    }

    @Test
    fun enviar_ocultaElMensajeDeCierre() = runTest {
        repository.gate = CompletableDeferred()
        val vm = viewModel(SessionEndReason.Expired)
        vm.fillLogin()

        vm.onSubmit()

        assertNull(vm.uiState.value.endReason)
    }

    @Test
    fun enviarConErrores_tambienOcultaElMensajeDeCierre() {
        val vm = viewModel(SessionEndReason.Expired)

        vm.onSubmit()

        assertNull(vm.uiState.value.endReason)
    }

    @Test
    fun visibilidadDeContrasena_alternaPorCampoYEstaOcultaPorDefecto() {
        val vm = viewModel()
        assertFalse(AuthField.LoginPassword in vm.uiState.value.visiblePasswords)

        vm.onTogglePasswordVisibility(AuthField.LoginPassword)
        assertEquals(setOf(AuthField.LoginPassword), vm.uiState.value.visiblePasswords)

        vm.onTogglePasswordVisibility(AuthField.RegisterPassword)
        assertEquals(setOf(AuthField.LoginPassword, AuthField.RegisterPassword), vm.uiState.value.visiblePasswords)

        vm.onTogglePasswordVisibility(AuthField.LoginPassword)
        assertEquals(setOf(AuthField.RegisterPassword), vm.uiState.value.visiblePasswords)
    }

    @Test
    fun visibilidad_soloAplicaALosCamposDeContrasena() {
        val vm = viewModel()

        vm.onTogglePasswordVisibility(AuthField.LoginEmail)

        assertTrue(vm.uiState.value.visiblePasswords.isEmpty())
    }

    @Test
    fun elEstadoNoRevelaLasContrasenasEnToString() {
        val vm = viewModel()
        vm.fillLogin(password = "MUY-SECRETA")

        assertFalse(vm.uiState.value.toString().contains("MUY-SECRETA"))
        assertFalse(vm.uiState.value.field(AuthField.LoginPassword).toString().contains("MUY-SECRETA"))
    }
}
