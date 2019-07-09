package fi.hell.storygame

import android.content.Context
import android.content.Intent
import fi.hell.storygame.model.AuthData
import fi.hell.storygame.model.LoginResponse

class AuthService {

    companion object {
        private const val PREF_NAME = "StoryGame"
        private const val TOKEN_KEY = "token"
        private const val USER_ID = "userId"
        private const val FCM_TOKEN = "fcmToken"

        private var authData: AuthData? = null

        fun getAuthData(context: Context, redirect: Boolean = true): AuthData? {
            if (authData != null) {
                return authData
            }
            val settings = context.getSharedPreferences(PREF_NAME, 0)
            val token =  settings.getString(TOKEN_KEY, null)
            val userId =  settings.getString(USER_ID, null)
            val fcmToken = settings.getString(FCM_TOKEN, null)
            if ((token == null || userId == null) && redirect) {
                val intent = Intent(context, LoginActivity::class.java)
                context.startActivity(intent)
                return null
            } else if (token == null || userId == null) {
                return null
            }
            authData = AuthData(token, userId, fcmToken)
            return authData
        }

        fun saveUserData(context: Context, loginResponse: LoginResponse) {
            val settings = context.getSharedPreferences(PREF_NAME, 0)
            settings.edit()
                .putString(TOKEN_KEY, loginResponse.token.access_token)
                .putString(USER_ID, loginResponse.user.id)
                .putString(FCM_TOKEN, loginResponse.user.fcm_token)
                .apply()
        }

        fun updateFCMToken(context: Context, token: String?) {
            val settings = context.getSharedPreferences(PREF_NAME, 0)
            settings.edit().putString(FCM_TOKEN, token).apply()
        }

        fun logout(context: Context) {
            authData = null
            val settings = context.getSharedPreferences(PREF_NAME, 0)
            settings.edit().clear().apply()
        }
    }
}