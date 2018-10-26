/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.AccountManager
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.support.customtabs.CustomTabsClient
import android.support.customtabs.CustomTabsIntent
import android.support.customtabs.CustomTabsServiceConnection
import android.support.customtabs.CustomTabsSession
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.v4.app.LoaderManager
import android.support.v4.content.ContextCompat
import android.support.v4.content.Loader
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.view.MenuItem
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.ui.setup.LoginActivity
import kotlinx.android.synthetic.main.accounts_content.*
import kotlinx.android.synthetic.main.activity_accounts.*


class AccountsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, LoaderManager.LoaderCallbacks<AccountsActivity.Settings>, SyncStatusObserver {

    companion object {
        val accountsDrawerHandler = DefaultAccountsDrawerHandler()

        private const val fragTagStartup = "startup"
        private const val CUSTOM_TAB_PACKAGE_NAME = "com.android.chrome"
    }

    private var syncStatusSnackbar: Snackbar? = null
    private var syncStatusObserver: Any? = null

    private var mClient: CustomTabsClient? = null
    private var mCustomTabsSession: CustomTabsSession? = null
    private var mCustomTabsServiceConnection: CustomTabsServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)

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

        fab.setOnClickListener {
            customTabsIntent.launchUrl(this@AccountsActivity, Uri.parse("${LoginActivity.LOGIN_URL_AUTHORIZE}?client_id=${LoginActivity.CLIENT_ID}&response_type=code&redirect_uri=${LoginActivity.REDIRECT_URI_ROOT}:/oauth2redirect"))
        }

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.setDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)
        nav_view.itemIconTintList = null

        /* When the DAVdroid main activity is started, start a Settings service that stays in memory
        for better performance. The service stops itself when memory is trimmed. */
        val settingsIntent = Intent(this, Settings::class.java)
        startService(settingsIntent)

        val args = Bundle(1)
        supportLoaderManager.initLoader(0, args, this)
    }

    override fun onCreateLoader(code: Int, args: Bundle?) =
            SettingsLoader(this)

    override fun onLoadFinished(loader: Loader<Settings>, result: Settings?) {
        val result = result ?: return

        if (supportFragmentManager.findFragmentByTag(fragTagStartup) == null) {
            val ft = supportFragmentManager.beginTransaction()
            StartupDialogFragment.getStartupDialogs(this, result.settings).forEach { ft.add(it, fragTagStartup) }
            ft.commit()
        }

        nav_view?.menu?.let {
            accountsDrawerHandler.onSettingsChanged(result.settings, it)
        }
    }

    override fun onLoaderReset(loader: Loader<Settings>) {
        nav_view?.menu?.let {
            accountsDrawerHandler.onSettingsChanged(null, it)
        }
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

    fun closeCustomTabs() {
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


    class Settings(
            val settings: ISettings
    )

    class SettingsLoader(
            context: Context
    ) : at.bitfire.davdroid.ui.SettingsLoader<Settings>(context) {

        override fun loadInBackground(): Settings? {
            settings?.let {
                val accountManager = AccountManager.get(context)
                val accounts = accountManager.getAccountsByType(context.getString(R.string.account_type))

                return Settings(
                        it
                )
            }
            return null
        }

    }

}
