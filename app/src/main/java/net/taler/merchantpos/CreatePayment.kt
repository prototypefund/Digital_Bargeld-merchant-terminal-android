package net.taler.merchantpos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.Volley
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject


/**
 * Fragment that allows the merchant to create a payment.
 */
class CreatePayment : Fragment() {
    private lateinit var queue: RequestQueue
    private lateinit var model: PosTerminalViewModel

    private var paused: Boolean = false


    override fun onPause() {
        super.onPause()
        this.paused = true
    }

    override fun onResume() {
        super.onResume()
        this.paused = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = activity?.run {
            ViewModelProviders.of(this)[PosTerminalViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        queue = Volley.newRequestQueue(context)
    }

    private fun onRequestPayment() {
        val amountValStr = activity!!.findViewById<EditText>(R.id.edit_payment_amount).text
        val amount = "TESTKUDOS:${amountValStr}"
        model.activeAmount = amount
        model.activeSubject = activity!!.findViewById<EditText>(R.id.edit_payment_subject).text

        val order = JSONObject().also {
            it.put("amount", amount)
            it.put("summary", model.activeSubject!!)
            it.put("fulfillment_url", "https://example.com")
            it.put("instance", "default")
        }

        val reqBody = JSONObject().also { it.put("order", order) }

        val req = MerchantInternalRequest(
            Request.Method.POST,
            model.merchantConfig!!,
            "order",
            null,
            reqBody,
            Response.Listener { onOrderCreated(it) },
            Response.ErrorListener { onNetworkError(it) })

        queue.add(req)
    }

    private fun onNetworkError(volleyError: VolleyError?) {
        val mySnackbar = Snackbar.make(view!!, "Network Error", Snackbar.LENGTH_SHORT)
        mySnackbar.show()
    }

    private fun onOrderCreated(orderResponse: JSONObject) {
        val merchantConfig = model.merchantConfig!!
        val orderId = orderResponse.getString("order_id")
        val params = mapOf("order_id" to orderId, "instance" to merchantConfig.instance)
        model.activeOrderId = orderId

        val req = MerchantInternalRequest(Request.Method.GET, model.merchantConfig!!, "check-payment", params, null,
            Response.Listener { onCheckPayment(it) }, Response.ErrorListener { onNetworkError(it) })
        queue.add(req)
    }

    /**
     * Called when the /check-payment response gave a result.
     */
    private fun onCheckPayment(checkPaymentResponse: JSONObject) {
        if (paused) {
            return
        }
        if (checkPaymentResponse.getBoolean("paid")) {
            val mySnackbar = Snackbar.make(view!!, "Already paid?!", Snackbar.LENGTH_SHORT)
            mySnackbar.show()
            return
        }
        model.activeTalerPayUri = checkPaymentResponse.getString("taler_pay_uri")
        findNavController().navigate(R.id.action_createPayment_to_processPayment)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_create_payment, container, false)
        val requestPaymentButton = view.findViewById<Button>(R.id.button_request_payment);
        requestPaymentButton.setOnClickListener {
            onRequestPayment()
        }
        return view
    }

}
