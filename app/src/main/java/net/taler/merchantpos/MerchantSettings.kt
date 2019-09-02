package net.taler.merchantpos

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProviders
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.Volley
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject


/**
 * Fragment that displays merchant settings.
 */
class MerchantSettings : Fragment() {

    private lateinit var queue: RequestQueue
    private lateinit var model: PosTerminalViewModel

    private var newConfig: MerchantConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = activity?.run {
            ViewModelProviders.of(this)[PosTerminalViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        queue = Volley.newRequestQueue(context)
    }

    private fun reset(view: View) {
        val backendUrlEdit = view.findViewById<EditText>(R.id.edit_settings_backend_url)
        backendUrlEdit.setText(model.merchantConfig!!.baseUrl, TextView.BufferType.EDITABLE)

        val backendInstanceEdit = view.findViewById<EditText>(R.id.edit_settings_instance)
        backendInstanceEdit.setText(model.merchantConfig!!.instance, TextView.BufferType.EDITABLE)

        val backendApiKeyEdit = view.findViewById<EditText>(R.id.edit_settings_apikey)
        backendApiKeyEdit.setText(model.merchantConfig!!.apiKey, TextView.BufferType.EDITABLE)

        val currencyView = view.findViewById<TextView>(R.id.text_settings_currency)
        currencyView.text = model.merchantConfig!!.currency
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_merchant_settings, container, false)

        reset(view)

        val buttonApply = view.findViewById<Button>(R.id.button_settings_apply)
        buttonApply.setOnClickListener {

            val backendUrlEdit = view.findViewById<EditText>(R.id.edit_settings_backend_url)
            val backendInstanceEdit = view.findViewById<EditText>(R.id.edit_settings_instance)
            val backendApiKeyEdit = view.findViewById<EditText>(R.id.edit_settings_apikey)

            val config = MerchantConfig(
                backendUrlEdit.text.toString(),
                backendInstanceEdit.text.toString(),
                backendApiKeyEdit.text.toString(),
                "UNKNOWN"
            )

            newConfig = config

            val req = MerchantInternalRequest(
                Request.Method.GET,
                config,
                "config",
                mapOf("instance" to config.instance),
                null,
                Response.Listener { onConfigReceived(it) },
                Response.ErrorListener { onNetworkError(it) })

            queue.add(req)

        }

        val buttonReset = view.findViewById<Button>(R.id.button_settings_reset)
        buttonReset.setOnClickListener {
            reset(view)
        }

        return view
    }

    private fun onConfigReceived(it: JSONObject) {
        val currency = it.getString("currency")
        val mySnackbar =
            Snackbar.make(view!!, "Changed to new ${currency} merchant", Snackbar.LENGTH_SHORT)

        val config = this.newConfig!!.copy(currency = currency)
        this.newConfig = null
        model.merchantConfig = config

        val currencyView = view!!.findViewById<TextView>(R.id.text_settings_currency)
        currencyView.text = currency

        mySnackbar.show()

        val prefs = activity!!.getSharedPreferences("taler-merchant-terminal", Context.MODE_PRIVATE)
        prefs.edit().putString("merchantBackendUrl", config.baseUrl)
            .putString("merchantBackendInstance", config.instance)
            .putString("merchantBackendApiKey", config.apiKey)
            .putString("merchantBackendCurrency", config.currency)
    }

    private fun onNetworkError(it: VolleyError) {
        val mySnackbar =
            Snackbar.make(view!!, "Error: Invalid Configuration", Snackbar.LENGTH_SHORT)
        mySnackbar.show()
    }
}
