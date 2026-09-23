package gh.ug.kasacore.model

/** An on-device trusted payee. Per K05 Decision 3, this never leaves the phone. */
data class Payee(
    val name: String,
    val number: String,
)
