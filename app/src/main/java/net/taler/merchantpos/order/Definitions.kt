package net.taler.merchantpos.order

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.taler.merchantpos.Amount

data class Category(
    val id: Int,
    val name: String
) {
    var selected: Boolean = false
}

@JsonIgnoreProperties("priceAsDouble")
data class Product(
    @JsonProperty("product_id")
    val id: String,
    val description: String,
    val price: String,
    val categories: List<Int>,
    @JsonProperty("delivery_location")
    val location: String
) {
    val priceAsDouble by lazy { Amount.fromString(price).amount.toDouble() }
}

data class Order(val availableCategories: Map<Int, Category>) {
    val products = HashMap<Product, Int>()
    val summary: String
        get() {
            val categories = HashMap<Category, Int>()
            products.forEach { (product, quantity) ->
                val categoryId = product.categories[0]
                val category = availableCategories.getValue(categoryId)
                val oldQuantity = categories[category] ?: 0
                categories[category] = oldQuantity + quantity
            }
            return categories.map { (category, quantity) ->
                "$quantity x ${category.name}"
            }.joinToString()
        }
    val total: Double
        get() {
            var total = 0.0
            products.forEach { (product, quantity) ->
                val price = product.priceAsDouble
                total += price * quantity
            }
            return total
        }
    val totalAsString: String
        get() = String.format("%.2f", total)

    operator fun plus(product: Product): Order {
        products[product] = (products[product] ?: 0) + 1
        return this
    }
}

typealias OrderLine = Pair<Product, Int>
