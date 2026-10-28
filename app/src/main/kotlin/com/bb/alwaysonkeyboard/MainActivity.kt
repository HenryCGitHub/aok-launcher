package com.bb.alwaysonkeyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.bb.alwaysonkeyboard.data.KeyboardStateRepository
import com.bb.alwaysonkeyboard.ui.theme.AlwaysOnKeyboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KeyboardStateRepository.initialize(applicationContext)

        enableEdgeToEdge()
        setContent {
            AlwaysOnKeyboardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        KeyboardStateRepository.refreshInstalledApps(applicationContext)
    }
}
