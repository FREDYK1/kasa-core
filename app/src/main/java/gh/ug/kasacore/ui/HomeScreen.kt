package gh.ug.kasacore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gh.ug.kasacore.R
import gh.ug.kasacore.ui.theme.KasaBigButtonText

/*
 * 03_FRONTEND_ANDROID_GUIDE.md Step 3.1: one big "Speak" button + symbol-board
 * entry, both clearly TalkBack-announced. This is the whole home screen —
 * deliberately nothing else competes with it (K05 Decision 2: no voice-wake,
 * a big tap target instead).
 */
@Composable
fun HomeScreen(
    onSpeak: () -> Unit,
    onSymbolBoard: () -> Unit,
    onAddPayee: () -> Unit,
    onOnboarding: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(24.dp)),
        verticalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = onSpeak,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = "Speak a command, double tap to start listening" },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(stringResource(R.string.home_speak_button), style = KasaBigButtonText)
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = onSymbolBoard,
            modifier = Modifier.fillMaxWidth().height(64.dp)
                .semantics { contentDescription = "Use symbols instead of your voice" },
        ) { Text(stringResource(R.string.home_symbol_board_button)) }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onAddPayee,
            modifier = Modifier.fillMaxWidth().height(64.dp)
                .semantics { contentDescription = "Add a trusted contact to send money to" },
        ) { Text(stringResource(R.string.home_add_payee_button)) }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onOnboarding,
            modifier = Modifier.fillMaxWidth().height(64.dp)
                .semantics { contentDescription = "Turn on menu reading in accessibility settings" },
        ) { Text(stringResource(R.string.home_onboarding_button)) }
    }
}
