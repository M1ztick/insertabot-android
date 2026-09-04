package org.mistykmedia.insertabot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.mistykmedia.insertabot.ui.InsertaBotApp
import org.mistykmedia.insertabot.ui.theme.InsertaBotTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the launch theme is swapped for
        // Theme.InsertaBot before the first frame is drawn.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            InsertaBotTheme {
                InsertaBotApp()
            }
        }
    }
}
