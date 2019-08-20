package net.taler.merchantpos


import android.util.ArrayMap
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import org.json.JSONObject

class MerchantInternalRequest(
    method: Int,
    private val merchantConfig: MerchantConfig,
    endpoint: String,
    params: Map<String, String>?,
    jsonRequest: JSONObject?,
    listener: Response.Listener<JSONObject>,
    errorListener: Response.ErrorListener
) :
    JsonObjectRequest(method, merchantConfig.urlFor(endpoint, params), jsonRequest, listener, errorListener) {

    override fun getHeaders(): MutableMap<String, String> {
        val headerMap = ArrayMap<String, String>()
        headerMap["Authorization"] = "ApiKey " + merchantConfig.apiKey
        return headerMap
    }
}