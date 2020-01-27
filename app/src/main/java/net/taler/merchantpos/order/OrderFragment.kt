package net.taler.merchantpos.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.Navigation.findNavController
import kotlinx.android.synthetic.main.fragment_order.*
import net.taler.merchantpos.R

class OrderFragment : Fragment() {

    private val viewModel: OrderViewModel by activityViewModels()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_order, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        restartButton.setOnClickListener { viewModel.restart() }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val nav: NavController = findNavController(requireActivity(), R.id.nav_host_fragment)
        historyButton.setOnClickListener { nav.navigate(R.id.action_global_merchantHistory) }
    }

}
