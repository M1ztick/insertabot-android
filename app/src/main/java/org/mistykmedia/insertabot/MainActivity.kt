package org.mistykmedia.insertabot

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import org.mistykmedia.insertabot.ui.InsertaBotApp
import org.mistykmedia.insertabot.ui.theme.InsertaBotTheme

class MainActivity : FragmentActivity() {

    private val unlocked = mutableStateOf(false)
    private val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val canAuthenticate = BiometricManager.from(this)
            .canAuthenticate(authenticators)

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        unlocked.value = true
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        finish()
                    }
                }
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("InsertaBot")
                    .setSubtitle("Authenticate to continue")
                    .setAllowedAuthenticators(authenticators)
                    .build()
            )
        } else {
            // No screen lock enrolled — allow access but do not expose credentials silently
            unlocked.value = true
        }

        setContent {
            InsertaBotTheme {
                if (unlocked.value) InsertaBotApp()
            }
        }
    }
}
