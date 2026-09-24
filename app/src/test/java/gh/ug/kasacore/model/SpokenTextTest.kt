package gh.ug.kasacore.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTextTest {

    @Test fun phoneNumbersAreReadDigitByDigit() {
        assertEquals("0 2 4 4 1 2 3 4 5 6", "0244123456".spokenNumbers())
        assertEquals("Entering recipient number 0 2 6 1 2 3 4 5 6 7", "Entering recipient number 0261234567".spokenNumbers())
    }

    @Test fun shortNumbersAndAmountsAreLeftAlone() {
        assertEquals("Entering amount 10", "Entering amount 10".spokenNumbers())
        assertEquals("GHS 1.00", "GHS 1.00".spokenNumbers())
    }

    @Test fun forSpeechAppliesBothRules() {
        assertEquals(
            "Send GH\u20B55 to 0 2 6 1 2 3 4 5 6 7 on A T",
            "Send GH\u20B55 to 0261234567 on AT".forSpeech(),
        )
    }
}
