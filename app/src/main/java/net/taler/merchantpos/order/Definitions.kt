package net.taler.merchantpos.order

import com.fasterxml.jackson.annotation.JsonIgnore
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
    val categories: List<Int>,
    @JsonIgnore
    val quantity: Int = 0
) : Product {
    val priceAsDouble by lazy { Amount.fromString(price).amount.toDouble() }

    override fun equals(other: Any?): Boolean {
        return other is ConfigProduct && id == other.id
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + price.hashCode()
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + categories.hashCode()
        return result
    }
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
    constructor(product: ConfigProduct) : this(
        product.id,
        product.description,
        product.price,
        product.location,
        product.quantity
    )
}

data class Order(val id: Int, val availableCategories: Map<Int, Category>) {
    val products = ArrayList<ConfigProduct>()
    val title: String = id.toString()
    val summary: String
        get() {
            val categories = HashMap<Category, Int>()
            products.forEach { product ->
                val categoryId = product.categories[0]
                val category = availableCategories.getValue(categoryId)
                val oldQuantity = categories[category] ?: 0
                categories[category] = oldQuantity + product.quantity
            }
            return categories.map { (category, quantity) ->
                "$quantity x ${category.name}"
            }.joinToString()
        }
    val total: Double
        get() {
            var total = 0.0
            products.forEach { product ->
                val price = product.priceAsDouble
                total += price * product.quantity
            }
            return total
        }
    val totalAsString: String
        get() = String.format("%.2f", total)

    operator fun plus(product: ConfigProduct): Order {
        val i = products.indexOf(product)
        if (i == -1) {
            products.add(product.copy(quantity = 1))
        } else {
            val quantity = products[i].quantity
            products[i] = products[i].copy(quantity = quantity + 1)
        }
        return this
    }

    operator fun minus(product: ConfigProduct): Order {
        val i = products.indexOf(product)
        if (i == -1) return this
        val quantity = products[i].quantity
        if (quantity <= 1) {
            products.remove(product)
        } else {
            products[i] = products[i].copy(quantity = quantity - 1)
        }
        return this
    }
}
