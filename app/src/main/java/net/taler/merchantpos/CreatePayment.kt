package net.taler.merchantpos

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private var listener: OnFragmentInteractionListener? = null
    private lateinit var queue: RequestQueue
    private lateinit var model: PosTerminalViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

        model = activity?.run {
            ViewModelProviders.of(this)[PosTerminalViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        queue = Volley.newRequestQueue(context)
    }

    private fun onRequestPayment() {
        val amount = "TESTKUDOS:10.00"
        model.activeAmount = amount
        var order = JSONObject().also {
            it.put("amount", amount)
            it.put("summary", "hello world")
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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException(context.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     *
     *
     * See the Android Training lesson [Communicating with Other Fragments]
     * (http://developer.android.com/training/basics/fragments/communicating.html)
     * for more information.
     */
    interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        fun onFragmentInteraction(uri: Uri)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment CreatePayment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            CreatePayment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
