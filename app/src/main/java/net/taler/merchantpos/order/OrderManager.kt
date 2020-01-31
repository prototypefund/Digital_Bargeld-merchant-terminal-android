package net.taler.merchantpos.order

import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations.map
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.taler.merchantpos.config.ConfigurationReceiver
import org.json.JSONObject

class OrderManager(private val mapper: ObjectMapper) : ConfigurationReceiver {

    companion object {
        val TAG = OrderManager::class.java.simpleName
    }

    private val productsByCategory = HashMap<Category, ArrayList<Product>>()

    private val mOrder = MutableLiveData<HashMap<Product, Int>>()
    internal val order: LiveData<HashMap<Product, Int>> = mOrder
    internal val orderTotal: LiveData<Double> = map(mOrder) { map -> getTotal(map) }

    private val mProducts = MutableLiveData<List<Product>>()
    internal val products: LiveData<List<Product>> = mProducts

    private val mCategories = MutableLiveData<List<Category>>()
    internal val categories: LiveData<List<Category>> = mCategories

    override suspend fun onConfigurationReceived(json: JSONObject): Boolean {
        // parse categories
        val categoriesStr = json.getJSONArray("categories").toString()
        val categoriesType = object : TypeReference<List<Category>>() {}
        val categories: List<Category> = mapper.readValue(categoriesStr, categoriesType)
        if (categories.isEmpty()) {
            Log.e(TAG, "No valid category found.")
            return false
        }
        // pre-select the first category
        categories[0].selected = true
        mCategories.postValue(categories)

        // parse products (live data gets updated in setCurrentCategory())
        val productsStr = json.getJSONArray("products").toString()
        val productsType = object : TypeReference<List<Product>>() {}
        val products: List<Product> = mapper.readValue(productsStr, productsType)

        // group products by categories
        productsByCategory.clear()
        products.forEach { product ->
            product.categories.forEach { categoryId ->
                val category = categories.find { it.id == categoryId }
                if (category == null) {
                    Log.e(TAG, "Product $product has unknown category $categoryId")
                    onConfigurationError()
                    return false
                }
                if (productsByCategory.containsKey(category)) {
                    productsByCategory[category]?.add(product)
                } else {
                    productsByCategory[category] = ArrayList<Product>().apply { add(product) }
                }
            }
        }
        return if (productsByCategory.size > 0) {
            mProducts.postValue(productsByCategory[categories[0]])
            true
        } else {
            false
        }
    }

    private fun onConfigurationError() {
        Log.e("TEST", "ERROR")
        // TODO
    }

    internal fun setCurrentCategory(category: Category) {
        val newCategories = categories.value?.apply {
            forEach { if (it.selected) it.selected = false }
            category.selected = true
        }
        mCategories.postValue(newCategories)
        mProducts.postValue(productsByCategory[category])
    }

    @UiThread
    internal fun addProduct(product: Product) {
        val map = mOrder.value ?: HashMap()
        val quantity = map[product] ?: 0
        map[product] = quantity + 1
        mOrder.value = map
    }

    @UiThread
    internal fun restart() {
        mOrder.value = HashMap()
    }

    private fun getTotal(map: HashMap<Product, Int>): Double {
        var total = 0.0
        map.forEach {
            val price = it.key.priceAsDouble
            total += price * it.value
        }
        return total
    }

}
