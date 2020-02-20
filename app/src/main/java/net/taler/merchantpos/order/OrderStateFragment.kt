package net.taler.merchantpos.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import kotlinx.android.synthetic.main.fragment_order_state.*
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.order.OrderAdapter.OrderLineLookup
import net.taler.merchantpos.order.OrderAdapter.OrderViewHolder


class OrderStateFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val orderManager by lazy { viewModel.orderManager }
    private val adapter = OrderAdapter()
    private var tracker: SelectionTracker<String>? = null

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
        val detailsLookup = OrderLineLookup(orderList)
        val tracker = SelectionTracker.Builder(
            "order-selection-id",
            orderList,
            adapter.keyProvider,
            detailsLookup,
            StorageStrategy.createStringStorage()
        ).withSelectionPredicate(
            SelectionPredicates.createSelectSingleAnything()
        ).build()
        savedInstanceState?.let { tracker.onRestoreInstanceState(it) }
        adapter.tracker = tracker
        tracker.addObserver(object : SelectionTracker.SelectionObserver<String>() {
            override fun onItemStateChanged(key: String, selected: Boolean) {
                super.onItemStateChanged(key, selected)
                val item = if (selected) adapter.getItemByKey(key) else null
                orderManager.selectOrderLine(item)
            }
        })
        this.tracker = tracker

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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        tracker?.onSaveInstanceState(outState)
    }

}

private class OrderAdapter : Adapter<OrderViewHolder>() {

    lateinit var tracker: SelectionTracker<String>
    val keyProvider = OrderKeyProvider()
    private val orderLines = ArrayList<OrderLine>()

    override fun getItemCount() = orderLines.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val item = orderLines[position]
        holder.bind(item, tracker.isSelected(item.first.id))
    }

    fun setItems(items: HashMap<ConfigProduct, Int>) {
        orderLines.clear()
        items.forEach { t -> orderLines.add(t.toPair()) }
        notifyDataSetChanged()
    }

    fun getItemByKey(key: String): OrderLine? {
        return orderLines.find { it.first.id == key }
    }

    private inner class OrderViewHolder(private val v: View) : ViewHolder(v) {
        private val quantity: TextView = v.findViewById(R.id.quantity)
        private val name: TextView = v.findViewById(R.id.name)
        private val price: TextView = v.findViewById(R.id.price)

        fun bind(orderLine: OrderLine, selected: Boolean) {
            v.isActivated = selected
            quantity.text = orderLine.second.toString()
            name.text = orderLine.first.description
            price.text = String.format("%.2f", orderLine.first.priceAsDouble * orderLine.second)
        }
    }

    private inner class OrderKeyProvider : ItemKeyProvider<String>(SCOPE_MAPPED) {
        override fun getKey(position: Int) = orderLines[position].first.id
        override fun getPosition(key: String): Int {
            return orderLines.indexOfFirst { it.first.id == key }
        }
    }

    internal class OrderLineLookup(private val list: RecyclerView) : ItemDetailsLookup<String>() {
        override fun getItemDetails(e: MotionEvent): ItemDetails<String>? {
            list.findChildViewUnder(e.x, e.y)?.let { view ->
                val holder = list.getChildViewHolder(view)
                val adapter = list.adapter as OrderAdapter
                val position = holder.adapterPosition
                return object : ItemDetails<String>() {
                    override fun getPosition(): Int = position
                    override fun getSelectionKey(): String = adapter.keyProvider.getKey(position)
                    override fun inSelectionHotspot(e: MotionEvent) = true
                }
            }
            return null
        }
    }

}
