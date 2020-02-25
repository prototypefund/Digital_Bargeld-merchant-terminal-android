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
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import kotlinx.android.synthetic.main.fragment_order_state.*
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.fadeIn
import net.taler.merchantpos.fadeOut
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
            adapter.setItems(order.products) {
                // workaround for bug: SelectionObserver doesn't update when removing selected item
                if (tracker.hasSelection()) {
                    val key = tracker.selection.first()
                    val product = order.products.find { it.id == key }
                    if (product == null) tracker.clearSelection()
                }
            }
        })
        orderManager.orderTotal.observe(viewLifecycleOwner, Observer { orderTotal ->
            if (orderTotal == 0.0) {
                totalView.fadeOut()
                totalView.text = null
            } else {
                val currency = viewModel.configManager.merchantConfig?.currency
                totalView.text = getString(R.string.order_total, orderTotal, currency)
                totalView.fadeIn()
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
    private val itemCallback = object : DiffUtil.ItemCallback<ConfigProduct>() {
        override fun areItemsTheSame(oldItem: ConfigProduct, newItem: ConfigProduct): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: ConfigProduct, newItem: ConfigProduct): Boolean {
            return oldItem.quantity == newItem.quantity
        }
    }
    private val differ = AsyncListDiffer<ConfigProduct>(this, itemCallback)

    override fun getItemCount() = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val item = getItem(position)!!
        holder.bind(item, tracker.isSelected(item.id))
    }

    fun setItems(items: List<ConfigProduct>, commitCallback: () -> Unit) {
        // toMutableList() is needed for some reason, otherwise doesn't update adapter
        differ.submitList(items.toMutableList(), commitCallback)
    }

    fun getItem(position: Int): ConfigProduct? = differ.currentList[position]

    fun getItemByKey(key: String): ConfigProduct? {
        return differ.currentList.find { it.id == key }
    }

    private inner class OrderViewHolder(private val v: View) : ViewHolder(v) {
        private val quantity: TextView = v.findViewById(R.id.quantity)
        private val name: TextView = v.findViewById(R.id.name)
        private val price: TextView = v.findViewById(R.id.price)

        fun bind(product: ConfigProduct, selected: Boolean) {
            v.isActivated = selected
            quantity.text = product.quantity.toString()
            name.text = product.description
            price.text = String.format("%.2f", product.priceAsDouble * product.quantity)
        }
    }

    private inner class OrderKeyProvider : ItemKeyProvider<String>(SCOPE_MAPPED) {
        override fun getKey(position: Int) = getItem(position)!!.id
        override fun getPosition(key: String): Int {
            return differ.currentList.indexOfFirst { it.id == key }
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
