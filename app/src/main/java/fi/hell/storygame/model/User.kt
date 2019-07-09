package fi.hell.storygame.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class User(
    val id: String,
    val username: String,
    val locale: String?,
    val fcm_token: String?
): Parcelable

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val token: Token,
    val user: User,
    val newUser: Boolean
)

data class Token(
    val expires_in: String,
    val access_token: String
)

data class AuthData(
    val accessToken: String,
    val userId: String,
    val fcmToken: String?
)

data class FCMToken(
    val token: String
)