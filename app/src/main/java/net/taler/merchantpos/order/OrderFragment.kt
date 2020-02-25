package net.taler.merchantpos.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.transition.TransitionManager.beginDelayedTransition
import kotlinx.android.synthetic.main.fragment_order.*
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.order.OrderFragmentDirections.Companion.actionGlobalOrder
import net.taler.merchantpos.order.RestartState.ENABLED
import net.taler.merchantpos.order.RestartState.UNDO

class OrderFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val orderManager by lazy { viewModel.orderManager }
    private val paymentManager by lazy { viewModel.paymentManager }
    private val args: OrderFragmentArgs by navArgs()
    private val liveOrder by lazy { orderManager.getOrder(args.orderId) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_order, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        restartButton.setOnClickListener { liveOrder.restartOrUndo() }
        liveOrder.restartState.observe(viewLifecycleOwner, Observer { state ->
            beginDelayedTransition(view as ViewGroup)
            if (state == UNDO) {
                restartButton.setText(R.string.order_undo)
                restartButton.isEnabled = true
                completeButton.isEnabled = false
            } else {
                restartButton.setText(R.string.order_restart)
                restartButton.isEnabled = state == ENABLED
                completeButton.isEnabled = state == ENABLED
            }
            nextButton.isEnabled = state == ENABLED
        })
        minusButton.setOnClickListener { liveOrder.decreaseSelectedOrderLine() }
        plusButton.setOnClickListener { liveOrder.increaseSelectedOrderLine() }
        liveOrder.modifyOrderAllowed.observe(viewLifecycleOwner, Observer { allowed ->
            minusButton.isEnabled = allowed
            plusButton.isEnabled = allowed
        })
        orderManager.hasPreviousOrder.observe(viewLifecycleOwner, Observer { hasPreviousOrder ->
            prevButton.isEnabled = hasPreviousOrder
        })
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        liveOrder.order.observe(viewLifecycleOwner, Observer { order ->
            activity?.title = getString(R.string.order_label_title, order.title)
        })
        prevButton.setOnClickListener { orderManager.previousOrder() }
        nextButton.setOnClickListener { orderManager.nextOrder() }
        completeButton.setOnClickListener {
            val order = liveOrder.order.value ?: return@setOnClickListener
            paymentManager.createPayment(order)
            findNavController().navigate(R.id.action_order_to_processPayment)
        }
        orderManager.currentOrderId.observe(viewLifecycleOwner, Observer { orderId ->
            if (args.orderId != orderId) {
                findNavController().navigate(actionGlobalOrder(orderId))
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (viewModel.configManager.needsConfig() || viewModel.configManager.merchantConfig?.currency == null) {
            findNavController().navigate(R.id.action_global_merchantSettings)
        }
    }

}
