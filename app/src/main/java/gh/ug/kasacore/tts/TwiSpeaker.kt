package gh.ug.kasacore.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Speaks prompts and results aloud. WCAG 1.3.3 rule for this app: every spoken
 * string here MUST also be rendered as an on-screen caption by the caller —
 * TwiSpeaker never carries information the UI doesn't also show.
 *
 * KNOWN RISK, flagged honestly rather than hidden: Android's TextToSpeech has
 * no guaranteed Twi (tw-GH) voice. We request it and fall back to the device
 * default if unavailable. This MUST be verified on the two test phones
 * (docs/08_K11_K13_SPIKE.md) — if no Twi voice exists on a given phone, Kelvin's
 * K33 pre-recorded prompt pack is the fallback for fixed strings, and free-form
 * results (amounts, names) will read in whatever voice is available.
 */
class TwiSpeaker(context: Context) {

    private var ready = false
    private val queueBeforeReady = mutableListOf<Pair<String, Boolean>>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val twi = Locale.Builder().setLanguage("tw").setRegion("GH").build()
            val result = ttsInstanceRef?.setLanguage(twi)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsInstanceRef?.language = Locale.getDefault() // honest fallback, see class doc
            }
            ready = true
            queueBeforeReady.forEach { (text, append) -> speakNow(text, append) }
            queueBeforeReady.clear()
        }
    }

    private val ttsInstanceRef: TextToSpeech? get() = tts

    /** append = true waits for whatever is being said; false interrupts it (a new screen replaces the old). */
    fun speak(text: String, append: Boolean = false) {
        if (ready) speakNow(text, append) else queueBeforeReady.add(text to append)
    }

    private fun speakNow(text: String, append: Boolean) {
        val mode = if (append) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
        tts.speak(text, mode, null, UUID.randomUUID().toString())
    }

    fun onUtteranceDone(callback: () -> Unit) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { callback() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { callback() }
        })
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
