package net.taler.merchantpos.payment

import android.graphics.Bitmap
import android.graphics.Bitmap.Config.RGB_565
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.google.zxing.BarcodeFormat.QR_CODE
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.android.synthetic.main.fragment_process_payment.*
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.order.getTotalAsString

class ProcessPaymentFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private val paymentManager by lazy { model.paymentManager }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_process_payment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        paymentManager.payment.observe(viewLifecycleOwner, Observer { payment ->
            onPaymentStateChanged(payment)
        })
        button_cancel_payment.setOnClickListener {
            onPaymentCancel()
        }
    }

    private fun onPaymentStateChanged(payment: Payment) {
        if (payment.error) {
            Snackbar.make(view!!, "Network Error", LENGTH_SHORT).show()
            return
        }
        if (payment.paid) {
            findNavController().navigate(R.id.action_processPayment_to_paymentSuccess)
            model.orderManager.restartOrUndo()
            return
        }
        text_view_amount.text = "${payment.order.getTotalAsString()} ${payment.currency}"
        text_view_order_reference.text = "Order Reference: ${payment.orderId}"
        payment.talerPayUri?.let {
            val qrcodeBitmap = makeQrCode(it)
            qrcode.setImageBitmap(qrcodeBitmap)
        }
    }

    private fun onPaymentCancel() {
        paymentManager.cancelPayment()
        findNavController().popBackStack()
        Snackbar.make(view!!, "Payment Canceled", LENGTH_SHORT).show()
    }

    private fun makeQrCode(text: String): Bitmap {
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix: BitMatrix = qrCodeWriter.encode(text, QR_CODE, 256, 256)
        val height = bitMatrix.height
        val width = bitMatrix.width
        val bmp = Bitmap.createBitmap(width, height, RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) BLACK else WHITE)
            }
        }
        return bmp
    }

}
