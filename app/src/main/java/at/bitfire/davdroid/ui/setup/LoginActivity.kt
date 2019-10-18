/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.UiUtils
import com.infomaniak.sync.ui.InfomaniakDetectConfigurationFragment
import java.util.*

/**
 * Activity to initially connect to a server and create an account.
 * Fields for server/user data can be pre-filled with extras in the Intent.
 */
class LoginActivity: AppCompatActivity() {

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
    }

//    private val loginFragmentLoader = ServiceLoader.load(ILoginCredentialsFragment::class.java)!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            // first call, add first login fragment
            var fragment: Fragment? = null
//            for (factory in loginFragmentLoader)
//                fragment = fragment ?: factory.getFragment(intent)


            if (intent != null) {
                val code = intent.getStringExtra("code")
                val codeVerifier = intent.getStringExtra("verifier")
                if (!TextUtils.isEmpty(code) && !TextUtils.isEmpty(codeVerifier)) {
                    fragment = InfomaniakDetectConfigurationFragment.newInstance(code!!, codeVerifier!!)
                }
            }

            if (fragment != null) {
                supportFragmentManager.beginTransaction()
                        .replace(android.R.id.content, fragment)
                        .commit()
            } else {
                Logger.log.severe("Couldn't create LoginFragment")
                Toast.makeText(this, getString(R.string.an_error_has_occurred), Toast.LENGTH_LONG).show()
                onBackPressed()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_login, menu)
        return true
    }

    fun showHelp(item: MenuItem) {
        UiUtils.launchUri(this, App.homepageUrl(this).buildUpon()
                .appendPath("tested-with")
                .build())
    }

}
