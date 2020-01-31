package net.taler.merchantpos

import android.app.Application
import android.text.Editable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.volley.toolbox.Volley
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.taler.merchantpos.config.ConfigManager
import net.taler.merchantpos.order.OrderManager

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val mapper = ObjectMapper()
        .registerModule(KotlinModule())
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val queue = Volley.newRequestQueue(app)

    val orderManager = OrderManager(mapper)
    val configManager = ConfigManager(app, viewModelScope, mapper, queue).apply {
        addConfigurationReceiver(orderManager)
    }

    val merchantConfig
        get() = configManager.merchantConfig

    var activeSubject: Editable? = null
    var activeOrderId: String? = null
    var activeAmount: String? = null
    var activeTalerPayUri: String? = null

    init {
        if (configManager.merchantConfig == null) {
            configManager.fetchConfig(configManager.config, false)
        }
    }

    override fun onCleared() {
        queue.cancelAll { !it.isCanceled }
    }

    fun activeAmountPretty(): String? {
        val amount = activeAmount ?: return null
        val components = amount.split(":")
        return "${components[1]} ${components[0]}"
    }

}
