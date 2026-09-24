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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gh.ug.kasacore.R
import gh.ug.kasacore.model.Intent
import gh.ug.kasacore.ui.theme.KasaCaptionText

/*
 * 03_FRONTEND_ANDROID_GUIDE.md Step 5. Tapping symbols builds the SAME Intent
 * object and enters the SAME confirm flow as voice — this is what earns the
 * "adaptable beyond one disability group" score (users who cannot speak).
 *
 * Send money takes a number typed right here, not a saved trusted payee —
 * MoMo already requires the number on-screen at USSD time either way, and a
 * required trusted-contacts list was a dead end with nothing to tap when
 * empty. Voice commands still resolve a spoken name against PayeesRepository
 * (K05 Decision 3); this is the symbol-board path, where the number IS the
 * input.
 */
@Composable
fun SymbolBoardScreen(
    onPick: (action: String, amount: Double?, recipientNumber: String?) -> Unit,
    onBack: () -> Unit,
) {
    var pickingSendMoney by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var numberText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.symbol_board_title), style = KasaCaptionText)
        Spacer(Modifier.height(24.dp))

        if (!pickingSendMoney) {
            SymbolButton(stringResource(R.string.symbol_send_money)) { pickingSendMoney = true }
            Spacer(Modifier.height(16.dp))
            SymbolButton(stringResource(R.string.symbol_check_balance)) {
                onPick(Intent.CHECK_BALANCE, null, null)
            }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Back")
            }
        } else {
            OutlinedTextField(
                value = numberText,
                onValueChange = { numberText = it.filter { c -> c.isDigit() } },
                label = { Text("Recipient's MoMo number") },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Recipient's MoMo number" },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount (GH₵)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            val canSend = numberText.isNotBlank() && amountText.toDoubleOrNull() != null
            Button(
                onClick = { onPick(Intent.SEND_MONEY, amountText.toDoubleOrNull(), numberText) },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth().height(64.dp)
                    .semantics { contentDescription = "Send money to this number" },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
            ) { Text("Send") }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { pickingSendMoney = false }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun SymbolButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = label },
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) { Text(label, style = KasaCaptionText) }
}
