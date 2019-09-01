package net.taler.merchantpos

import android.net.Uri

data class MerchantConfig(val baseUrl: String, val instance: String, val apiKey: String) {
    fun urlFor(endpoint: String, params: Map<String, String>?): String {
        val uriBuilder = Uri.parse(baseUrl).buildUpon()
        uriBuilder.appendPath(endpoint)
        params?.forEach {
            uriBuilder.appendQueryParameter(it.key, it.value)
        }
        return uriBuilder.toString()
    }
}