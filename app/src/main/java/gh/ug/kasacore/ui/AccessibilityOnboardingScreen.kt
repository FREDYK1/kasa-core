package gh.ug.kasacore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import gh.ug.kasacore.R

/*
 * 03_FRONTEND_ANDROID_GUIDE.md Step 6 — "Onboarding to enable the
 * AccessibilityService... a guided, TalkBack-friendly screen." Also carries
 * the K11-K13 PIN-audio reminder: TalkBack "Speak passwords" must stay OFF so
 * the native PIN dialog never reads digits aloud (docs/08_K11_K13_SPIKE.md §0).
 */
@Composable
fun AccessibilityOnboardingScreen(onOpenSettings: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.onboarding_title), color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onboarding_body))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_speak_passwords_check),
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(stringResource(R.string.onboarding_open_settings))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Back")
        }
    }
}
