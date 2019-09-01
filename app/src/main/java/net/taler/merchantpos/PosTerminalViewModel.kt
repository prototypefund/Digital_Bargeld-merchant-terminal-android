package net.taler.merchantpos

import android.text.Editable
import androidx.lifecycle.ViewModel

class PosTerminalViewModel : ViewModel() {
    var activeSubject: Editable? = null
    var merchantConfig: MerchantConfig? = null
    var activeOrderId: String? = null
    var activeAmount: String? = null
    var activeTalerPayUri: String? = null

    fun activeAmountPretty(): String? {
        val amount = activeAmount ?: return null
        val components = amount.split(":")
        return "${components[1]} ${components[0]}"
    }
}
