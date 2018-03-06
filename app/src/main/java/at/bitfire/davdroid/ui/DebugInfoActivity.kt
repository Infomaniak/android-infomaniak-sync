/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.support.v4.app.LoaderManager
import android.support.v4.content.AsyncTaskLoader
import android.support.v4.content.ContextCompat
import android.support.v4.content.FileProvider
import android.support.v4.content.Loader
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import at.bitfire.dav4android.exception.HttpException
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.Settings
import at.bitfire.ical4android.TaskProvider
import kotlinx.android.synthetic.main.activity_debug_info.*
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level

class DebugInfoActivity: AppCompatActivity(), LoaderManager.LoaderCallbacks<String> {

    companion object {
        const val KEY_THROWABLE = "throwable"
        const val KEY_LOGS = "logs"
        const val KEY_ACCOUNT = "account"
        const val KEY_AUTHORITY = "authority"
        const val KEY_PHASE = "phase"
        const val KEY_LOCAL_RESOURCE = "localResource"
        const val KEY_REMOTE_RESOURCE = "remoteResource"
    }

    private var report: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_info)

        supportLoaderManager.initLoader(0, intent.extras, this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_debug_info, menu)
        return true
    }


    fun onShare(item: MenuItem) {
        report?.let {
            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.type = "text/plain"
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} debug info")

            try {
                val debugInfoDir = File(cacheDir, "debug-info")
                debugInfoDir.mkdir()

                val reportFile = File(debugInfoDir, "debug.txt")
                Logger.log.fine("Writing debug info to ${reportFile.absolutePath}")
                val writer = FileWriter(reportFile)
                writer.write(it)
                writer.close()

                sendIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, getString(R.string.authority_log_provider), reportFile))
                sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            } catch(e: IOException) {
                // creating an attachment failed, so send it inline
                sendIntent.putExtra(Intent.EXTRA_TEXT, it)
            }

            startActivity(Intent.createChooser(sendIntent, null))
        }
    }


    override fun onCreateLoader(id: Int, args: Bundle?) =
            ReportLoader(this, args)

    override fun onLoadFinished(loader: Loader<String>, data: String?) {
        data?.let {
            report = it
            text_report.text = it
        }
    }

    override fun onLoaderReset(loader: Loader<String>) {}


    class ReportLoader(
            context: Context,
            val extras: Bundle?
    ): AsyncTaskLoader<String>(context) {

        var result: String? = null

        override fun onStartLoading() {
            if (result != null)
                deliverResult(result)
            else
                forceLoad()
        }

        override fun loadInBackground(): String {
            Logger.log.info("Building debug info")
            val report = StringBuilder("--- BEGIN DEBUG INFO ---\n")

            // begin with most specific information
            extras?.getInt(KEY_PHASE, -1).takeIf { it != -1 }?.let {
                report.append("SYNCHRONIZATION INFO\nSynchronization phase: $it\n")
            }
            extras?.getParcelable<Account>(KEY_ACCOUNT)?.let {
                report.append("Account name: ${it.name}\n")
            }
            extras?.getString(KEY_AUTHORITY)?.let {
                report.append("Authority: $it\n")
            }

            // exception details
            val throwable = extras?.getSerializable(KEY_THROWABLE) as Throwable?
            if (throwable is HttpException) {
                throwable.request?.let {
                    report.append("\nHTTP REQUEST:\n$it\n\n")
                }
                throwable.response?.let {
                    report.append("HTTP RESPONSE:\n$it\n")
                }
            }

            extras?.getString(KEY_LOCAL_RESOURCE)?.let {
                report.append("\nLOCAL RESOURCE:\n$it\n")
            }
            extras?.getString(KEY_REMOTE_RESOURCE)?.let {
                report.append("\nREMOTE RESOURCE:\n$it\n")
            }

            throwable?.let {
                report.append("\nEXCEPTION:\n${Log.getStackTraceString(throwable)}")
            }

            // logs (for instance, from failed resource detection)
            extras?.getString(KEY_LOGS)?.let {
                report.append("\nLOGS:\n$it\n")
            }

            // software information
            try {
                val pm = context.packageManager
                val installedFrom = pm.getInstallerPackageName(BuildConfig.APPLICATION_ID) ?: "APK (directly)"
                var workaroundInstalled = false
                try {
                    workaroundInstalled = pm.getPackageInfo("${BuildConfig.APPLICATION_ID}.jbworkaround", 0) != null
                } catch(e: PackageManager.NameNotFoundException) {
                }
                val formatter = SimpleDateFormat.getDateInstance()
                report.append("\nSOFTWARE INFORMATION\n" +
                              "Package: ${BuildConfig.APPLICATION_ID}\n" +
                              "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) from ${formatter.format(Date(BuildConfig.buildTime))}\n")
                      .append("Installed from: $installedFrom\n")
                      .append("JB Workaround installed: ${if (workaroundInstalled) "yes" else "no"}\n\n")
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't get software information", e)
            }

            // connectivity
            report.append("CONNECTIVITY (at the moment)\n")
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.activeNetworkInfo?.let { networkInfo ->
                val type = when (networkInfo.type) {
                    ConnectivityManager.TYPE_WIFI   -> "WiFi"
                    ConnectivityManager.TYPE_MOBILE -> "mobile"
                    else -> "type: ${networkInfo.type}"
                }
                report.append("Active connection: $type, ${networkInfo.detailedState}\n")
            }
            if (Build.VERSION.SDK_INT >= 23)
                connectivityManager.defaultProxy?.let { proxy ->
                    report.append("System default proxy: ${proxy.host}:${proxy.port}")
                }
            report.append("\n")

            report.append("CONFIGURATION\n")
            // power saving
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= 23)
                report.append("Power saving disabled: ")
                      .append(if (powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) "yes" else "no")
                      .append("\n")
            // permissions
            for (permission in arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
                                       Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
                                       TaskProvider.PERMISSION_READ_TASKS, TaskProvider.PERMISSION_WRITE_TASKS))
                report.append(permission).append(" permission: ")
                      .append(if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) "granted" else "denied")
                      .append("\n")
            // system-wide sync settings
            report.append("System-wide synchronization: ")
                  .append(if (ContentResolver.getMasterSyncAutomatically()) "automatically" else "manually")
                  .append("\n")
            // main accounts
            val accountManager = AccountManager.get(context)
            Settings.getInstance(context)?.let { settings ->
                for (acct in accountManager.getAccountsByType(context.getString(R.string.account_type))) {
                    try {
                        val accountSettings = AccountSettings(context, settings, acct)
                        report.append("Account: ${acct.name}\n" +
                                "  Address book sync. interval: ${syncStatus(accountSettings, context.getString(R.string.address_books_authority))}\n" +
                                "  Calendar     sync. interval: ${syncStatus(accountSettings, CalendarContract.AUTHORITY)}\n" +
                                "  OpenTasks    sync. interval: ${syncStatus(accountSettings, TaskProvider.ProviderName.OpenTasks.authority)}\n" +
                                "  WiFi only: ").append(accountSettings.getSyncWifiOnly())
                        accountSettings.getSyncWifiOnlySSIDs()?.let {
                            report.append(", SSIDs: ${accountSettings.getSyncWifiOnlySSIDs()}")
                        }
                        report.append("\n  [CardDAV] Contact group method: ${accountSettings.getGroupMethod()}")
                                .append("\n  [CalDAV] Time range (past days): ${accountSettings.getTimeRangePastDays()}")
                                .append("\n           Manage calendar colors: ${accountSettings.getManageCalendarColors()}")
                                .append("\n")
                    } catch (e: InvalidAccountException) {
                        report.append("$acct is invalid (unsupported settings version) or does not exist\n")
                    }
                }
            }
            // address book accounts
            for (acct in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)))
                try {
                    val addressBook = LocalAddressBook(context, acct, null)
                    report.append("Address book account: ${acct.name}\n" +
                            "  Main account: ${addressBook.mainAccount}\n" +
                            "  URL: ${addressBook.url}\n" +
                            "  Sync automatically: ").append(ContentResolver.getSyncAutomatically(acct, ContactsContract.AUTHORITY)).append("\n")
                } catch(e: Exception) {
                    report.append("$acct is invalid: ${e.message}\n")
                }
            report.append("\n")

            ServiceDB.OpenHelper(context).use { dbHelper ->
                report.append("SQLITE DUMP\n")
                dbHelper.dump(report)
                report.append("\n")
            }

            try {
                report.append(
                        "SYSTEM INFORMATION\n" +
                        "Android version: ${Build.VERSION.RELEASE} (${Build.DISPLAY})\n" +
                        "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n\n"
                )
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't get system details", e)
            }

            report.append("--- END DEBUG INFO ---\n")

            report.toString().let {
                result = it
                return it
            }
        }

        private fun syncStatus(settings: AccountSettings, authority: String): String {
            val interval = settings.getSyncInterval(authority)
            return if (interval != null) {
                if (interval == AccountSettings.SYNC_INTERVAL_MANUALLY) "manually" else "${interval/60} min"
            } else
                "—"
        }
    }

}
