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
import net.taler.merchantpos.config.ConfigManager
import net.taler.merchantpos.config.MerchantRequest
import net.taler.merchantpos.order.ContractProduct
import net.taler.merchantpos.order.Order
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

private val TIMEOUT = MINUTES.toMillis(2)
private val CHECK_INTERVAL = SECONDS.toMillis(1)
private const val FULFILLMENT_PREFIX = "taler://fulfillment-success/"

class PaymentManager(
    private val configManager: ConfigManager,
    private val queue: RequestQueue,
    private val mapper: ObjectMapper
) {

    private val mPayment = MutableLiveData<Payment>()
    val payment: LiveData<Payment> = mPayment

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
        val amount = "$currency:${order.totalAsString}"
        val summary = order.summary

        mPayment.value = Payment(order, summary, currency)

        val fulfillmentId = "${System.currentTimeMillis()}-${order.hashCode()}"
        val fulfillmentUrl =
            "${FULFILLMENT_PREFIX}${URLEncoder.encode(summary, "UTF-8")}#$fulfillmentId"
        val body = JSONObject().apply {
            put("order", JSONObject().apply {
                put("amount", amount)
                put("summary", summary)
                // fulfillment_url needs to be unique per order
                put("fulfillment_url", fulfillmentUrl)
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
        val contractProducts = products.map { ContractProduct(it) }
        val productsStr = mapper.writeValueAsString(contractProducts)
        return JSONArray(productsStr)
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
        val currentValue = requireNotNull(mPayment.value)
        if (checkPaymentResponse.getBoolean("paid")) {
            mPayment.value = currentValue.copy(paid = true)
            checkTimer.cancel()
        } else if (currentValue.talerPayUri == null) {
            val talerPayUri = checkPaymentResponse.getString("taler_pay_uri")
            mPayment.value = currentValue.copy(talerPayUri = talerPayUri)
        }
    }

    private fun onNetworkError(volleyError: VolleyError) {
        Log.e(PaymentManager::class.java.simpleName, volleyError.toString())
        cancelPayment()
    }

    fun cancelPayment() {
        mPayment.value = mPayment.value!!.copy(error = true)
        checkTimer.cancel()
    }

}
