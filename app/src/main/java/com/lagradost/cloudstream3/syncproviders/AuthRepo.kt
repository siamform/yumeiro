@file:Suppress("unused")

package com.lagradost.cloudstream3.syncproviders

/** Wraps an [AuthAPI]. Yumeiro keeps no tracker sessions, so there is never a signed-in user. */
abstract class AuthRepo(open val api: AuthAPI) {
    val idPrefix: String get() = api.idPrefix
    val name: String get() = api.name
    val icon: Int? get() = api.icon
    val requiresLogin: Boolean get() = api.requiresLogin
    val createAccountUrl: String? get() = api.createAccountUrl
    val hasOAuth2: Boolean get() = api.hasOAuth2
    val hasPin: Boolean get() = api.hasPin
    val hasInApp: Boolean get() = api.hasInApp

    fun authData(): AuthData? = null
    fun authToken(): AuthToken? = authData()?.token
    fun authUser(): AuthUser? = authData()?.user
    fun isAvailable(): Boolean = !requiresLogin || authData() != null
    val accounts: Array<AuthData> get() = emptyArray()
    var accountId: Int
        get() = -1
        set(@Suppress("UNUSED_PARAMETER") value) {}
}
