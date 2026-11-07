@file:Suppress("unused", "UNUSED_PARAMETER")

package com.lagradost.cloudstream3.syncproviders

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

/*
 * Account / tracker surface that CloudStream extensions link against (AniList, MAL, Simkl, ...).
 *
 * Yumeiro does not sign in to trackers on behalf of extensions, so every account reports
 * "not logged in" and library calls return nothing. Extensions that only *use* these classes for
 * optional features (continue-watching rows, tracker artwork, recommendations parsed from public
 * APIs) load and work normally instead of failing with NoClassDefFoundError.
 * Names and JVM signatures must stay binary compatible with CloudStream.
 */

data class AuthLoginPage(
    val url: String,
    val payload: String? = null,
)

data class AuthToken(
    @JsonProperty("accessToken") val accessToken: String? = null,
    @JsonProperty("refreshToken") val refreshToken: String? = null,
    @JsonProperty("accessTokenLifetime") val accessTokenLifetime: Long? = null,
    @JsonProperty("refreshTokenLifetime") val refreshTokenLifetime: Long? = null,
    @JsonProperty("payload") val payload: String? = null,
) {
    fun isAccessTokenExpired(marginSec: Long = 10L): Boolean =
        accessTokenLifetime != null && System.currentTimeMillis() / 1000L + marginSec >= accessTokenLifetime

    fun isRefreshTokenExpired(marginSec: Long = 10L): Boolean =
        refreshTokenLifetime != null && System.currentTimeMillis() / 1000L + marginSec >= refreshTokenLifetime
}

data class AuthUser(
    @JsonProperty("name") val name: String?,
    @JsonProperty("id") val id: Int,
    @JsonProperty("profilePicture") val profilePicture: String? = null,
    @JsonProperty("profilePictureHeaders") @JsonAlias("profilePictureHeader")
    val profilePictureHeaders: Map<String, String>? = null,
)

data class AuthData(
    @JsonProperty("user") val user: AuthUser,
    @JsonProperty("token") val token: AuthToken,
)

data class AuthPinData(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresIn: Int,
    val interval: Int,
)

data class AuthLoginRequirement(
    val password: Boolean = false,
    val username: Boolean = false,
    val email: Boolean = false,
    val server: Boolean = false,
)

data class AuthLoginResponse(
    @JsonProperty("password") val password: String?,
    @JsonProperty("username") val username: String?,
    @JsonProperty("email") val email: String?,
    @JsonProperty("server") val server: String?,
)

abstract class AuthAPI {
    open val name: String = "NONE"
    open val idPrefix: String = "NONE"
    open val icon: Int? = null
    open val requiresLogin: Boolean = true
    open val createAccountUrl: String? = null
    open val redirectUrlIdentifier: String? = null
    open val hasOAuth2: Boolean = false
    open val hasPin: Boolean = false
    open val hasInApp: Boolean = false
    open val inAppLoginRequirement: AuthLoginRequirement? = null

    open suspend fun login(redirectUrl: String, payload: String?): AuthToken? = null
    open fun loginRequest(): AuthLoginPage? = null
    open suspend fun pinRequest(): AuthPinData? = null
    open suspend fun login(payload: AuthPinData): AuthToken? = null
    open suspend fun login(form: AuthLoginResponse): AuthToken? = null
    open suspend fun refreshToken(token: AuthToken): AuthToken? = null
    open suspend fun user(token: AuthToken?): AuthUser? = null
    open suspend fun invalidateToken(token: AuthToken): Nothing = throw NotImplementedError()

    /** Old (pre-4.6) account API kept for extensions that still call it. Never logged in here. */
    open fun loginInfo(): LoginInfo? = null

    class LoginInfo(
        val profilePicture: String? = null,
        val name: String?,
        val accountIndex: Int,
    )
}
