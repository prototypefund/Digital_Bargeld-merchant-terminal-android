package net.taler.merchantpos

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup


/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [MerchantSettings.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [MerchantSettings.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class MerchantSettings : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_merchant_settings, container, false)
    }
}
