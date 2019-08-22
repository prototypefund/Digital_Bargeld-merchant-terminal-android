package net.taler.merchantpos

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import com.google.android.material.snackbar.Snackbar


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [CreatePayment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [CreatePayment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class CreatePayment : Fragment() {
    private lateinit var queue: RequestQueue
    private lateinit var model: PosTerminalViewModel

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

        var order = JSONObject().also {
            it.put("amount", amount)
            it.put("summary", model.activeSubject!!)
            it.put("fulfillment_url", "https://example.com")
            it.put("instance", "default")
        }

        var reqBody = JSONObject().also { it.put("order", order) }

        var req = MerchantInternalRequest(
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

        var req = MerchantInternalRequest(Request.Method.GET, model.merchantConfig!!, "check-payment", params, null,
            Response.Listener { onCheckPayment(it) }, Response.ErrorListener { onNetworkError(it) })
        queue.add(req)
    }

    private fun onCheckPayment(checkPaymentResponse: JSONObject) {
        if (checkPaymentResponse.getBoolean("paid")) {
            val mySnackbar = Snackbar.make(view!!, "Already paid?!", Snackbar.LENGTH_SHORT)
            mySnackbar.show()
            return
        }
        model.activeContractUri = checkPaymentResponse.getString("contract_url")
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
