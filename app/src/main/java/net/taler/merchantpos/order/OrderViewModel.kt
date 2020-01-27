package net.taler.merchantpos.order

import android.app.Application
import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations.map
import androidx.lifecycle.viewModelScope
import com.android.volley.Request.Method.GET
import com.android.volley.Response.ErrorListener
import com.android.volley.Response.Listener
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class OrderViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        val TAG = OrderViewModel::class.java.simpleName
    }

    private val url = "https://grobox.de/taler/products.json"
    private val queue = Volley.newRequestQueue(app)
    private val mapper = ObjectMapper()
        .registerModule(KotlinModule())
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val productsByCategory = HashMap<Category, ArrayList<Product>>()

    private val mOrder = MutableLiveData<HashMap<Product, Int>>()
    internal val order: LiveData<HashMap<Product, Int>> = mOrder
    internal val orderTotal: LiveData<Double> = map(mOrder) { map -> getTotal(map) }

    private val mProducts = MutableLiveData<List<Product>>()
    internal val products: LiveData<List<Product>> = mProducts

    private val mCategories = MutableLiveData<List<Category>>()
    internal val categories: LiveData<List<Category>> = mCategories

    init {
        val stringRequest = JsonObjectRequest(GET, url, null,
            Listener { response -> onConfigurationReceived(response) },
            ErrorListener { onConfigurationError() }
        )
        queue.add(stringRequest)
    }

    override fun onCleared() {
        queue.cancelAll { !it.isCanceled }
    }

    private fun onConfigurationReceived(json: JSONObject) = viewModelScope.launch(Dispatchers.IO) {
        // parse categories
        val categoriesStr = json.getJSONArray("categories").toString()
        val categoriesType = object : TypeReference<List<Category>>() {}
        val categories: List<Category> = mapper.readValue(categoriesStr, categoriesType)
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
                    return@launch
                }
                if (productsByCategory.containsKey(category)) {
                    productsByCategory[category]?.add(product)
                } else {
                    productsByCategory[category] = ArrayList<Product>().apply { add(product) }
                }
            }
        }
        // pre-select the first category
        if (productsByCategory.size > 0) setCurrentCategory(categories[0])
        else onConfigurationError()
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
