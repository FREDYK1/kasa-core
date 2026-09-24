package gh.ug.kasacore.model

/** Text-to-speech says "0244123456" as one huge number; read long digit runs (phone numbers) digit by digit. */
fun String.spokenNumbers(): String = replace(Regex("\\d{7,}")) { it.value.toList().joinToString(" ") }

/** Everything the app speaks goes through this; captions show the original text. */
fun String.forSpeech(): String = spokenNetworkNames().spokenNumbers()
