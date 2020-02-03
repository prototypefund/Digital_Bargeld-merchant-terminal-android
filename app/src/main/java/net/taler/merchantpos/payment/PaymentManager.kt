package net.taler.merchantpos.payment

import android.os.CountDownTimer
import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.volley.Request.Method.GET
import com.android.volley.Request.Method.POST
import com.android.volley.RequestQueue
import com.android.volley.Response.ErrorListener
import com.android.volley.Response.Listener
import com.android.volley.VolleyError
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.taler.merchantpos.config.ConfigManager
import net.taler.merchantpos.config.MerchantRequest
import net.taler.merchantpos.order.Order
import net.taler.merchantpos.order.getTotalAsString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

private val TIMEOUT = MINUTES.toMillis(2)
private val CHECK_INTERVAL = SECONDS.toMillis(1)

class PaymentManager(
    private val configManager: ConfigManager,
    private val queue: RequestQueue,
    private val mapper: ObjectMapper
) {

    private val mPayment = MutableLiveData<Payment>()
    var payment: LiveData<Payment> = mPayment

    private val checkTimer = object : CountDownTimer(TIMEOUT, CHECK_INTERVAL) {
        override fun onTick(millisUntilFinished: Long) {
            val orderId = payment.value?.orderId
            if (orderId == null) cancel()
            else checkPayment(orderId)
        }

        override fun onFinish() {
            payment.value?.copy(error = true)?.let { mPayment.value = it }
        }
    }

    @UiThread
    fun createPayment(order: Order) {
        val merchantConfig = configManager.merchantConfig!!

        val currency = merchantConfig.currency!!
        val orderTotal = order.getTotalAsString()
        val amount = "$currency:$orderTotal"
        val summary = order.map {
            "${it.value} x ${it.key.description}"
        }.joinToString()

        mPayment.value = Payment(order, summary, currency)

        val body = JSONObject().apply {
            put("order", JSONObject().apply {
                put("amount", amount)
                put("summary", summary)
                // fulfillment_url needs to be unique per order
                put("fulfillment_url", "https://example.com/${order.hashCode()}")
                put("instance", "default")
                put("products", order.getProductsJson())
            })
        }

        val req = MerchantRequest(POST, merchantConfig, "order", null, body,
            Listener { onOrderCreated(it) },
            ErrorListener { onNetworkError(it) }
        )
        queue.add(req)
    }

    private fun Order.getProductsJson(): JSONArray {
        val json = JSONArray()
        forEach { product, quantity ->
            val node = mapper.valueToTree<ObjectNode>(product).apply {
                put("quantity", quantity)
            }
            json.put(JSONObject(mapper.writeValueAsString(node)))
        }
        return json
    }

    private fun onOrderCreated(orderResponse: JSONObject) {
        val orderId = orderResponse.getString("order_id")
        mPayment.value = mPayment.value!!.copy(orderId = orderId)
        checkTimer.start()
    }

    private fun checkPayment(orderId: String) {
        val merchantConfig = configManager.merchantConfig!!
        val params = mapOf(
            "order_id" to orderId,
            "instance" to merchantConfig.instance
        )

        val req = MerchantRequest(GET, merchantConfig, "check-payment", params, null,
            Listener { onPaymentChecked(it) },
            ErrorListener { onNetworkError(it) })
        queue.add(req)
    }

    /**
     * Called when the /check-payment response gave a result.
     */
    private fun onPaymentChecked(checkPaymentResponse: JSONObject) {
        if (checkPaymentResponse.getBoolean("paid")) {
            mPayment.value = mPayment.value!!.copy(paid = true)
            checkTimer.cancel()
        } else {
            val talerPayUri = checkPaymentResponse.getString("taler_pay_uri")
            mPayment.value = mPayment.value!!.copy(talerPayUri = talerPayUri)
        }
    }

    private fun onNetworkError(volleyError: VolleyError) {
        Log.e(PaymentManager::class.java.simpleName, volleyError.toString())
        mPayment.value = mPayment.value!!.copy(error = true)
        checkTimer.cancel()
    }

}
