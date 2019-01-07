/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.ui.setup.LoginActivity
import com.bugsnag.android.Bugsnag
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.accounts_content.*
import kotlinx.android.synthetic.main.activity_accounts.*
import kotlinx.android.synthetic.main.activity_accounts.view.*


class AccountsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, SyncStatusObserver {

    companion object {
        val accountsDrawerHandler = DefaultAccountsDrawerHandler()

        private const val fragTagStartup = "startup"
        private const val CUSTOM_TAB_PACKAGE_NAME = "com.android.chrome"
    }

    private lateinit var settings: Settings

    private var syncStatusSnackbar: Snackbar? = null
    private var syncStatusObserver: Any? = null

    private var mClient: CustomTabsClient? = null
    private var mCustomTabsSession: CustomTabsSession? = null
    private var mCustomTabsServiceConnection: CustomTabsServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)
        Bugsnag.init(this)
        Bugsnag.setNotifyReleaseStages("production")
        settings = Settings.getInstance(this)

        setSupportActionBar(toolbar)

        val data = intent.data
        if (data != null && LoginActivity.REDIRECT_URI_ROOT == data.scheme) {
            intent.data = null
            val code = data.getQueryParameter("code")
            val error = data.getQueryParameter("error")
            if (!TextUtils.isEmpty(code)) {
                val intent = Intent(this, LoginActivity::class.java)
                intent.putExtra("code", code)
                startActivity(intent)
            }
        }

        mCustomTabsServiceConnection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(componentName: ComponentName, customTabsClient: CustomTabsClient) {
                mClient = customTabsClient
                mClient!!.warmup(0L)
                mCustomTabsSession = mClient!!.newSession(null)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                mClient = null
            }
        }

        CustomTabsClient.bindCustomTabsService(this@AccountsActivity, CUSTOM_TAB_PACKAGE_NAME, mCustomTabsServiceConnection)
        val customTabsIntent = CustomTabsIntent.Builder(mCustomTabsSession)
                .setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .setShowTitle(true)
                .build()

        if (supportFragmentManager.findFragmentByTag(fragTagStartup) == null) {
            val ft = supportFragmentManager.beginTransaction()
            StartupDialogFragment.getStartupDialogs(this).forEach { ft.add(it, fragTagStartup) }
            ft.commit()
        }

        fab.setOnClickListener {
            try {
                customTabsIntent.launchUrl(this@AccountsActivity, Uri.parse("${LoginActivity.LOGIN_URL_AUTHORIZE}?client_id=${LoginActivity.CLIENT_ID}&response_type=code&redirect_uri=${LoginActivity.REDIRECT_URI_ROOT}:/oauth2redirect"))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this@AccountsActivity, getString(R.string.an_error_has_occurred), Toast.LENGTH_LONG).show()
            }
        }
        fab.show()

        accountsDrawerHandler.initMenu(this, drawer_layout.nav_view.menu)
        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)
        nav_view.itemIconTintList = null
    }

    override fun onResume() {
        super.onResume()

        onStatusChanged(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS)
        syncStatusObserver = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this)
    }

    override fun onPause() {
        super.onPause()

        syncStatusObserver?.let {
            ContentResolver.removeStatusChangeListener(it)
            syncStatusObserver = null
        }
    }

    public override fun onDestroy() {
        super.onDestroy()

        closeCustomTabs()
    }

    private fun closeCustomTabs() {
        if (mCustomTabsServiceConnection != null) {
            unbindService(mCustomTabsServiceConnection)
        }
    }

    override fun onStatusChanged(which: Int) {
        syncStatusSnackbar?.let {
            it.dismiss()
            syncStatusSnackbar = null
        }

        if (!ContentResolver.getMasterSyncAutomatically()) {
            val snackbar = Snackbar
                    .make(findViewById(R.id.coordinator), R.string.accounts_global_sync_disabled, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.accounts_global_sync_enable) {
                        ContentResolver.setMasterSyncAutomatically(true)
                    }
            syncStatusSnackbar = snackbar
            snackbar.show()
        }
    }


    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START))
            drawer_layout.closeDrawer(GravityCompat.START)
        else
            super.onBackPressed()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val processed = accountsDrawerHandler.onNavigationItemSelected(this, item)
        drawer_layout.closeDrawer(GravityCompat.START)
        return processed
    }

}
