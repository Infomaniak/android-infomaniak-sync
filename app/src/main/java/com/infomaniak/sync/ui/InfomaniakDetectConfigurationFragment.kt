/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.infomaniak.sync.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.Credentials
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.setup.AccountDetailsFragment
import at.bitfire.davdroid.ui.setup.DavResourceFinder
import at.bitfire.davdroid.ui.setup.LoginModel
import com.bugsnag.android.Bugsnag
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.infomaniak.sync.GlobalConstants.CLIENT_ID
import com.infomaniak.sync.GlobalConstants.PASSWORD_API_URL
import com.infomaniak.sync.GlobalConstants.PROFILE_API_URL
import com.infomaniak.sync.GlobalConstants.REDIRECT_URI
import com.infomaniak.sync.GlobalConstants.SYNC_INFOMANIAK
import com.infomaniak.sync.GlobalConstants.TOKEN_LOGIN_URL
import com.infomaniak.sync.model.ApiToken
import com.infomaniak.sync.model.InfomaniakPassword
import com.infomaniak.sync.model.InfomaniakUser
import kotlinx.android.synthetic.main.infomaniak_loading_view.*
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.lang.ref.WeakReference
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level
import kotlin.concurrent.thread

class InfomaniakDetectConfigurationFragment : Fragment() {

    private lateinit var loginModel: LoginModel
    private lateinit var model: DetectConfigurationModel
    lateinit var code: String
    lateinit var codeVerifier: String

    companion object {

        fun newInstance(code: String, codeVerifier: String) = InfomaniakDetectConfigurationFragment().apply {
            this.code = code
            this.codeVerifier = codeVerifier
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loginModel = ViewModelProviders.of(requireActivity()).get(LoginModel::class.java)
        model = ViewModelProviders.of(this).get(DetectConfigurationModel::class.java)

        doAsync {

            loginModel.baseURI = URI(SYNC_INFOMANIAK)
            loginModel.credentials = getCredential()

            uiThread {
                if (loginModel.credentials == null) {
                    requireFragmentManager().beginTransaction()
                            .add(NothingDetectedFragment(), null)
                            .commit()
                } else {
                    model.detectConfiguration(loginModel).observe(this@InfomaniakDetectConfigurationFragment, Observer<DavResourceFinder.Configuration> { result ->

                        publishProgress(getString(R.string.login_finalising))

                        // save result for next step
                        loginModel.configuration = result

                        // remove "Detecting configuration" fragment, it shouldn't come back
                        requireFragmentManager().popBackStack()

                        if (result.calDAV != null || result.cardDAV != null)
                            requireFragmentManager().beginTransaction()
                                    .replace(android.R.id.content, AccountDetailsFragment())
                                    .addToBackStack(null)
                                    .commit()
                        else
                            requireFragmentManager().beginTransaction()
                                    .add(NothingDetectedFragment(), null)
                                    .commit()
                    })
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.infomaniak_loading_view, container, false)!!

    private fun publishProgress(text: CharSequence) {
        activity?.runOnUiThread {
            message_status.text = text
        }
    }

    private fun getCredential(): Credentials? {

        var bodyResult: String? = ""
        try {

            val okHttpClient = OkHttpClient.Builder()
                    .build()

            val gson = Gson()

            var formBuilder: MultipartBody.Builder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("grant_type", "authorization_code")
                    .addFormDataPart("client_id", CLIENT_ID)
                    .addFormDataPart("code", code)
                    .addFormDataPart("code_verifier", codeVerifier)
                    .addFormDataPart("redirect_uri", REDIRECT_URI)

            var request = Request.Builder()
                    .url(TOKEN_LOGIN_URL)
                    .post(formBuilder.build())
                    .build()

            var response = okHttpClient.newCall(request).execute()

            var responseBody: ResponseBody? = response.body() ?: return null

            bodyResult = responseBody!!.string()

            val apiToken: ApiToken
            if (bodyResult != null) {
                val jsonResult = JsonParser().parse(bodyResult)
                if (response.isSuccessful) {
                    apiToken = gson.fromJson(jsonResult.asJsonObject, ApiToken::class.java)
                } else {
                    return null
                }
            } else {
                return null
            }

            request = Request.Builder()
                    .url(PROFILE_API_URL)
                    .header("Authorization", "Bearer " + apiToken.access_token)
                    .get()
                    .build()

            publishProgress(getString(R.string.login_retrieving_account_information))

            response = okHttpClient.newCall(request).execute()

            responseBody = response.body()

            if (responseBody == null) {
                return null
            }

            bodyResult = responseBody.string()

            if (response.isSuccessful && bodyResult != null) {
                var jsonResult = JsonParser().parse(bodyResult)
                val infomaniakUser = gson.fromJson(jsonResult.asJsonObject.getAsJsonObject("data"), InfomaniakUser::class.java)

                val accounts: Array<Account> = AccountManager.get(context).getAccountsByType(getString(R.string.account_type))

                var numberSameName = 1
                var tempDisplayName = infomaniakUser.display_name
                var i = 0
                while (i < accounts.size) {
                    val account = accounts[i]
                    if (account.name == tempDisplayName) {
                        tempDisplayName = infomaniakUser.display_name + " $numberSameName"
                        numberSameName++
                        i = 0
                    } else {
                        i++
                    }
                }
                infomaniakUser.display_name = tempDisplayName

                val formater = SimpleDateFormat("EEEE MMM d yyyy HH:mm:ss", Locale.getDefault())

                formBuilder = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("name", "Infomaniak Sync - " + formater.format(Date()))

                request = Request.Builder()
                        .url(PASSWORD_API_URL)
                        .header("Authorization", "Bearer " + apiToken.access_token)
                        .post(formBuilder.build())
                        .build()

                publishProgress(getString(R.string.login_generating_an_application_password))

                response = okHttpClient.newCall(request).execute()

                responseBody = response.body()

                if (responseBody == null) {
                    return null
                }

                bodyResult = responseBody.string()
                if (response.isSuccessful && bodyResult != null) {
                    jsonResult = JsonParser().parse(bodyResult)
                    val infomaniakPassword = gson.fromJson(jsonResult.asJsonObject.getAsJsonObject("data"), InfomaniakPassword::class.java)

                    publishProgress(getText(R.string.login_querying_server))

                    return Credentials(infomaniakUser.login, infomaniakPassword.password, null, infomaniakUser.display_name, infomaniakUser.email)
                }
            }
            return null
        } catch (exception: Exception) {
            exception.printStackTrace()
            Bugsnag.notify(exception) { report ->
                report.error.metaData.addToTab("textError", "error", bodyResult ?: "")
            }
            return null
        }
    }

    class DetectConfigurationModel(
            application: Application
    ) : AndroidViewModel(application) {

        private var detectionThread: WeakReference<Thread>? = null
        private var result = MutableLiveData<DavResourceFinder.Configuration>()

        fun detectConfiguration(loginModel: LoginModel): LiveData<DavResourceFinder.Configuration> {
            synchronized(result) {
                if (detectionThread != null)
                // detection already running
                    return result
            }

            thread {
                synchronized(result) {
                    detectionThread = WeakReference(Thread.currentThread())
                }

                try {
                    DavResourceFinder(getApplication(), loginModel).use { finder ->
                        result.postValue(finder.findInitialConfiguration())
                    }
                } catch (e: Exception) {
                    // exception, shouldn't happen
                    Logger.log.log(Level.SEVERE, "Internal resource detection error", e)
                }
            }
            return result
        }

        override fun onCleared() {
            synchronized(result) {
                detectionThread?.get()?.let { thread ->
                    Logger.log.info("Aborting resource detection")
                    thread.interrupt()
                }
                detectionThread = null
            }
        }
    }


    class NothingDetectedFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val model = ViewModelProviders.of(requireActivity()).get(LoginModel::class.java)
            return AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.login_configuration_detection)
                    .setIcon(R.drawable.ic_error_dark)
                    .setMessage(R.string.login_no_caldav_carddav)
                    .setNeutralButton(R.string.login_view_logs) { _, _ ->
                        val intent = Intent(activity, DebugInfoActivity::class.java)
                        intent.putExtra(DebugInfoActivity.KEY_LOGS, model.configuration?.logs)
                        startActivity(intent)
                    }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        // just dismiss
                    }
                    .create()!!
        }

    }

}
