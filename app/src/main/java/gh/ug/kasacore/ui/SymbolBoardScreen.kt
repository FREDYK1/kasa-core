package gh.ug.kasacore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import gh.ug.kasacore.model.Network
import gh.ug.kasacore.model.spokenNetworkNames
import gh.ug.kasacore.model.toGhanaLocalNumber
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
    onPick: (action: String, amount: Double?, recipientNumber: String?, reference: String?) -> Unit,
    onBack: () -> Unit,
) {
    var pickingSendMoney by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var numberText by remember { mutableStateOf("") }
    var referenceText by remember { mutableStateOf("1") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.symbol_board_title), style = KasaCaptionText)
        Spacer(Modifier.height(24.dp))

        if (!pickingSendMoney) {
            SymbolButton(stringResource(R.string.symbol_send_money)) { pickingSendMoney = true }
            Spacer(Modifier.height(16.dp))
            SymbolButton(stringResource(R.string.symbol_check_balance)) {
                onPick(Intent.CHECK_BALANCE, null, null, null)
            }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Back")
            }
        } else {
            // The network decides the menu route, so show what we detected as they type.
            val network = Network.fromNumber(numberText)
            val complete = numberText.toGhanaLocalNumber().length >= 10
            OutlinedTextField(
                value = numberText,
                onValueChange = { numberText = it.filter { c -> c.isDigit() } },
                label = { Text("Recipient's mobile number") },
                supportingText = {
                    val hint = when {
                        network != null -> "${network.label} number"
                        complete -> "Not an MTN, Telecel or AT number"
                        else -> "MTN, Telecel or AT, e.g. 0244123456"
                    }
                    Text(hint, modifier = Modifier.semantics { contentDescription = hint.spokenNetworkNames() })
                },
                isError = complete && network == null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount (GH₵)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = referenceText,
                // Typed into a USSD session, so keep to letters/digits/spaces — a stray * or # would
                // be read by the network as a USSD control character.
                onValueChange = { referenceText = it.filter { c -> c.isLetterOrDigit() || c == ' ' } },
                label = { Text("Reference") },
                supportingText = { Text("Defaults to 1 if left empty") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            val canSend = network != null && amountText.toDoubleOrNull() != null
            Button(
                onClick = {
                    onPick(
                        Intent.SEND_MONEY,
                        amountText.toDoubleOrNull(),
                        numberText.toGhanaLocalNumber(),
                        referenceText.trim().ifEmpty { "1" },
                    )
                },
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
