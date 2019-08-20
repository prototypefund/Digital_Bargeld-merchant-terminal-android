package net.taler.merchantpos


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

class MyAdapter(private val myDataset: Array<String>) : RecyclerView.Adapter<MyAdapter.MyViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val textView = LayoutInflater.from(parent.context).inflate(R.layout.history_row, parent, false)
        return MyViewHolder(textView as TextView)
    }

    override fun getItemCount(): Int {
        return myDataset.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.textView.text = myDataset[position]
    }

    class MyViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [MerchantHistory.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [MerchantHistory.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class MerchantHistory : Fragment() {
    private lateinit var queue: RequestQueue
    private lateinit var model: PosTerminalViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = activity?.run {
            ViewModelProviders.of(this)[PosTerminalViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        queue = Volley.newRequestQueue(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val myLayoutManager = LinearLayoutManager(this@MerchantHistory.context)
        val myItemDecoration = DividerItemDecoration(context, myLayoutManager.orientation)
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_merchant_history, container, false)
        val myList = ArrayList<String>()
        for (i in 0..100) {
            myList.add("Element $i")
        }
        val myArray: Array<String> = myList.toTypedArray()
        view.findViewById<RecyclerView>(R.id.list_history).apply {
            layoutManager = myLayoutManager
            adapter = MyAdapter(myArray)
            addItemDecoration(myItemDecoration)
        }
        return view
    }
}
