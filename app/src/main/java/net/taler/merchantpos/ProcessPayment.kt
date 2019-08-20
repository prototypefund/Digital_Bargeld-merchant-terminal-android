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
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [ProcessPayment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [ProcessPayment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class ProcessPayment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private var listener: OnFragmentInteractionListener? = null

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

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_process_payment, container, false)
        val img = view.findViewById<ImageView>(R.id.qrcode)
        val myBitmap = makeQrCode(model.activeContractUri!!)
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
        return bmp;
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
         * @return A new instance of fragment ProcessPayment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            ProcessPayment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
