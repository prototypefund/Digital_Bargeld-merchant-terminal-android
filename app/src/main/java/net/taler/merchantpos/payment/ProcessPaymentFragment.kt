package net.taler.merchantpos.payment

import android.annotation.SuppressLint
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
import kotlinx.android.synthetic.main.fragment_process_payment.*
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.NfcManager.Companion.hasNfc
import net.taler.merchantpos.QrCodeManager.makeQrCode
import net.taler.merchantpos.R

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
        val introRes =
            if (hasNfc(requireContext())) R.string.payment_intro_nfc else R.string.payment_intro
        textView2.setText(introRes)
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
        @SuppressLint("SetTextI18n")
        text_view_amount.text = "${payment.order.totalAsString} ${payment.currency}"
        text_view_order_reference.text = getString(R.string.payment_order_ref, payment.orderId)
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

}
