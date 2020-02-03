package net.taler.merchantpos.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_order.*
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.order.RestartState.ENABLED
import net.taler.merchantpos.order.RestartState.UNDO

class OrderFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val orderManager by lazy { viewModel.orderManager }
    private val paymentManager by lazy { viewModel.paymentManager }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_order, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        restartButton.setOnClickListener { orderManager.restartOrUndo() }
        orderManager.restartState.observe(viewLifecycleOwner, Observer { state ->
            if (state == UNDO) {
                restartButton.setText(R.string.order_undo)
                restartButton.isEnabled = true
                completeButton.isEnabled = false
            } else {
                restartButton.setText(R.string.order_restart)
                restartButton.isEnabled = state == ENABLED
                completeButton.isEnabled = state == ENABLED
            }
        })
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val nav: NavController = findNavController(requireActivity(), R.id.nav_host_fragment)
        reconfigureButton.setOnClickListener { nav.navigate(R.id.action_order_to_merchantSettings) }
        historyButton.setOnClickListener { nav.navigate(R.id.action_order_to_merchantHistory) }
        logoutButton.setOnClickListener { nav.navigate(R.id.action_order_to_merchantSettings) }
        completeButton.setOnClickListener {
            val order = orderManager.order.value ?: return@setOnClickListener
            paymentManager.createPayment(order)
            nav.navigate(R.id.action_order_to_processPayment)
        }
    }

    override fun onStart() {
        super.onStart()
        if (viewModel.configManager.needsConfig()) {
            findNavController().navigate(R.id.action_global_merchantSettings)
        }
    }

}
