package net.taler.merchantpos.config

import android.os.Bundle
import android.text.method.LinkMovementMethod
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
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_merchant_config.*
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.config.MerchantConfigFragmentDirections.Companion.actionSettingsToOrder
import net.taler.merchantpos.navigate
import net.taler.merchantpos.topSnackbar

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
        return inflater.inflate(R.layout.fragment_merchant_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        okButton.setOnClickListener {
            val inputUrl = configUrlView.editText!!.text
            val url = if (inputUrl.startsWith("http")) {
                inputUrl.toString()
            } else {
                "https://$inputUrl".also { configUrlView.editText!!.setText(it) }
            }
            progressBar.visibility = VISIBLE
            okButton.visibility = INVISIBLE
            val config = Config(
                configUrl = url,
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
        }
        configDocsView.movementMethod = LinkMovementMethod.getInstance()
        updateView(savedInstanceState == null)
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

    private fun updateView(isInitialization: Boolean = false) {
        val config = configManager.config
        configUrlView.editText!!.setText(
            if (isInitialization && config.configUrl.isBlank()) CONFIG_URL_DEMO
            else config.configUrl
        )
        usernameView.editText!!.setText(
            if (isInitialization && config.username.isBlank()) CONFIG_USERNAME_DEMO
            else config.username
        )
        passwordView.editText!!.setText(
            if (isInitialization && config.password.isBlank()) CONFIG_PASSWORD_DEMO
            else config.password
        )
        forgetPasswordButton.visibility = if (config.hasPassword()) VISIBLE else GONE
    }

    private fun onConfigReceived(currency: String) {
        onResultReceived()
        updateView()
        topSnackbar(view!!, getString(R.string.config_changed, currency), LENGTH_LONG)
        actionSettingsToOrder().navigate(findNavController())
    }

    private fun onNetworkError(authError: Boolean) {
        onResultReceived()
        val res = if (authError) R.string.config_auth_error else R.string.config_error
        Snackbar.make(view!!, res, LENGTH_LONG).show()
    }

    private fun onResultReceived() {
        progressBar.visibility = INVISIBLE
        okButton.visibility = VISIBLE
    }

}
