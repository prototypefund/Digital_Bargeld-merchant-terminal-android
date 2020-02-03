package net.taler.merchantpos.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import kotlinx.android.synthetic.main.fragment_merchant_settings.*
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R

/**
 * Fragment that displays merchant settings.
 */
class MerchantConfigFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private val configManager by lazy { model.configManager }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_merchant_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        okButton.setOnClickListener {
            if (!checkInput()) return@setOnClickListener
            configUrlView.error = null
            progressBar.visibility = VISIBLE
            okButton.visibility = INVISIBLE
            val config = Config(
                configUrl = configUrlView.editText!!.text.toString(),
                username = usernameView.editText!!.text.toString(),
                password = passwordView.editText!!.text.toString()
            )
            configManager.fetchConfig(config, true, savePasswordCheckBox.isChecked)
            configManager.configUpdateResult.observe(viewLifecycleOwner, Observer { result ->
                when {
                    result == null -> return@Observer
                    result.error -> onNetworkError(result.authError)
                    else -> onConfigReceived(result.currency!!)
                }
                configManager.configUpdateResult.removeObservers(viewLifecycleOwner)
            })
        }
        forgetPasswordButton.setOnClickListener {
            configManager.forgetPassword()
            passwordView.editText!!.text = null
            forgetPasswordButton.visibility = GONE
            currencyView.visibility = GONE
        }
        updateView()
    }

    override fun onStart() {
        super.onStart()
        // focus password if this is the only empty field
        if (passwordView.editText!!.text.isBlank()
            && !configUrlView.editText!!.text.isBlank()
            && !usernameView.editText!!.text.isBlank()
        ) {
            passwordView.requestFocus()
        }
    }

    private fun updateView() {
        configUrlView.editText!!.setText(configManager.config.configUrl)
        usernameView.editText!!.setText(configManager.config.username)
        passwordView.editText!!.setText(configManager.config.password)

        forgetPasswordButton.visibility = if (configManager.config.hasPassword()) VISIBLE else GONE

        val currency = configManager.merchantConfig?.currency
        if (currency == null) {
            currencyView.visibility = GONE
        } else {
            currencyView.text = getString(R.string.config_currency, currency)
            currencyView.visibility = VISIBLE
        }
    }

    private fun checkInput(): Boolean {
        return if (configUrlView.editText!!.text.startsWith("https://")) {
            true
        } else {
            configUrlView.error = getString(R.string.config_malformed_url)
            false
        }
    }

    private fun onConfigReceived(currency: String) {
        onResultReceived()
        updateView()
        Snackbar.make(view!!, "Changed to new $currency merchant", LENGTH_SHORT).show()
        findNavController().navigate(R.id.order)
    }

    private fun onNetworkError(authError: Boolean) {
        onResultReceived()
        val res = if (authError) R.string.config_auth_error else R.string.config_error
        Snackbar.make(view!!, res, LENGTH_SHORT).show()
    }

    private fun onResultReceived() {
        progressBar.visibility = INVISIBLE
        okButton.visibility = VISIBLE
    }

}
