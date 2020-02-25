package net.taler.merchantpos

import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.content.Intent.CATEGORY_HOME
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
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

    private lateinit var nav: NavController
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        model.paymentManager.payment.observe(this, Observer { payment ->
            payment?.talerPayUri?.let {
                nfcManager.setTagString(it)
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
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        nav = navHostFragment.navController
        toolbar.setupWithNavController(nav, appBarConfiguration)
    }

    override fun onStart() {
        super.onStart()
        if (model.configManager.needsConfig()) {
            nav.navigate(R.id.action_global_merchantSettings)
        } else if (model.configManager.merchantConfig == null) {
            nav.navigate(R.id.action_global_configFetcher)
        }
    }

    public override fun onResume() {
        super.onResume()
        // TODO should we only read tags when a payment is to be made?
        NfcManager.start(this, nfcManager)
    }

    public override fun onPause() {
        super.onPause()
        NfcManager.stop(this)
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
        } else if (nav.currentDestination?.id == R.id.merchantSettings && model.configManager.needsConfig()) {
            // we are in the configuration screen and need a config to continue
            val intent = Intent(ACTION_MAIN).apply {
                addCategory(CATEGORY_HOME)
                flags = FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } else {
            super.onBackPressed()
        }
    }

}
