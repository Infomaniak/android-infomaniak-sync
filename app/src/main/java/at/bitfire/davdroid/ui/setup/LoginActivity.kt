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
import android.os.AsyncTask
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.UiUtils
import com.bugsnag.android.Bugsnag
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.infomaniak.sync.ApiToken
import com.infomaniak.sync.InfomaniakPassword
import com.infomaniak.sync.InfomaniakUser
import kotlinx.android.synthetic.main.infomaniak_loading_view.*
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.lang.ref.WeakReference
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock


/**
 * Activity to initially connect to a server and create an account.
 * Fields for server/user data can be pre-filled with extras in the Intent.
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        /**
         * When set, "login by URL" will be activated by default, and the URL field will be set to this value.
         * When not set, "login by email" will be activated by default.
         */
        const val EXTRA_URL = "url"

        /**
         * When set, and {@link #EXTRA_PASSWORD} is set too, the user name field will be set to this value.
         * When set, and {@link #EXTRA_URL} is not set, the email address field will be set to this value.
         */
        const val EXTRA_USERNAME = "username"

        /**
         * When set, the password field will be set to this value.
         */
        const val EXTRA_PASSWORD = "password"

        const val APP_UID = "com.infomaniak.sync"
        const val CLIENT_ID = "CE011334-F75A-4263-9F9F-45FC5A142F59"

        const val REDIRECT_URI = "$APP_UID:/oauth2redirect"

        private const val LOGIN_ENDPOINT = "https://login.infomaniak.com"
        const val AUTHORIZE_LOGIN_URL = "$LOGIN_ENDPOINT/authorize"
        const val TOKEN_LOGIN_URL = "$LOGIN_ENDPOINT/token"

        private const val API_ENDPOINT = "https://api.infomaniak.com/1"
        const val PROFILE_API_URL = "$API_ENDPOINT/profile"
        const val PASSWORD_API_URL = "$API_ENDPOINT/profile/password"

        private const val SYNC_INFOMANIAK = "https://sync.infomaniak.com"

        private var connection: GenerateInfomaniakAccountTask? = null
        private var mutex = ReentrantLock(true)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.infomaniak_loading_view)

        if (intent != null) {
            val code = intent.getStringExtra("code")
            val codeVerifier = intent.getStringExtra("verifier")
            if (TextUtils.isEmpty(code) || TextUtils.isEmpty(codeVerifier)) {
                Toast.makeText(this, getString(R.string.an_error_has_occurred), Toast.LENGTH_LONG).show()
                backPressed()
            } else {
                connection = GenerateInfomaniakAccountTask(this, code, codeVerifier)
                connection!!.execute()
            }
        } else {
            Toast.makeText(this, getString(R.string.an_error_has_occurred), Toast.LENGTH_LONG).show()
            backPressed()
        }
    }

    override fun onBackPressed() {
    }

    private fun backPressed() {
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        try {
            mutex.unlock()
        } catch (e: IllegalMonitorStateException) {
            mutex = ReentrantLock(true)
        }
    }

    override fun onPause() {
        super.onPause()
        mutex.tryLock()
    }

    public override fun onDestroy() {
        super.onDestroy()

        if (connection != null) {
            connection!!.cancel(true)
        }
    }

    fun showHelp(item: MenuItem) {
        UiUtils.launchUri(this, App.homepageUrl(this).buildUpon()
                .appendPath("tested-with")
                .build())
    }

    class GenerateInfomaniakAccountTask internal constructor(context: LoginActivity,
                                                             private val code: String,
                                                             private val codeVerifier: String) :
            AsyncTask<String, CharSequence, DavResourceFinder.Configuration>() {

        private val activityReference: WeakReference<LoginActivity> = WeakReference(context)

        override fun doInBackground(vararg params: String): DavResourceFinder.Configuration? {
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

                var loginActivity: LoginActivity = activityReference.get() ?: return null

                publishProgress(loginActivity.getString(R.string.login_retrieving_account_information))

                response = okHttpClient.newCall(request).execute()

                responseBody = response.body()

                if (responseBody == null) {
                    return null
                }

                bodyResult = responseBody.string()

                if (response.isSuccessful && bodyResult != null) {
                    var jsonResult = JsonParser().parse(bodyResult)
                    val infomaniakUser = gson.fromJson(jsonResult.asJsonObject.getAsJsonObject("data"), InfomaniakUser::class.java)

                    val accounts: Array<Account> = AccountManager.get(loginActivity).getAccountsByType(loginActivity.getString(R.string.account_type))

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

                    publishProgress(loginActivity.getString(R.string.login_generating_an_application_password))

                    response = okHttpClient.newCall(request).execute()

                    responseBody = response.body()

                    if (responseBody == null) {
                        return null
                    }

                    bodyResult = responseBody.string()
                    if (response.isSuccessful && bodyResult != null) {
                        jsonResult = JsonParser().parse(bodyResult)
                        val infomaniakPassword = gson.fromJson(jsonResult.asJsonObject.getAsJsonObject("data"), InfomaniakPassword::class.java)

                        publishProgress(loginActivity.getText(R.string.login_querying_server))

                        loginActivity = activityReference.get()!!
                        val loginInfo = LoginInfo(URI(SYNC_INFOMANIAK), infomaniakUser.login, infomaniakPassword.password, null, infomaniakUser.display_name, infomaniakUser.email)
                        val configuration: DavResourceFinder.Configuration = DavResourceFinder(loginActivity.baseContext, loginInfo).findInitialConfiguration()

                        publishProgress(loginActivity.getString(R.string.login_finalising))

                        mutex.lock()
                        return configuration
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

        override fun onProgressUpdate(vararg text: CharSequence) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing) return

            activity.message_status.text = text.first()
        }

        override fun onPostExecute(configuration: DavResourceFinder.Configuration?) {
            activityReference.get()?.let { activity ->
                if (configuration == null) {
                    Toast.makeText(activity, activity.getString(R.string.an_error_has_occurred), Toast.LENGTH_LONG).show()
                    activity.backPressed()
                } else {

                    if (configuration.calDAV == null && configuration.cardDAV == null) {
                        Toast.makeText(activity, activity.getString(R.string.an_error_has_occurred), Toast.LENGTH_LONG).show()
                        activity.backPressed()
                    } else {
                        // service found: continue
                        activity.supportFragmentManager.beginTransaction()
                                .replace(android.R.id.content, AccountDetailsFragment.newInstance(configuration))
                                .addToBackStack(null)
                                .commit()
                    }
                }
            }
        }

        override fun onCancelled() {
            activityReference.get()?.let { activity ->
                Toast.makeText(activity, activity.getString(R.string.an_error_has_occurred), Toast.LENGTH_LONG).show()
                activity.backPressed()
            }
        }
    }
}
