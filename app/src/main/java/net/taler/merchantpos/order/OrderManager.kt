package net.taler.merchantpos.order

import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations.map
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.taler.merchantpos.Amount.Companion.fromString
import net.taler.merchantpos.config.ConfigurationReceiver
import net.taler.merchantpos.order.RestartState.DISABLED
import net.taler.merchantpos.order.RestartState.ENABLED
import net.taler.merchantpos.order.RestartState.UNDO
import org.json.JSONObject

enum class RestartState { ENABLED, DISABLED, UNDO }

class OrderManager(private val mapper: ObjectMapper) : ConfigurationReceiver {

    companion object {
        val TAG = OrderManager::class.java.simpleName
    }

    private val productsByCategory = HashMap<Category, ArrayList<Product>>()

    private val mOrder = MutableLiveData<Order>()
    internal val order: LiveData<Order> = mOrder
    internal val orderTotal: LiveData<Double> = map(mOrder) { it.getTotal() }

    private val mProducts = MutableLiveData<List<Product>>()
    internal val products: LiveData<List<Product>> = mProducts

    private val mCategories = MutableLiveData<List<Category>>()
    internal val categories: LiveData<List<Category>> = mCategories

    private var undoOrder: Order? = null
    private val mRestartState = MutableLiveData<RestartState>().apply { value = DISABLED }
    internal val restartState: LiveData<RestartState> = mRestartState

    @Suppress("BlockingMethodInNonBlockingContext") // run on Dispatchers.Main
    override suspend fun onConfigurationReceived(json: JSONObject, currency: String): Boolean {
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

        // parse products (live data gets updated in setCurrentCategory())
        val productsStr = json.getJSONArray("products").toString()
        val productsType = object : TypeReference<List<Product>>() {}
        val products: List<Product> = mapper.readValue(productsStr, productsType)

        // group products by categories
        productsByCategory.clear()
        products.forEach { product ->
            val productCurrency = fromString(product.price).currency
            if (productCurrency != currency) {
                Log.e(TAG, "Product $product has currency $productCurrency, $currency expected")
                return false
            }
            product.categories.forEach { categoryId ->
                val category = categories.find { it.id == categoryId }
                if (category == null) {
                    Log.e(TAG, "Product $product has unknown category $categoryId")
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
            mCategories.postValue(categories)
            mProducts.postValue(productsByCategory[categories[0]])
            true
        } else {
            false
        }
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
        mRestartState.value = ENABLED
    }

    @UiThread
    internal fun restartOrUndo() {
        if (restartState.value == UNDO) {
            mOrder.value = undoOrder
            mRestartState.value = ENABLED
            undoOrder = null
        } else {
            undoOrder = mOrder.value
            mOrder.value = HashMap()
            mRestartState.value = UNDO
        }
    }

}

fun Order.getTotal(): Double {
    var total = 0.0
    forEach {
        val price = it.key.priceAsDouble
        total += price * it.value
    }
    return total
}

fun Order.getTotalAsString(): String {
    return String.format("%.2f", getTotal())
}
