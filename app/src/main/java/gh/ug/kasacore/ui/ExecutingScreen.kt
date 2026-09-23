package gh.ug.kasacore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gh.ug.kasacore.ui.theme.KasaCaptionText

/** Shown while the USSD engine is driving menus (UssdListener.onMenuRead keeps updating the caption elsewhere). */
@Composable
fun ExecutingScreen(caption: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            caption,
            style = KasaCaptionText,
            modifier = Modifier.padding(top = 24.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
