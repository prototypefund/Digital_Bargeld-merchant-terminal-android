package net.taler.merchantpos.order

import com.fasterxml.jackson.annotation.JsonProperty
import net.taler.merchantpos.Amount

data class Category(
    val id: Int,
    val name: String
) {
    var selected: Boolean = false
}

interface Product {
    val id: String
    val description: String
    val price: String
    val location: String?
}

data class ConfigProduct(
    @JsonProperty("product_id")
    override val id: String,
    override val description: String,
    override val price: String,
    @JsonProperty("delivery_location")
    override val location: String?,
    val categories: List<Int>
) : Product {
    val priceAsDouble by lazy { Amount.fromString(price).amount.toDouble() }
}

data class ContractProduct(
    @JsonProperty("product_id")
    override val id: String,
    override val description: String,
    override val price: String,
    @JsonProperty("delivery_location")
    override val location: String?,
    val quantity: Int
) : Product {
    constructor(product: ConfigProduct, quantity: Int) : this(
        product.id,
        product.description,
        product.price,
        product.location,
        quantity
    )
}

data class Order(val availableCategories: Map<Int, Category>) {
    val products = HashMap<ConfigProduct, Int>()
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

    operator fun plus(product: ConfigProduct): Order {
        products[product] = (products[product] ?: 0) + 1
        return this
    }

    operator fun minus(product: ConfigProduct): Order {
        var quantity = products[product] ?: throw IllegalStateException()
        quantity -= 1
        if (quantity > 0) products[product] = quantity
        else products.remove(product)
        return this
    }
}

typealias OrderLine = Pair<ConfigProduct, Int>
