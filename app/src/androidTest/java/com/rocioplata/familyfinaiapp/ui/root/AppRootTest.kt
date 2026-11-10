package com.rocioplata.familyfinaiapp.ui.root

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import com.rocioplata.familyfinaiapp.ui.main.MainUiState
import com.rocioplata.familyfinaiapp.ui.main.TestMainHost
import com.rocioplata.familyfinaiapp.ui.main.contentState
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** La raíz decide qué se muestra según la sesión (CA-01, CA-07, CA-19, RF-09). */
@RunWith(AndroidJUnit4::class)
class AppRootTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val mainState = MainUiState(families = contentState(), pendingInvitations = 0, displayName = "rocío")

    @Test
    fun mientrasSeLeeLaSesion_noSeMuestraNiElAccesoNiLaPantallaPrincipal() {
        rule.setContent {
            FamilyFinAIAppTheme {
                AppRootContent(
                    state = SessionState.Loading,
                    signedOut = { Text("ACCESO") },
                    signedIn = { Text("PRINCIPAL") },
                )
            }
        }

        rule.onAllNodesWithText("ACCESO").assertCountEquals(0)
        rule.onAllNodesWithText("PRINCIPAL").assertCountEquals(0)
    }

    @Test
    fun sinSesion_muestraElAccesoConElMotivoDelCierre() {
        rule.setContent {
            FamilyFinAIAppTheme {
                AppRootContent(
                    state = SessionState.SignedOut(SessionEndReason.Expired),
                    signedOut = { reason -> Text("ACCESO $reason") },
                    signedIn = { Text("PRINCIPAL") },
                )
            }
        }

        rule.onNodeWithText("ACCESO Expired").assertIsDisplayed()
        rule.onAllNodesWithText("PRINCIPAL").assertCountEquals(0)
    }

    // CA-19
    @Test
    fun conSesion_muestraLaPantallaPrincipal_yAtrasEnInicioSaleDeLaAppSinMostrarElAcceso() {
        rule.setContent {
            FamilyFinAIAppTheme {
                AppRootContent(
                    state = SessionState.SignedIn("u-1", 1),
                    signedOut = { Text("ACCESO") },
                    signedIn = { TestMainHost(initial = mainState) },
                )
            }
        }
        rule.onNodeWithText("Inicio · Familia Plata").assertIsDisplayed()
        assertFalse(rule.activity.isFinishing)

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

        rule.waitUntil(timeoutMillis = 5_000) { rule.activity.isFinishing }
        assertTrue(rule.activity.isFinishing)
        rule.onAllNodesWithText("ACCESO").assertCountEquals(0)
    }

    // RF-09: otra sesión no hereda el estado de la anterior.
    @Test
    fun alCambiarLaGeneracion_laPantallaPrincipalEmpiezaDeNuevo() {
        var state by mutableStateOf<SessionState>(SessionState.SignedIn("u-1", 1))
        rule.setContent {
            FamilyFinAIAppTheme {
                AppRootContent(
                    state = state,
                    signedOut = { Text("ACCESO") },
                    signedIn = {
                        var taps by remember { mutableIntStateOf(0) }
                        Button(onClick = { taps++ }) { Text("toques=$taps") }
                    },
                )
            }
        }
        rule.onNodeWithText("toques=0").performClick()
        rule.onNodeWithText("toques=1").assertIsDisplayed()

        state = SessionState.SignedIn("u-1", 2)
        rule.waitForIdle()

        rule.onNodeWithText("toques=0").assertIsDisplayed()
    }
}
