package net.taler.merchantpos.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import kotlinx.android.synthetic.main.fragment_categories.*
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.order.CategoryAdapter.CategoryViewHolder

interface CategorySelectionListener {
    fun onCategorySelected(category: Category)
}

class CategoriesFragment : Fragment(), CategorySelectionListener {

    private val viewModel: MainViewModel by activityViewModels()
    private val orderManager by lazy { viewModel.orderManager }
    private val adapter = CategoryAdapter(this)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_categories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        categoriesList.apply {
            adapter = this@CategoriesFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        orderManager.categories.observe(viewLifecycleOwner, Observer { categories ->
            adapter.setItems(categories)
            progressBar.visibility = INVISIBLE
        })
    }

    override fun onCategorySelected(category: Category) {
        orderManager.setCurrentCategory(category)
    }

}

private class CategoryAdapter(
    private val listener: CategorySelectionListener
) : Adapter<CategoryViewHolder>() {

    private val categories = ArrayList<Category>()

    override fun getItemCount() = categories.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    fun setItems(items: List<Category>) {
        categories.clear()
        categories.addAll(items)
        notifyDataSetChanged()
    }

    private inner class CategoryViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val button: Button = v.findViewById(R.id.button)

        fun bind(category: Category) {
            button.text = category.localizedName
            button.isPressed = category.selected
            button.setOnClickListener { listener.onCategorySelected(category) }
        }
    }

}
