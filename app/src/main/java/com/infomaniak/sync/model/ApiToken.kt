package com.infomaniak.sync.model

class ApiToken(val errorAPI: ErrorAPI) {
    val access_token: String? = null
    val refresh_token: String? = null
    val token_type: String? = null
    val expires_in: Int = 0
    val user_id: Int = 0
    val scope: String? = null
}
