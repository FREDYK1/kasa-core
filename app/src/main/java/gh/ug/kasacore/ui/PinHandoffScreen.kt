package gh.ug.kasacore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gh.ug.kasacore.R
import gh.ug.kasacore.ui.theme.KasaCaptionText

/*
 * K05 Decision 1, the whole product: the app shows NOTHING that captures a
 * digit. The user types the PIN straight into the phone's own system USSD
 * dialog, which this screen sits behind/underneath. There is no keypad here
 * on purpose — see docs/06_K05_DESIGN_DECISIONS.md.
 */
@Composable
fun PinHandoffScreen(caption: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            caption,
            style = KasaCaptionText,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.pin_handoff_earpiece_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
