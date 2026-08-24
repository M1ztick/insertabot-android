package org.mistykmedia.insertabot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.mistykmedia.insertabot.ui.InsertaBotApp
import org.mistykmedia.insertabot.ui.theme.InsertaBotTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            InsertaBotTheme {
                InsertaBotApp()
            }
        }
    }
}
