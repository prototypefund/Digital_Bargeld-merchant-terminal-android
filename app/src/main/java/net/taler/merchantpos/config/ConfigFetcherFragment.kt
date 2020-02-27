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
import net.taler.merchantpos.config.ConfigFetcherFragmentDirections.Companion.actionConfigFetcherToOrder

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

        configManager.configUpdateResult.observe(viewLifecycleOwner, Observer { result ->
            when {
                result == null -> return@Observer
                result.error -> onNetworkError(result.authError)
                else -> actionConfigFetcherToOrder().let { findNavController().navigate(it) }
            }
        })
    }

    private fun onNetworkError(authError: Boolean) {
        val res = if (authError) R.string.config_auth_error else R.string.config_error
        Snackbar.make(view!!, res, LENGTH_SHORT).show()
        findNavController().navigate(R.id.action_configFetcher_to_merchantSettings)
    }

}
