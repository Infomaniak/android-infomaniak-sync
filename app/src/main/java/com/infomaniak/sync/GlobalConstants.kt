package com.infomaniak.sync

object GlobalConstants {

	const val APP_UID = "com.infomaniak.sync"
	const val CLIENT_ID = "CE011334-F75A-4263-9F9F-45FC5A142F59"

	const val REDIRECT_URI = "$APP_UID:/oauth2redirect"

	const val LOGIN_ENDPOINT = "https://login.infomaniak.com"
	const val AUTHORIZE_LOGIN_URL = "$LOGIN_ENDPOINT/authorize"
	const val TOKEN_LOGIN_URL = "$LOGIN_ENDPOINT/token"

	private const val API_ENDPOINT = "https://api.infomaniak.com/1"
	const val PROFILE_API_URL = "$API_ENDPOINT/profile"
	const val PASSWORD_API_URL = "$API_ENDPOINT/profile/password"

	const val SYNC_INFOMANIAK = "https://sync.infomaniak.com"
}