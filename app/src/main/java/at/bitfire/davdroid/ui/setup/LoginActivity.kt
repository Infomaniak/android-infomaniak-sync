/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.widget.TextView
import android.widget.Toast
import at.bitfire.davdroid.R
import com.facebook.stetho.okhttp3.StethoInterceptor
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
import java.util.*


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

//        internal const val LOGIN_URL_AUTHORIZE = "https://login.infomaniak.com/authorize"
//        private const val LOGIN_URL_TOKEN = "https://login.infomaniak.com/token"
        internal const val LOGIN_URL_AUTHORIZE = "https://login.beta.sharedbox.com/authorize"
        private const val LOGIN_URL_TOKEN = "https://login.beta.sharedbox.com/token"

        internal const val REDIRECT_URI_ROOT = "com.infomaniak.sync"

        internal const val CLIENT_ID = "CE011334-F75A-4263-9F9F-45FC5A142F59"

        private const val URL_API_PROFIL = "https://api.infomaniak.com/1/profile";
        private const val URL_API_PROFIL_PASSWORD = "https://api.infomaniak.com/1/profile/password"

        private const val URL_SYNC_INFOMANIAK = "https://sync.infomaniak.com"

        private var connection: GenerateInfomaniakAccountTask? = null
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.infomaniak_loading_view)

        val data = intent.data
        if (data != null) {
            if (REDIRECT_URI_ROOT == data.scheme) {
                val code = data.getQueryParameter("code")
                val error = data.getQueryParameter("error")
                if (!TextUtils.isEmpty(code)) {
                    connection = GenerateInfomaniakAccountTask(this, code)
                    connection!!.execute()
                }
                if (!TextUtils.isEmpty(error)) {
                    if (error == "access_denied") {

                    }
                    Toast.makeText(this, getString(R.string.an_error_has_occurred), Toast.LENGTH_LONG).show()
                    backPressed()
                }
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

    public override fun onDestroy() {
        super.onDestroy()

        connection!!.cancel(true)
    }

    class GenerateInfomaniakAccountTask internal constructor(context: LoginActivity, private val code: String) : AsyncTask<String, CharSequence, DavResourceFinder.Configuration>() {

        private val activityReference: WeakReference<LoginActivity> = WeakReference(context)

        override fun doInBackground(vararg params: String): DavResourceFinder.Configuration? {
            try {

                val okHttpClient = OkHttpClient.Builder()
                        .addNetworkInterceptor(StethoInterceptor())
                        .build()

                val gson = Gson()


                var formBuilder: MultipartBody.Builder = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("grant_type", "authorization_code")
                        .addFormDataPart("client_id", CLIENT_ID)
                        .addFormDataPart("code", code)
                        .addFormDataPart("redirect_uri", "$REDIRECT_URI_ROOT:/oauth2redirect")

                var request = Request.Builder()
                        .url(LOGIN_URL_TOKEN)
                        .post(formBuilder.build())
                        .build()

                var response = okHttpClient.newCall(request).execute()

                var responseBody: ResponseBody? = response.body() ?: return null

                var bodyResult: String? = responseBody!!.string()

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
                        .url(URL_API_PROFIL)
                        .header("Authorization", "Bearer " + apiToken.access_token)
                        .get()
                        .build()

                publishProgress("Récupération des informations du compte ...")

                response = okHttpClient.newCall(request).execute()

                responseBody = response.body()

                if (responseBody == null) {
                    return null
                }

                bodyResult = responseBody.string()

                if (response.isSuccessful && bodyResult != null) {
                    var jsonResult = JsonParser().parse(bodyResult)
                    val infomaniakUser = gson.fromJson(jsonResult.asJsonObject.getAsJsonObject("data"), InfomaniakUser::class.java)

                    formBuilder = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("name", "InfomaniakSync_" + Date())

                    request = Request.Builder()
                            .url(URL_API_PROFIL_PASSWORD)
                            .header("Authorization", "Bearer " + apiToken.access_token)
                            .post(formBuilder.build())
                            .build()

                    publishProgress("Génération d'un mot de passe d'application ...")

                    response = okHttpClient.newCall(request).execute()

                    responseBody = response.body()

                    if (responseBody == null) {
                        return null
                    }

                    bodyResult = responseBody.string()
                    if (response.isSuccessful && bodyResult != null) {
                        jsonResult = JsonParser().parse(bodyResult)
                        val infomaniakPassword = gson.fromJson(jsonResult.asJsonObject.getAsJsonObject("data"), InfomaniakPassword::class.java)

                        val loginActivity = activityReference.get()
                        if (loginActivity != null) {

                            publishProgress(loginActivity.getText(R.string.login_querying_server))

                            val loginInfo = LoginInfo(URI(URL_SYNC_INFOMANIAK), infomaniakUser.login, infomaniakPassword.password, null, infomaniakUser.display_name)
                            val configuration: DavResourceFinder.Configuration = DavResourceFinder(loginActivity.baseContext, loginInfo).findInitialConfiguration()

                            publishProgress("Finalisation ...")

                            return configuration
                        }
                    }
                }
                return null
            } catch (exception: Exception) {
                exception.printStackTrace()
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
                        val tv = TextView(activity)
                        tv.text = ""
                        activity.setContentView(tv)
                        // no service found: show error message
                        activity.supportFragmentManager.beginTransaction()
                                .add(DetectConfigurationFragment.NothingDetectedFragment.newInstance(configuration.logs), null)
                                .commit()
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
    }
}
