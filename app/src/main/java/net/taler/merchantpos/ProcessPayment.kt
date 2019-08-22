package net.taler.merchantpos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import android.opengl.ETC1.getWidth
import android.opengl.ETC1.getHeight
import android.os.Handler
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.Volley
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject
import java.net.URLEncoder


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 *
 */
class ProcessPayment : Fragment() {

    private var paused: Boolean = true
    private lateinit var queue: RequestQueue
    private lateinit var model: PosTerminalViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = activity?.run {
            ViewModelProviders.of(this)[PosTerminalViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        queue = Volley.newRequestQueue(context)

    }

    private fun onCheckPayment(checkPaymentResponse: JSONObject) {
        if (paused) {
            return
        }
        //Log.v("taler-merchant", "got check payment result ${checkPaymentResponse}")
        if (checkPaymentResponse.getBoolean("paid")) {
            queue.cancelAll { true }
            findNavController().navigate(R.id.action_processPayment_to_paymentSuccess)
            return
        }
    }

    private fun onNetworkError(volleyError: VolleyError?) {
        val mySnackbar = Snackbar.make(view!!, "Network Error", Snackbar.LENGTH_SHORT)
        mySnackbar.show()
    }

    private fun checkPaid() {
        if (paused) {
            return
        }
        //Log.v("taler-merchant", "checkig if payment happened")
        val params = mapOf("order_id" to model.activeOrderId!!, "instance" to model.merchantConfig!!.instance)
        var req = MerchantInternalRequest(Request.Method.GET, model.merchantConfig!!, "check-payment", params, null,
            Response.Listener { onCheckPayment(it) }, Response.ErrorListener { onNetworkError(it) })
        queue.add(req)
        val handler = Handler()
        handler.postDelayed({
            checkPaid()
        }, 500)

    }

    override fun onResume() {
        this.paused = false
        checkPaid()
        super.onResume()
    }

    override fun onPause() {
        this.paused = true
        super.onPause()
        queue.cancelAll { true }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_process_payment, container, false)
        val img = view.findViewById<ImageView>(R.id.qrcode)
        val talerPayUrl = "talerpay:" + URLEncoder.encode(model.activeContractUri!!, "utf-8")
        val myBitmap = makeQrCode(talerPayUrl)
        img.setImageBitmap(myBitmap)
        val cancelPaymentButton = view.findViewById<Button>(R.id.button_cancel_payment)
        cancelPaymentButton.setOnClickListener {
            onPaymentCancel()
        }
        val textViewAmount = view.findViewById<TextView>(R.id.text_view_amount)
        textViewAmount.text = model.activeAmountPretty()
        val textViewOrderId = view.findViewById<TextView>(R.id.text_view_order_reference)
        textViewOrderId.text = "Order Reference: " + model.activeOrderId
        return view
    }

    private fun onPaymentCancel() {
        val navController = findNavController()
        navController.popBackStack()

        val mySnackbar = Snackbar.make(view!!, "Payment Canceled", Snackbar.LENGTH_SHORT)
        mySnackbar.show()
    }

    fun makeQrCode(text: String): Bitmap {
        val qrCodeWriter: QRCodeWriter = QRCodeWriter()
        val bitMatrix: BitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 256, 256)
        val height = bitMatrix.height
        val width = bitMatrix.width
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
