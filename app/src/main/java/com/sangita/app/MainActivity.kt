package com.sangita.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.sangita.app.ui.navigation.SangitaNavGraph
import com.sangita.app.ui.theme.SangitaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Ask Android for the fastest refresh rate at the current display resolution.
        // The system can still reduce it for battery, thermal or user preferences.
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val currentMode = display.mode
        val preferredRate = display.supportedModes
            .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
            .maxOfOrNull { it.refreshRate }
        if (preferredRate != null) {
            window.attributes = window.attributes.apply { preferredRefreshRate = preferredRate }
        }

        enableEdgeToEdge()

        val app = application as SangitaApplication
        val userPreferences = app.container.userPreferences

        setContent {
            val currentTheme by userPreferences.themeFlow.collectAsState()
            SangitaTheme(themeMode = currentTheme) {
                val navController = rememberNavController()
                SangitaNavGraph(
                    navController = navController,
                    app = app
                )
            }
        }
    }
}
