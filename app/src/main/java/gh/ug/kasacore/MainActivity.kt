package gh.ug.kasacore

import android.Manifest
import android.content.Intent as AndroidIntent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import gh.ug.kasacore.ui.AccessibilityOnboardingScreen
import gh.ug.kasacore.ui.AddPayeeScreen
import gh.ug.kasacore.ui.ConfirmScreen
import gh.ug.kasacore.ui.ExecutingScreen
import gh.ug.kasacore.ui.HomeScreen
import gh.ug.kasacore.ui.ListeningScreen
import gh.ug.kasacore.ui.PinHandoffScreen
import gh.ug.kasacore.ui.ResultScreen
import gh.ug.kasacore.ui.SymbolBoardScreen
import gh.ug.kasacore.ui.theme.KasaTheme

/*
 * The single Activity. Screens are driven entirely by KasaViewModel.screen —
 * see 03_FRONTEND_ANDROID_GUIDE.md Step 4 for the flow this dispatches.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: KasaViewModel by viewModels()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Home screen re-checks lazily; nothing to do here for the prototype. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE))

        setContent {
            KasaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val screen by viewModel.screen.collectAsState()
                    val caption by viewModel.caption.collectAsState()
                    when (val s = screen) {
                        is KasaScreen.Home -> HomeScreen(
                            onSpeak = { ensureRecordAudioThen { viewModel.startListening() } },
                            onSymbolBoard = { viewModel.openSymbolBoard() },
                            onAddPayee = { viewModel.openAddPayee() },
                            onOnboarding = { viewModel.openOnboarding() },
                        )
                        is KasaScreen.Listening -> ListeningScreen(
                            onStop = { viewModel.stopListeningAndUnderstand() },
                        )
                        is KasaScreen.Confirm -> ConfirmScreen(
                            summary = s.summary,
                            onApprove = { viewModel.approve(s.intent) },
                            onCancel = { viewModel.cancel() },
                        )
                        is KasaScreen.Executing -> ExecutingScreen(caption = caption)
                        is KasaScreen.PinHandoff -> PinHandoffScreen(caption = s.caption)
                        is KasaScreen.Result -> ResultScreen(
                            caption = s.caption, isError = false, onBackHome = { viewModel.backToHome() },
                        )
                        is KasaScreen.ErrorScreen -> ResultScreen(
                            caption = s.message, isError = true, onBackHome = { viewModel.backToHome() },
                        )
                        is KasaScreen.SymbolBoard -> SymbolBoardScreen(
                            onPick = { action, amount, number -> viewModel.symbolTapped(action, amount, number) },
                            onBack = { viewModel.backToHome() },
                        )
                        is KasaScreen.AddPayee -> AddPayeeScreen(
                            onSave = { name, number -> viewModel.savePayee(name, number) },
                            onCancel = { viewModel.backToHome() },
                        )
                        is KasaScreen.Onboarding -> AccessibilityOnboardingScreen(
                            onOpenSettings = { startActivity(AndroidIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            onBack = { viewModel.backToHome() },
                        )
                    }
                }
            }
        }
    }

    private fun ensureRecordAudioThen(action: () -> Unit) {
        // Permission was requested at launch; if the user denied it, Android will
        // simply no-op the recorder. A production build should show a rationale here.
        action()
    }
}
