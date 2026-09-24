package gh.ug.kasacore.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkTest {

    @Test fun mtnPrefixes() {
        for (p in listOf("024", "025", "053", "054", "055", "059")) {
            assertEquals(p, Network.MTN, Network.fromNumber(p + "1234567"))
        }
    }

    @Test fun telecelPrefixes() {
        for (p in listOf("020", "050")) assertEquals(p, Network.TELECEL, Network.fromNumber(p + "1234567"))
    }

    @Test fun atPrefixes() {
        for (p in listOf("026", "027", "056", "057")) assertEquals(p, Network.AT, Network.fromNumber(p + "1234567"))
    }

    @Test fun unsupportedOrMalformedNumbersAreNull() {
        assertNull(Network.fromNumber("0231234567"))   // not one of the supported prefixes
        assertNull(Network.fromNumber("024123456"))    // 9 digits
        assertNull(Network.fromNumber("02412345678"))  // 11 digits
        assertNull(Network.fromNumber(""))
        assertNull(Network.fromNumber(null))
    }

    @Test fun atIsSpelledOutForSpeechButNothingElseChanges() {
        assertEquals("Send GH₵5 to 0261234567 on A T, reference 7.", "Send GH₵5 to 0261234567 on AT, reference 7.".spokenNetworkNames())
        assertEquals("1) A T", "1) AT".spokenNetworkNames())
        assertEquals("MTN, Telecel", "MTN, Telecel".spokenNetworkNames())
        assertEquals("Transfer GHATANA", "Transfer GHATANA".spokenNetworkNames()) // only the standalone word
    }

    @Test fun internationalFormatIsNormalisedToLocal() {
        assertEquals("0201234567", "233201234567".toGhanaLocalNumber())
        assertEquals(Network.TELECEL, Network.fromNumber("233201234567"))
        assertEquals("0244123456", "0244123456".toGhanaLocalNumber())
    }
}
