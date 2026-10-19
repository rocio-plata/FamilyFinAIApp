package com.rocioplata.familyfinaiapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.rocioplata.familyfinaiapp.ui.main.MainScreen
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamilyFinAIAppTheme {
                MainScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
