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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.Volley
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*



data class HistoryItem(
    val orderId: String,
    val amount: Amount,
    val summary: String,
    val timestamp: Instant
)

class MyAdapter(private var myDataset: List<HistoryItem>) :
    RecyclerView.Adapter<MyAdapter.MyViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.history_row, parent, false)
        return MyViewHolder(view)
    }

    override fun getItemCount(): Int {
        return myDataset.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val item = myDataset[position]
        val summaryTextView = holder.rowView.findViewById<TextView>(R.id.text_history_summary)
        summaryTextView.text = myDataset[position].summary

        val amount = myDataset[position].amount
        val amountTextView = holder.rowView.findViewById<TextView>(R.id.text_history_amount)
        @SuppressLint("SetTextI18n")
        amountTextView.text = "${amount.amount} ${amount.currency}"

        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.UK)
            .withZone(ZoneId.systemDefault())
        val timestampTextView = holder.rowView.findViewById<TextView>(R.id.text_history_time)
        timestampTextView.text = formatter.format(item.timestamp)

        val orderIdTextView =  holder.rowView.findViewById<TextView>(R.id.text_history_order_id)
        orderIdTextView.text = item.orderId
    }

    fun setData(dataset: List<HistoryItem>) {
        this.myDataset = dataset
        this.notifyDataSetChanged()
    }

    class MyViewHolder(val rowView: View) : RecyclerView.ViewHolder(rowView)
}

fun parseTalerTimestamp(s: String): Instant {
    return Instant.ofEpochSecond(s.substringAfterLast('(').substringBeforeLast(')').toLong())
}

/**
 * Fragment to display the merchant's payment history,
 * received from the backend.
 */
class MerchantHistory : Fragment() {
    private lateinit var queue: RequestQueue
    private val model: PosTerminalViewModel by activityViewModels()
    private val historyListAdapter = MyAdapter(listOf())

    private val isLoading = MutableLiveData<Boolean>().apply { value = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        queue = Volley.newRequestQueue(context)
    }

    private fun onNetworkError(volleyError: VolleyError?) {
        this.isLoading.value = false
        val mySnackbar = Snackbar.make(view!!, "Network Error", Snackbar.LENGTH_SHORT)
        mySnackbar.show()
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

    private fun fetchHistory() {
        isLoading.value = true
        val instance = model.merchantConfig!!.instance
        val req = MerchantInternalRequest(
            Request.Method.GET,
            model.merchantConfig!!,
            "history",
            mapOf("instance" to instance),
            null,
            Response.Listener { onHistoryResponse(it) },
            Response.ErrorListener { onNetworkError(it) })
        queue.add(req)
    }

    override fun onResume() {
        super.onResume()
        fetchHistory()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val myLayoutManager = LinearLayoutManager(this@MerchantHistory.context)
        val myItemDecoration = DividerItemDecoration(context, myLayoutManager.orientation)
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_merchant_history, container, false)
        view.findViewById<RecyclerView>(R.id.list_history).apply {
            layoutManager = myLayoutManager
            adapter = historyListAdapter
            addItemDecoration(myItemDecoration)
        }

        val refreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swiperefresh)
        refreshLayout.isRefreshing = false
        refreshLayout.setOnRefreshListener {
            Log.v(TAG, "refreshing!")
            fetchHistory()
        }

        this.isLoading.observe(viewLifecycleOwner, androidx.lifecycle.Observer { loading ->
            Log.v(TAG, "setting refreshing to $loading")
            refreshLayout.isRefreshing = loading
        })

        return view
    }

    companion object {
        const val TAG = "taler-merchant"
    }
}
