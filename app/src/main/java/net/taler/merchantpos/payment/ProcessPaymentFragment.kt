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
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import kotlinx.android.synthetic.main.fragment_process_payment.*
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.NfcManager.Companion.hasNfc
import net.taler.merchantpos.QrCodeManager.makeQrCode
import net.taler.merchantpos.R
import net.taler.merchantpos.fadeIn
import net.taler.merchantpos.fadeOut
import net.taler.merchantpos.payment.ProcessPaymentFragmentDirections.Companion.actionProcessPaymentToPaymentSuccess
import net.taler.merchantpos.topSnackbar

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
        payIntroView.setText(introRes)
        paymentManager.payment.observe(viewLifecycleOwner, Observer { payment ->
            onPaymentStateChanged(payment)
        })
        cancelPaymentButton.setOnClickListener {
            onPaymentCancel()
        }
    }

    private fun onPaymentStateChanged(payment: Payment) {
        if (payment.error) {
            topSnackbar(view!!, R.string.error_network, LENGTH_LONG)
            findNavController().navigateUp()
            return
        }
        if (payment.paid) {
            model.orderManager.onOrderPaid(payment.order.id)
            actionProcessPaymentToPaymentSuccess().let {
                findNavController().navigate(it)
            }
            return
        }
        payIntroView.fadeIn()
        @SuppressLint("SetTextI18n")
        amountView.text = "${payment.order.totalAsString} ${payment.currency}"
        payment.orderId?.let {
            orderRefView.text = getString(R.string.payment_order_ref, it)
            orderRefView.fadeIn()
        }
        payment.talerPayUri?.let {
            val qrcodeBitmap = makeQrCode(it)
            qrcodeView.setImageBitmap(qrcodeBitmap)
            qrcodeView.fadeIn()
            progressBar.fadeOut()
        }
    }

    private fun onPaymentCancel() {
        paymentManager.cancelPayment()
        findNavController().navigateUp()
        topSnackbar(view!!, R.string.payment_canceled, LENGTH_LONG)
    }

}
