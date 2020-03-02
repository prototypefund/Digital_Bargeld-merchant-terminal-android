package net.taler.merchantpos.order

import androidx.core.os.LocaleListCompat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonProperty
import net.taler.merchantpos.Amount
import java.util.*
import java.util.Locale.LanguageRange
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

data class Category(
    val id: Int,
    val name: String,
    @JsonProperty("name_i18n")
    val nameI18n: Map<String, String>?
) {
    var selected: Boolean = false
    val localizedName: String get() = getLocalizedString(nameI18n, name)
}

abstract class Product {
    abstract val id: String
    abstract val description: String
    abstract val descriptionI18n: Map<String, String>?
    abstract val price: String
    abstract val location: String?
    @get:JsonIgnore
    val localizedDescription: String
        get() = getLocalizedString(descriptionI18n, description)
}

data class ConfigProduct(
    @JsonProperty("product_id")
    override val id: String,
    override val description: String,
    @JsonProperty("description_i18n")
    override val descriptionI18n: Map<String, String>?,
    override val price: String,
    @JsonProperty("delivery_location")
    override val location: String?,
    val categories: List<Int>,
    @JsonIgnore
    val quantity: Int = 0
) : Product() {
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
    @JsonInclude(NON_NULL)
    @JsonProperty("description_i18n")
    override val descriptionI18n: Map<String, String>?,
    override val price: String,
    @JsonProperty("delivery_location")
    override val location: String?,
    val quantity: Int
) : Product() {
    constructor(product: ConfigProduct) : this(
        product.id,
        product.description,
        product.descriptionI18n,
        product.price,
        product.location,
        product.quantity
    )
}

private fun getLocalizedString(map: Map<String, String>?, default: String): String {
    // just return the default, if it is the only element
    if (map == null) return default
    // create a priority list of language ranges from system locales
    val locales = LocaleListCompat.getDefault()
    val priorityList = ArrayList<LanguageRange>(locales.size())
    for (i in 0 until locales.size()) {
        priorityList.add(LanguageRange(locales[i].toLanguageTag()))
    }
    // create a list of locales available in the given map
    val availableLocales = map.keys.mapNotNull {
        if (it == "_") return@mapNotNull null
        val list = it.split("_")
        when (list.size) {
            1 -> Locale(list[0])
            2 -> Locale(list[0], list[1])
            3 -> Locale(list[0], list[1], list[2])
            else -> null
        }
    }
    val match = Locale.lookup(priorityList, availableLocales)
    return match?.toString()?.let { map[it] } ?: default
}

data class Order(val id: Int, val availableCategories: Map<Int, Category>) {
    val products = ArrayList<ConfigProduct>()
    val title: String = id.toString()
    val summary: String
        get() = getCategoryQuantities().map { (category: Category, quantity: Int) ->
                "$quantity x ${category.localizedName}"
        }.joinToString()
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

    private fun getCategoryQuantities(): HashMap<Category, Int> {
        val categories = HashMap<Category, Int>()
        products.forEach { product ->
            val categoryId = product.categories[0]
            val category = availableCategories.getValue(categoryId)
            val oldQuantity = categories[category] ?: 0
            categories[category] = oldQuantity + product.quantity
        }
        return categories
    }

    /**
     * Returns a map of i18n summaries for each locale present in *all* given [Category]s
     * or null if there's no locale that fulfills this criteria.
     */
    val summaryI18n: Map<String, String>?
        get() {
            val categoryQuantities = getCategoryQuantities()
            // get all available locales
            val availableLocales = categoryQuantities.mapNotNull { (category, _) ->
                val nameI18n = category.nameI18n
                // if one category doesn't have locales, we can return null here already
                nameI18n?.keys ?: return null
            }.flatten().toHashSet()
            // remove all locales not supported by all categories
            categoryQuantities.forEach { (category, _) ->
                // category.nameI18n should be non-null now
                availableLocales.retainAll(category.nameI18n!!.keys)
                if (availableLocales.isEmpty()) return null
            }
            return availableLocales.map { locale ->
                Pair(
                    locale, categoryQuantities.map { (category, quantity) ->
                        // category.nameI18n should be non-null now
                        "$quantity x ${category.nameI18n!![locale]}"
                    }.joinToString()
                )
            }.toMap()
        }

}
