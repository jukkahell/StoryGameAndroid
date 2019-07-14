package fi.hell.storygame.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal
import java.util.*

enum class GameStatus(val pos: Int) {
    @SerializedName("created")
    CREATED(1),
    @SerializedName("started")
    STARTED(0),
    @SerializedName("finished")
    FINISHED(2)
}

enum class PrivacyMode(val pos: Int) {
    @SerializedName("private")
    PRIVATE(0),
    @SerializedName("public_read")
    PUBLIC_READ(1),
    @SerializedName("public_read_write")
    PUBLIC_READ_WRITE(2)
}

@Parcelize
data class Game(
    val id: String,
    val title: String,
    val settings: Settings,
    val status: GameStatus,
    val owner: String,
    val created: Date,
    var nextWriter: User,
    val users: List<User>,
    val stories: List<Story>
): Parcelable

data class CreateGame(
    val title: String,
    val settings: Settings
)

@Parcelize
data class Settings(
    val locale: String,
    val privacy: PrivacyMode,
    val minWords: Int,
    val maxWords: Int,
    val roundsPerUser: Int,
    val wordsVisible: Int,
    val maxParticipants: Int
    ): Parcelable

data class HttpError(
    val statusCode: Int,
    val error: String,
    val message: String
)

@Parcelize
data class Story(
    val text: String,
    val author: String?
): Parcelable