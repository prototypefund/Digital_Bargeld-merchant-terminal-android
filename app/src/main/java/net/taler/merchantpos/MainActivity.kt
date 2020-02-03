package net.taler.merchantpos

import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat.START
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener

class MainActivity : AppCompatActivity(), OnNavigationItemSelectedListener {

    private val model: MainViewModel by viewModels()
    private val nfcManager = NfcManager()
    private var nfcAdapter: NfcAdapter? = null

    private lateinit var nav: NavController
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        model.paymentManager.payment.observe(this, Observer { payment ->
            payment?.talerPayUri?.let {
                nfcManager.setContractUri(it)
            }
        })

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.order,
                R.id.merchantSettings,
                R.id.merchantHistory
            ), drawerLayout
        )

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        nav = navHostFragment.navController
        toolbar.setupWithNavController(nav, appBarConfiguration)
    }

    override fun onStart() {
        super.onStart()
        if (!model.configManager.config.isValid()) {
            nav.navigate(R.id.action_global_merchantSettings)
        } else if (model.configManager.merchantConfig == null) {
            nav.navigate(R.id.action_global_configFetcher)
        }
    }

    public override fun onResume() {
        super.onResume()
        // TODO should we only read tags when a payment is to be made?
        nfcAdapter?.enableReaderMode(this, nfcManager, nfcManager.flags, null)
    }

    public override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_order -> nav.navigate(R.id.action_global_order)
            R.id.nav_history -> nav.navigate(R.id.action_global_merchantHistory)
            R.id.nav_settings -> nav.navigate(R.id.action_global_merchantSettings)
        }
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.closeDrawer(START)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(START)) {
            drawerLayout.closeDrawer(START)
        } else {
            super.onBackPressed()
        }
    }

}
