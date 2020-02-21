package net.taler.merchantpos

import org.json.JSONObject

data class Amount(val currency: String, val amount: String) {
    @Suppress("unused")
    fun isZero(): Boolean {
        return amount.toDouble() == 0.0
    }

    companion object {
        private const val FRACTIONAL_BASE = 1e8

        @Suppress("unused")
        fun fromJson(jsonAmount: JSONObject): Amount {
            val amountCurrency = jsonAmount.getString("currency")
            val amountValue = jsonAmount.getString("value")
            val amountFraction = jsonAmount.getString("fraction")
            val amountIntValue = Integer.parseInt(amountValue)
            val amountIntFraction = Integer.parseInt(amountFraction)
            return Amount(
                amountCurrency,
                (amountIntValue + amountIntFraction / FRACTIONAL_BASE).toString()
            )
        }

        fun fromString(strAmount: String): Amount {
            val components = strAmount.split(":")
            return Amount(components[0], components[1])
        }
    }
}
