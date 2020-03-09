package net.taler.merchantpos.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.config.ConfigFetcherFragmentDirections.Companion.actionConfigFetcherToMerchantSettings
import net.taler.merchantpos.config.ConfigFetcherFragmentDirections.Companion.actionConfigFetcherToOrder
import net.taler.merchantpos.navigate

class ConfigFetcherFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private val configManager by lazy { model.configManager }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_config_fetcher, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        configManager.fetchConfig(configManager.config, false)
        configManager.configUpdateResult.observe(viewLifecycleOwner, Observer { result ->
            when (result) {
                null -> return@Observer
                is ConfigUpdateResult.Error -> onNetworkError(result.msg)
                is ConfigUpdateResult.Success -> {
                    actionConfigFetcherToOrder().navigate(findNavController())
                }
            }
        })
    }

    private fun onNetworkError(msg: String) {
        Snackbar.make(view!!, msg, LENGTH_SHORT).show()
        actionConfigFetcherToMerchantSettings().navigate(findNavController())
    }

}
