package com.localai.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.localai.chat.ui.navigation.AppNavigation
import com.localai.chat.ui.theme.LocalAIChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalAIChatTheme {
                AppNavigation()
            }
        }
    }
}
