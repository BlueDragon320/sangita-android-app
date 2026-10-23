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
