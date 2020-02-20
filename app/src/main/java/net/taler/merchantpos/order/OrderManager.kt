package net.taler.merchantpos.order

import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations.map
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.taler.merchantpos.Amount.Companion.fromString
import net.taler.merchantpos.CombinedLiveData
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

    private val productsByCategory = HashMap<Category, ArrayList<ConfigProduct>>()

    private val mOrder = MutableLiveData<Order>()
    private val newOrder  // an empty order containing only available categories
        get() = Order(productsByCategory.keys.map { it.id to it }.toMap())
    internal val order: LiveData<Order> = mOrder
    internal val orderTotal: LiveData<Double> = map(mOrder) { it.total }

    private val mProducts = MutableLiveData<List<ConfigProduct>>()
    internal val products: LiveData<List<ConfigProduct>> = mProducts

    private val mCategories = MutableLiveData<List<Category>>()
    internal val categories: LiveData<List<Category>> = mCategories

    private var undoOrder: Order? = null
    private val mRestartState = MutableLiveData<RestartState>().apply { value = DISABLED }
    internal val restartState: LiveData<RestartState> = mRestartState

    private val mSelectedOrderLine = MutableLiveData<ConfigProduct>()

    internal val modifyOrderAllowed =
        CombinedLiveData(restartState, mSelectedOrderLine) { restartState, selectedOrderLine ->
            restartState != DISABLED && selectedOrderLine != null
        }

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
        val productsType = object : TypeReference<List<ConfigProduct>>() {}
        val products: List<ConfigProduct> = mapper.readValue(productsStr, productsType)

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
                    productsByCategory[category] = ArrayList<ConfigProduct>().apply { add(product) }
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
    internal fun addProduct(product: ConfigProduct) {
        val order = mOrder.value ?: newOrder
        mOrder.value = order + product
        mRestartState.value = ENABLED
    }

    @UiThread
    internal fun removeProduct(product: ConfigProduct) {
        val order = mOrder.value ?: throw IllegalStateException()
        val modifiedOrder = order - product
        mOrder.value = modifiedOrder
        mRestartState.value = if (modifiedOrder.products.isEmpty()) DISABLED else ENABLED
    }

    @UiThread
    internal fun restartOrUndo() {
        if (restartState.value == UNDO) {
            mOrder.value = undoOrder
            mRestartState.value = ENABLED
            undoOrder = null
        } else {
            undoOrder = mOrder.value
            mOrder.value = newOrder
            mRestartState.value = UNDO
        }
    }

    @UiThread
    fun selectOrderLine(product: ConfigProduct?) {
        mSelectedOrderLine.value = product
    }

    @UiThread
    fun increaseSelectedOrderLine() {
        val orderLine = mSelectedOrderLine.value ?: throw IllegalStateException()
        addProduct(orderLine)
    }

    @UiThread
    fun decreaseSelectedOrderLine() {
        val orderLine = mSelectedOrderLine.value ?: throw IllegalStateException()
        removeProduct(orderLine)
    }

}
