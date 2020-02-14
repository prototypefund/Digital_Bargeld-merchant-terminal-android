package net.taler.merchantpos.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_order_state.*
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.order.OrderAdapter.OrderViewHolder

class OrderStateFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val orderManager by lazy { viewModel.orderManager }
    private val adapter = OrderAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_order_state, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        orderList.apply {
            adapter = this@OrderStateFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        orderManager.order.observe(viewLifecycleOwner, Observer { order ->
            adapter.setItems(order.products)
        })
        orderManager.orderTotal.observe(viewLifecycleOwner, Observer { orderTotal ->
            if (orderTotal == 0.0) {
                totalView.text = null
            } else {
                totalView.text = getString(R.string.order_total, orderTotal)
            }
        })
    }

}

private class OrderAdapter : RecyclerView.Adapter<OrderViewHolder>() {

    private val orderLines = ArrayList<OrderLine>()

    override fun getItemCount() = orderLines.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orderLines[position])
    }

    fun setItems(items: HashMap<ConfigProduct, Int>) {
        orderLines.clear()
        items.forEach { t -> orderLines.add(t.toPair()) }
        notifyDataSetChanged()
    }

    private inner class OrderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val quantity: TextView = v.findViewById(R.id.quantity)
        private val name: TextView = v.findViewById(R.id.name)
        private val price: TextView = v.findViewById(R.id.price)

        fun bind(orderLine: OrderLine) {
            quantity.text = orderLine.second.toString()
            name.text = orderLine.first.description
            price.text = String.format("%.2f", orderLine.first.priceAsDouble * orderLine.second)
        }
    }

}
