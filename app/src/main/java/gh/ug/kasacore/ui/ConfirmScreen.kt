package gh.ug.kasacore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gh.ug.kasacore.R
import gh.ug.kasacore.ui.theme.KasaCaptionText

/*
 * 03_FRONTEND_ANDROID_GUIDE.md Step 3.3 — "the heart of safety". Nothing
 * proceeds without an explicit approve (WCAG 3.3.4). No timeout: this screen
 * never expires under the user (WCAG 2.2.1).
 */
@Composable
fun ConfirmScreen(summary: String, onApprove: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            summary,
            style = KasaCaptionText,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onApprove,
            modifier = Modifier.fillMaxWidth().height(96.dp)
                .semantics { contentDescription = "Yes, approve this action" },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text(stringResource(R.string.confirm_approve), style = KasaCaptionText) }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(72.dp)
                .semantics { contentDescription = "Cancel this action" },
        ) { Text(stringResource(R.string.confirm_cancel), style = KasaCaptionText) }
    }
}
