package gh.ug.kasacore.ussd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeService : UssdService {
    val actions = mutableListOf<String>()
    var hasField = true
    private var handler: ((String) -> Unit)? = null
    override fun dial(code: String) { actions += "dial:$code" }
    override fun onDialog(handler: (String) -> Unit) { this.handler = handler }
    override fun inject(text: String) { actions += "inject:$text" }
    override fun choose(option: String) { actions += "choose:$option" }
    override fun hasInput() = hasField
    override fun dismiss() { actions += "dismiss" }
    override fun skip() { actions += "skip" }
    override fun end() { actions += "end" }
    fun show(text: String) = handler!!.invoke(text)
}

private class RecordingListener : UssdListener {
    var pinRequests = 0
    val errors = mutableListOf<String>()
    override fun onMenuRead(text: String) {}
    override fun onPinRequired() { pinRequests++ }
    override fun onSuccess(resultText: String) {}
    override fun onError(reason: String) { errors += reason }
}

/** Mirrors send_money in config/ussd_scripts.json; screen text is from the K10 SIM captures. */
class UssdNavigatorRoutingTest {

    private val sendMoney = Flow(
        "send_money", true,
        listOf(
            Step("main_menu", chooseLabel = listOf("transfer money"), fallbackOption = "1"),
            Step("transfer_menu", chooseLabel = listOf("momo user"), fallbackOption = "1", unlessSlot = "other_network"),
            Step("transfer_menu_other_networks", chooseLabel = listOf("other networks"), fallbackOption = "5",
                expect = listOf("other networks"), whenSlot = "other_network"),
            Step("other_network_at", chooseLabel = listOf("at"), fallbackOption = "1",
                expect = listOf("other network"), whenSlot = "at"),
            Step("other_network_telecel", chooseLabel = listOf("telecel"), fallbackOption = "2",
                expect = listOf("other network"), whenSlot = "telecel"),
            Step("recipient", input = "{recipient_number}", expect = listOf("mobile number")),
            Step("confirm_number", input = "{recipient_number}", expect = listOf("confirm", "re-enter", "reenter")),
            Step("amount", input = "{amount}", expect = listOf("amount")),
            Step("reference", input = "{reference}", expect = listOf("reference")),
        ),
        true,
    )
    private val detect = Detect(
        pin = listOf("enter mm pin"), success = listOf("successful"),
        failure = listOf("incorrect"), timeout = listOf("timed out"),
    )

    private val main = "Unlock more deals, try our new MoMo App\n1) Transfer Money\n2) MoMoPay&Pay Bill\n" +
        "3) Airtime&Bundles\n4) Allow Cash Out\n5) Financial\n# for next"
    private val transfer = "More offers await on the new MoMo App\n1) MoMo User\n2) Non Momo User\n3) Send with Care\n" +
        "4) Favorite\n5) Other Networks\n6) Bank Account\n# for next"
    private val otherNetworks = "Transfer Money to other Network\n1) AT\n2) Telecel\n3) E-zwich\n4) G-Money\n" +
        "5) Zeepay\n6) GhanaPay\n0) Back"
    private val pinScreen = "Transfer to KOFI for GHS 5 with Reference: 7. Fee is GHS 0.00. Enter MM PIN or 2 to cancel."

    private fun start(service: FakeService, listener: RecordingListener, slots: Map<String, String>) =
        UssdNavigator(mapOf("send_money" to sendMoney), detect, service, listener).run("send_money", slots)

    private val common = mapOf("recipient_number" to "0244123456", "amount" to "5", "reference" to "7")

    @Test fun mtnNumberGoesThroughMoMoUser() {
        val svc = FakeService(); val l = RecordingListener()
        start(svc, l, common)
        svc.show(main); svc.show(transfer)
        svc.show("Enter mobile number"); svc.show("Confirm Number"); svc.show("Enter Amount"); svc.show("Enter Reference")
        svc.show(pinScreen)
        assertEquals(
            listOf("dial:*170#", "choose:1", "choose:1", "inject:0244123456", "inject:0244123456", "inject:5", "inject:7"),
            svc.actions,
        )
        assertEquals(1, l.pinRequests)
    }

    @Test fun telecelNumberGoesThroughOtherNetworksThenTelecel() {
        val svc = FakeService(); val l = RecordingListener()
        start(svc, l, common + mapOf("recipient_number" to "0201234567", "other_network" to "true", "telecel" to "true"))
        svc.show(main); svc.show(transfer); svc.show(otherNetworks)
        svc.show("Enter mobile number"); svc.show("Confirm Number"); svc.show("Enter Amount"); svc.show("Enter Reference")
        assertEquals(
            listOf("dial:*170#", "choose:1", "choose:5", "choose:2",
                "inject:0201234567", "inject:0201234567", "inject:5", "inject:7"),
            svc.actions,
        )
    }

    @Test fun otherNetworkConfirmScreenWithDifferentWordingStillGetsTheNumber() {
        val svc = FakeService(); val l = RecordingListener()
        start(svc, l, common + mapOf("recipient_number" to "0261234567", "other_network" to "true", "at" to "true"))
        svc.show(main); svc.show(transfer); svc.show(otherNetworks)
        svc.show("Enter mobile number"); svc.show("Confirm Mobile Number")
        assertEquals(
            listOf("dial:*170#", "choose:1", "choose:5", "choose:1", "inject:0261234567", "inject:0261234567"),
            svc.actions,
        )
    }

    @Test fun atNumberPicksAtNotTelecel() {
        val svc = FakeService(); val l = RecordingListener()
        start(svc, l, common + mapOf("recipient_number" to "0261234567", "other_network" to "true", "at" to "true"))
        svc.show(main); svc.show(transfer); svc.show(otherNetworks)
        assertEquals(listOf("dial:*170#", "choose:1", "choose:5", "choose:1"), svc.actions)
    }

    @Test fun screenWithoutAReplyFieldDoesNotConsumeAStep() {
        val svc = FakeService(); val l = RecordingListener()
        start(svc, l, common)
        svc.hasField = false
        svc.show("USSD code running...")
        assertEquals(listOf("dial:*170#"), svc.actions)
        svc.hasField = true
        svc.show(main)
        assertEquals(listOf("dial:*170#", "choose:1"), svc.actions)
    }

    @Test fun inputStepWaitsForItsOwnPrompt() {
        val svc = FakeService(); val l = RecordingListener()
        start(svc, l, common)
        svc.show(main); svc.show(transfer)
        svc.show(transfer) // a stray repeat of the previous menu must not get the recipient number typed into it
        assertTrue(svc.actions.none { it.startsWith("inject:") })
    }
}
