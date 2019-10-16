package com.infomaniak.sync

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.util.Base64
import android.webkit.URLUtil
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import at.bitfire.davdroid.R
import com.google.android.material.snackbar.Snackbar
import java.security.MessageDigest
import java.security.SecureRandom

class CustomTab constructor(private val activity: Activity) {

	var codeChallengeMethod: String = "S256"
	lateinit var codeChallenge: String
	lateinit var codeVerifier: String

	private var tabClient: CustomTabsClient? = null
	private var tabConnection: CustomTabsServiceConnection? = null
	private val tabIntent: CustomTabsIntent by lazy {
		CustomTabsIntent.Builder()
			.apply {
				AppCompatResources
					.getDrawable(
						activity,
						R.drawable.ic_arrow_back_white_24dp
					)?.toBitmap()
					?.let { setCloseButtonIcon(it) }

				setToolbarColor(
					ContextCompat.getColor(
						activity,
						R.color.primaryColor
					)
				)
			}.run {
				build()
			}.also { customTabIntent ->
				customTabIntent.intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
			}
	}

	fun showTab(url: String) {
		if (URLUtil.isValidUrl(url)) {
			when {
				isChromeCustomTabsSupported(activity) -> bindCustomTabsService(
					url,
					CHROME_STABLE_PACKAGE
				)
				else -> {
					showOnDefaultBrowser(url)
				}
			}
		} else {
			Snackbar.make(
				activity.window.decorView,
				R.string.an_error_has_occurred,
				Snackbar.LENGTH_LONG
			).show()
		}
	}

	fun unbind() {
		try {
			activity.unbindService(tabConnection!!)
		} catch (ignore: Exception) {
		}
	}

	private fun showOnDefaultBrowser(url: String) {
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse(url)
			addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
		}

		try {
			activity.startActivity(intent)
		} catch (e: Exception) {
			Snackbar.make(
				activity.window.decorView,
				R.string.an_error_has_occurred,
				Snackbar.LENGTH_LONG
			).show()
		}
	}

	private fun bindCustomTabsService(url: String, chromePackage: String) {
		tabConnection = object : CustomTabsServiceConnection() {
			override fun onCustomTabsServiceConnected(
				componentName: ComponentName,
				client: CustomTabsClient
			) {
				tabClient = client
				launchCustomTab(url)
			}

			override fun onServiceDisconnected(name: ComponentName) {
				tabClient = null
			}
		}

		unbind()
		CustomTabsClient.bindCustomTabsService(activity, chromePackage, tabConnection)
	}

	private fun launchCustomTab(url: String) {
		tabClient?.warmup(0L)
		tabIntent.launchUrl(activity, Uri.parse(url))
	}

	private fun isChromeCustomTabsSupported(context: Context): Boolean {
		val serviceIntent = Intent(SERVICE_ACTION).apply {
			setPackage(CHROME_STABLE_PACKAGE)
		}
		val resolveInfos: MutableList<ResolveInfo>? = context.packageManager.queryIntentServices(serviceIntent, 0)
		return !resolveInfos.isNullOrEmpty()
	}

	companion object {
		private const val CHROME_STABLE_PACKAGE = "com.android.chrome"
		private const val SERVICE_ACTION = "android.support.customtabs.action.CustomTabsService"
	}

	fun getPkceCodes() {
		val preferenceName = "pkce_step_codes"
		val verifierTag = "code_verifier"
		val challengeTag = "code_challenge"

		val prefs = activity.getSharedPreferences(preferenceName, AppCompatActivity.MODE_PRIVATE)
		val verifier = prefs.getString(verifierTag, null)
		val challenge = prefs.getString(challengeTag, null)

		if (challenge == null || verifier == null) {
			codeVerifier = generateCodeVerifier()
			codeChallenge = generateCodeChallenge(codeVerifier)
			val editor = activity.getSharedPreferences(preferenceName, AppCompatActivity.MODE_PRIVATE).edit()
			editor.putString(verifierTag, codeVerifier)
			editor.putString(challengeTag, codeChallenge)
			editor.apply()
		} else {
			codeVerifier = verifier
			codeChallenge = challenge
		}
	}

	private fun generateCodeVerifier(): String {
		val sr = SecureRandom()
		val code = ByteArray(33)
		sr.nextBytes(code)
		return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
	}

	private fun generateCodeChallenge(codeVerifier: String): String {
		val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
		val md = MessageDigest.getInstance("SHA-256")
		md.update(bytes, 0, bytes.size)
		val digest = md.digest()
		return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
	}
}