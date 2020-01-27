package net.taler.merchantpos.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import kotlinx.android.synthetic.main.fragment_products.*
import net.taler.merchantpos.R
import net.taler.merchantpos.order.ProductAdapter.ProductViewHolder

interface ProductSelectionListener {
    fun onProductSelected(product: Product)
}

class ProductsFragment : Fragment(), ProductSelectionListener {

    private val viewModel: OrderViewModel by activityViewModels()
    private val adapter = ProductAdapter(this)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_products, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        productsList.apply {
            adapter = this@ProductsFragment.adapter
            layoutManager = GridLayoutManager(requireContext(), 3)
        }

        viewModel.products.observe(viewLifecycleOwner, Observer { products ->
            if (products == null) {
                adapter.setItems(emptyList())
            } else {
                adapter.setItems(products)
            }
            progressBar.visibility = INVISIBLE
        })
    }

    override fun onProductSelected(product: Product) {
        viewModel.addProduct(product)
    }

}

private class ProductAdapter(
    private val listener: ProductSelectionListener
) : Adapter<ProductViewHolder>() {

    private val products = ArrayList<Product>()

    override fun getItemCount() = products.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    fun setItems(items: List<Product>) {
        products.clear()
        products.addAll(items)
        notifyDataSetChanged()
    }

    private inner class ProductViewHolder(private val v: View) : ViewHolder(v) {
        private val name: TextView = v.findViewById(R.id.name)
        private val price: TextView = v.findViewById(R.id.price)

        fun bind(product: Product) {
            name.text = product.description
            price.text = product.priceAsDouble.toString()
            v.setOnClickListener { listener.onProductSelected(product) }
        }
    }

}