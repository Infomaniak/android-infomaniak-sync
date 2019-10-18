/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.DavService
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.LoginAccountDetailsBinding
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Credentials
import at.bitfire.davdroid.model.HomeSet
import at.bitfire.davdroid.model.Service
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.login_account_details.*
import java.util.logging.Level
import kotlin.concurrent.thread

class AccountDetailsFragment: Fragment() {

    private lateinit var loginModel: LoginModel
    private lateinit var model: AccountDetailsModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loginModel = ViewModelProviders.of(requireActivity()).get(LoginModel::class.java)
        model = ViewModelProviders.of(this).get(AccountDetailsModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = LoginAccountDetailsBinding.inflate(inflater, container, false)
        v.lifecycleOwner = viewLifecycleOwner
        v.details = model

        val config = loginModel.configuration ?: throw IllegalStateException()

        model.name.value = loginModel.credentials?.accountName ?:
                config.calDAV?.email ?:
                loginModel.credentials?.userName ?:
                loginModel.credentials?.certificateAlias

        // CardDAV-specific
        val settings = Settings.getInstance(requireActivity())
        v.carddav.visibility = if (config.cardDAV != null) View.VISIBLE else View.GONE
        if (settings.has(AccountSettings.KEY_CONTACT_GROUP_METHOD))
            v.contactGroupMethod.isEnabled = false

        v.createAccount.setOnClickListener {
            val name = model.name.value
            if (name.isNullOrBlank())
                model.nameError.value = getString(R.string.login_account_name_required)
            else {
                // check whether account name already exists
                val am = AccountManager.get(requireActivity())
                if (am.getAccountsByType(getString(R.string.account_type)).any { it.name == name }) {
                    model.nameError.value = getString(R.string.login_account_name_already_taken)
                    return@setOnClickListener
                }

                val idx = v.contactGroupMethod.selectedItemPosition
                val groupMethodName = resources.getStringArray(R.array.settings_contact_group_method_values)[idx]

                v.createAccountProgress.visibility = View.VISIBLE
                v.createAccount.visibility = View.GONE

                model.createAccount(
                        name,
                        loginModel.credentials!!,
                        config,
                        GroupMethod.valueOf(groupMethodName)
                ).observe(this, Observer<Boolean> { success ->
                    if (success)
                        requireActivity().finish()
                    else {
                        Snackbar.make(requireActivity().findViewById(android.R.id.content), R.string.login_account_not_created, Snackbar.LENGTH_LONG).show()

                        v.createAccountProgress.visibility = View.GONE
                        v.createAccount.visibility = View.VISIBLE
                    }
                })
            }
        }

        val forcedGroupMethod = settings.getString(AccountSettings.KEY_CONTACT_GROUP_METHOD)?.let { GroupMethod.valueOf(it) }
        if (forcedGroupMethod != null) {
            v.contactGroupMethod.isEnabled = false
            for ((i, method) in resources.getStringArray(R.array.settings_contact_group_method_values).withIndex()) {
                if (method == forcedGroupMethod.name) {
                    v.contactGroupMethod.setSelection(i)
                    break
                }
            }
        } else
            v.contactGroupMethod.isEnabled = true

        return v.root
    }

    override fun onStart() {
        super.onStart()
        create_account.callOnClick()
    }

    class AccountDetailsModel(
            application: Application
    ): AndroidViewModel(application) {

        val name = MutableLiveData<String>()
        val nameError = MutableLiveData<String>()

        fun createAccount(name: String, credentials: Credentials, config: DavResourceFinder.Configuration, groupMethod: GroupMethod): LiveData<Boolean> {
            val result = MutableLiveData<Boolean>()
            val context = getApplication<Application>()
            thread {
                val account = Account(name, context.getString(R.string.account_type))

                // create Android account
                val userData = AccountSettings.initialUserData(credentials)
                Logger.log.log(Level.INFO, "Creating Android account with initial config", arrayOf(account, userData))

                val accountManager = AccountManager.get(context)
                if (!accountManager.addAccountExplicitly(account, credentials.password, userData)) {
                    result.postValue(false)
                    return@thread
                }

                // add entries for account to service DB
                Logger.log.log(Level.INFO, "Writing account configuration to database", config)
                val db = AppDatabase.getInstance(context)
                try {
                    val accountSettings = AccountSettings(context, account)

                    val refreshIntent = Intent(context, DavService::class.java)
                    refreshIntent.action = DavService.ACTION_REFRESH_COLLECTIONS

                    if (config.cardDAV != null) {
                        // insert CardDAV service

                        val id = insertService(db, name, Service.TYPE_CARDDAV, config.cardDAV)

                        // initial CardDAV account settings
                        accountSettings.setGroupMethod(groupMethod)

                        // start CardDAV service detection (refresh collections)
                        refreshIntent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, id)
                        context.startService(refreshIntent)

                        // contact sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_address_books.xml
                        accountSettings.setSyncInterval(context.getString(R.string.address_books_authority), Constants.DEFAULT_SYNC_INTERVAL)
                    } else
                        ContentResolver.setIsSyncable(account, context.getString(R.string.address_books_authority), 0)

                    if (config.calDAV != null) {
                        // insert CalDAV service
                        val id = insertService(db, name, Service.TYPE_CALDAV, config.calDAV)

                        // start CalDAV service detection (refresh collections)
                        refreshIntent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, id)
                        context.startService(refreshIntent)

                        // calendar sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_calendars.xml
                        accountSettings.setSyncInterval(CalendarContract.AUTHORITY, Constants.DEFAULT_SYNC_INTERVAL)

                        // enable task sync if OpenTasks is installed
                        // further changes will be handled by PackageChangedReceiver
                        if (LocalTaskList.tasksProviderAvailable(context)) {
                            ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 1)
                            accountSettings.setSyncInterval(TaskProvider.ProviderName.OpenTasks.authority, Constants.DEFAULT_SYNC_INTERVAL)
                        }
                    } else {
                        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0)
                        ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 0)
                    }

                } catch(e: InvalidAccountException) {
                    Logger.log.log(Level.SEVERE, "Couldn't access account settings", e)
                    result.postValue(false)
                    return@thread
                }
                result.postValue(true)
            }
            return result
        }

        private fun insertService(db: AppDatabase, accountName: String, type: String, info: DavResourceFinder.Configuration.ServiceInfo): Long {
            // insert service
            val service = Service(0, accountName, type, info.principal)
            val serviceId = db.serviceDao().insertOrReplace(service)

            // insert home sets
            val homeSetDao = db.homeSetDao()
            for (homeSet in info.homeSets) {
                homeSetDao.insertOrReplace(HomeSet(0, serviceId, homeSet))
            }

            // insert collections
            val collectionDao = db.collectionDao()
            for (collection in info.collections.values) {
                collection.serviceId = serviceId
                collectionDao.insertOrReplace(collection)
            }

            return serviceId
        }

    }

}