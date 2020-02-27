package net.taler.merchantpos.config

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Base64.NO_WRAP
import android.util.Base64.encodeToString
import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.volley.Request.Method.GET
import com.android.volley.RequestQueue
import com.android.volley.Response.ErrorListener
import com.android.volley.Response.Listener
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val SETTINGS_NAME = "taler-merchant-terminal"

private const val SETTINGS_CONFIG_URL = "configUrl"
private const val SETTINGS_USERNAME = "username"
private const val SETTINGS_PASSWORD = "password"

internal const val CONFIG_URL_DEMO = "https://grobox.de/taler/pos.json"
internal const val CONFIG_USERNAME_DEMO = "torsten"
internal const val CONFIG_PASSWORD_DEMO = "test"

private val TAG = ConfigManager::class.java.simpleName

interface ConfigurationReceiver {
    /**
     * Returns true if the configuration was valid, false otherwise.
     */
    suspend fun onConfigurationReceived(json: JSONObject, currency: String): Boolean
}

class ConfigManager(
    context: Context,
    private val scope: CoroutineScope,
    private val mapper: ObjectMapper,
    private val queue: RequestQueue
) {

    private val prefs = context.getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE)
    private val configurationReceivers = ArrayList<ConfigurationReceiver>()

    var config = Config(
        configUrl = prefs.getString(SETTINGS_CONFIG_URL, "")!!,
        username = prefs.getString(SETTINGS_USERNAME, "")!!,
        password = prefs.getString(SETTINGS_PASSWORD, "")!!
    )
    var merchantConfig: MerchantConfig? = null
        private set

    private val mConfigUpdateResult = MutableLiveData<ConfigUpdateResult>()
    val configUpdateResult: LiveData<ConfigUpdateResult> = mConfigUpdateResult

    fun addConfigurationReceiver(receiver: ConfigurationReceiver) {
        configurationReceivers.add(receiver)
    }

    /**
     * Returns true if the user needs to provide more configuration
     * and false if the configuration is sufficient to continue.
     */
    fun needsConfig(): Boolean {
        return !config.isValid() || (!config.hasPassword() && merchantConfig == null)
    }

    @UiThread
    fun fetchConfig(config: Config, save: Boolean, savePassword: Boolean = false) {
        mConfigUpdateResult.value = null
        val configToSave = if (save) {
            if (savePassword) config else config.copy(password = "")
        } else null

        val stringRequest = object : JsonObjectRequest(GET, config.configUrl, null,
            Listener { onConfigReceived(it, configToSave) },
            ErrorListener { onNetworkError(it) }
        ) {
            // send basic auth header
            override fun getHeaders(): MutableMap<String, String> {
                val credentials = "${config.username}:${config.password}"
                val auth = ("Basic ${encodeToString(credentials.toByteArray(), NO_WRAP)}")
                return mutableMapOf("Authorization" to auth)
            }
        }
        queue.add(stringRequest)
    }

    private fun onConfigReceived(json: JSONObject, config: Config?) {
        val merchantConfig: MerchantConfig = try {
            mapper.readValue(json.getString("config"))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing merchant config", e)
            mConfigUpdateResult.value = ConfigUpdateResult(null)
            return
        }

        val params = mapOf("instance" to merchantConfig.instance)
        val req = MerchantRequest(GET, merchantConfig, "config", params, null,
            Listener { onMerchantConfigReceived(config, json, merchantConfig, it) },
            ErrorListener { onNetworkError(it) }
        )
        queue.add(req)
    }

    private fun onMerchantConfigReceived(
        newConfig: Config?,
        configJson: JSONObject,
        merchantConfig: MerchantConfig,
        json: JSONObject
    ) = scope.launch(Dispatchers.Main) {
        val currency = json.getString("currency")

        var configValid = true
        configurationReceivers.forEach {
            val result = try {
                it.onConfigurationReceived(configJson, currency)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling configuration by ${it::class.java.simpleName}", e)
                false
            }
            configValid = result && configValid
        }
        if (configValid) {
            newConfig?.let {
                config = it
                saveConfig(it)
            }
            this@ConfigManager.merchantConfig = merchantConfig.copy(currency = currency)
            mConfigUpdateResult.value = ConfigUpdateResult(currency)
        } else {
            mConfigUpdateResult.value = ConfigUpdateResult(null)
        }
    }

    fun forgetPassword() {
        config = config.copy(password = "")
        saveConfig(config)
        merchantConfig = null
    }

    private fun saveConfig(config: Config) {
        prefs.edit()
            .putString(SETTINGS_CONFIG_URL, config.configUrl)
            .putString(SETTINGS_USERNAME, config.username)
            .putString(SETTINGS_PASSWORD, config.password)
            .apply()
    }

    private fun onNetworkError(it: VolleyError?) {
        val authError = it?.networkResponse?.statusCode == 401
        mConfigUpdateResult.value = ConfigUpdateResult(null, authError)
    }

}

class ConfigUpdateResult(val currency: String?, val authError: Boolean = false) {
    val error: Boolean = currency == null
}
