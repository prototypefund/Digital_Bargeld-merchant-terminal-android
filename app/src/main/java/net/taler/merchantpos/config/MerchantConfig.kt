package net.taler.merchantpos.config

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty

data class Config(
    val configUrl: String,
    val username: String,
    val password: String
) {
    fun isValid() = !configUrl.isBlank()
    fun hasPassword() = !password.isBlank()
}

data class MerchantConfig(
    @JsonProperty("base_url")
    val baseUrl: String,
    val instance: String,
    @JsonProperty("api_key")
    val apiKey: String,
    val currency: String?
) {
    fun urlFor(endpoint: String, params: Map<String, String>?): String {
        val uriBuilder = Uri.parse(baseUrl).buildUpon()
        uriBuilder.appendPath(endpoint)
        params?.forEach {
            uriBuilder.appendQueryParameter(it.key, it.value)
        }
        return uriBuilder.toString()
    }
}
