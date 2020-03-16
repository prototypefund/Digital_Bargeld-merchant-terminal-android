/*
 * This file is part of GNU Taler
 * (C) 2020 Taler Systems S.A.
 *
 * GNU Taler is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3, or (at your option) any later version.
 *
 * GNU Taler is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
 */

package net.taler.merchantpos

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.android.volley.Request.Method.GET
import com.android.volley.RequestQueue
import com.android.volley.Response.ErrorListener
import com.android.volley.Response.Listener
import com.android.volley.toolbox.Volley
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import kotlinx.android.synthetic.main.fragment_merchant_history.*
import net.taler.merchantpos.HistoryItemAdapter.HistoryItemViewHolder
import net.taler.merchantpos.MerchantHistoryDirections.Companion.actionGlobalMerchantSettings
import net.taler.merchantpos.config.MerchantRequest
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle.SHORT
import java.util.*

/**
 * Fragment to display the merchant's payment history,
 * received from the backend.
 */
class MerchantHistory : Fragment() {

    companion object {
        const val TAG = "taler-merchant"
    }

    private lateinit var queue: RequestQueue
    private val model: MainViewModel by activityViewModels()
    private val historyListAdapter = HistoryItemAdapter(listOf())

    private val isLoading = MutableLiveData<Boolean>().apply { value = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        queue = Volley.newRequestQueue(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_merchant_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        list_history.apply {
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(context, VERTICAL))
            adapter = historyListAdapter
        }

        swipeRefresh.isRefreshing = false
        swipeRefresh.setOnRefreshListener {
            Log.v(TAG, "refreshing!")
            fetchHistory()
        }

        this.isLoading.observe(viewLifecycleOwner, androidx.lifecycle.Observer { loading ->
            Log.v(TAG, "setting refreshing to $loading")
            swipeRefresh.isRefreshing = loading
        })
    }

    override fun onStart() {
        super.onStart()
        if (model.configManager.merchantConfig?.instance == null) {
            actionGlobalMerchantSettings().navigate(findNavController())
        } else {
            fetchHistory()
        }
    }

    private fun fetchHistory() {
        isLoading.value = true
        val merchantConfig = model.configManager.merchantConfig!!
        val params = mapOf("instance" to merchantConfig.instance)
        val req = MerchantRequest(GET, merchantConfig, "history", params, null,
            Listener { onHistoryResponse(it) },
            ErrorListener { onNetworkError() })
        queue.add(req)
    }

    private fun onHistoryResponse(body: JSONObject) {
        this.isLoading.value = false
        Log.v(TAG, "got history response $body")
        // TODO use jackson instead of manual parsing
        val data = arrayListOf<HistoryItem>()
        val historyJson = body.getJSONArray("history")
        for (i in 0 until historyJson.length()) {
            val item = historyJson.getJSONObject(i)
            val orderId = item.getString("order_id")
            val summary = item.getString("summary")
            val timestampObj = item.getJSONObject("timestamp")
            val timestamp = Instant.ofEpochSecond(timestampObj.getLong("t_ms"))
            val amount = Amount.fromString(item.getString("amount"))
            data.add(HistoryItem(orderId, amount, summary, timestamp))
        }
        historyListAdapter.setData(data)
    }

    private fun onNetworkError() {
        this.isLoading.value = false
        Snackbar.make(view!!, R.string.error_network, LENGTH_SHORT).show()
    }

}

data class HistoryItem(
    val orderId: String,
    val amount: Amount,
    val summary: String,
    val timestamp: Instant
)

class HistoryItemAdapter(private var items: List<HistoryItem>) : Adapter<HistoryItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryItemViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.history_row, parent, false)
        return HistoryItemViewHolder(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: HistoryItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun setData(items: List<HistoryItem>) {
        this.items = items
        this.notifyDataSetChanged()
    }

    class HistoryItemViewHolder(v: View) : ViewHolder(v) {

        private val summaryTextView: TextView = v.findViewById(R.id.text_history_summary)
        private val amountTextView: TextView = v.findViewById(R.id.text_history_amount)
        private val timestampTextView: TextView = v.findViewById(R.id.text_history_time)
        private val orderIdTextView: TextView = v.findViewById(R.id.text_history_order_id)
        private val formatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())

        fun bind(item: HistoryItem) {
            summaryTextView.text = item.summary
            val amount = item.amount
            @SuppressLint("SetTextI18n")
            amountTextView.text = "${amount.amount} ${amount.currency}"
            timestampTextView.text = formatter.format(item.timestamp)
            orderIdTextView.text = item.orderId
        }
    }

}
