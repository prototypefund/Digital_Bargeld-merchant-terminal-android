package net.taler.merchantpos.order

import com.fasterxml.jackson.annotation.JsonProperty
import net.taler.merchantpos.Amount

data class Category(
    val id: Int,
    val name: String
) {
    var selected: Boolean = false
}

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

typealias Order = HashMap<Product, Int>

typealias OrderLine = Pair<Product, Int>
