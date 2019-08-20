package net.taler.merchantpos

import androidx.lifecycle.ViewModel

class PosTerminalViewModel : ViewModel() {
    var merchantConfig: MerchantConfig? = null
    var activeOrderId: String? = null
    var activeAmount: String? = null
    var activeContractUri: String? = null

    fun activeAmountPretty(): String? {
        val a = activeAmount ?: return null
        return a.replace(":", " ")
    }
}