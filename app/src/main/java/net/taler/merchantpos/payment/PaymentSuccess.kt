package net.taler.merchantpos.payment


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import net.taler.merchantpos.R

/**
 * A simple [Fragment] subclass.
 */
class PaymentSuccess : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_payment_success, container, false)
        view.findViewById<Button>(R.id.button_success_back).setOnClickListener {
            activity!!.findNavController(R.id.nav_host_fragment).navigateUp()
        }
        return view
    }


}