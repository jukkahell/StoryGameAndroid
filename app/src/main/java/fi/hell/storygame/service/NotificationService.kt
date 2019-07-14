package fi.hell.storygame.service

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import fi.hell.storygame.AuthService
import fi.hell.storygame.BuildConfig
import fi.hell.storygame.model.FCMToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import fi.hell.storygame.model.User

class NotificationService: FirebaseMessagingService() {

    private var broadcaster: LocalBroadcastManager? = null

    override fun onCreate() {
        broadcaster = LocalBroadcastManager.getInstance(this)
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String?) {
        Log.d(TAG, "Refreshed token: $token")
        AuthService.updateFCMToken(this, token)
        val authData = AuthService.getAuthData(this, false) ?: return
        Thread {
            URL(BuildConfig.BACKEND_URL +"/user/fcmToken").openConnection()
                .let {
                    it as HttpURLConnection
                }
                .apply {
                    addRequestProperty(
                        "Authorization", "Bearer ${authData.accessToken}"
                    )
                    addRequestProperty(
                        "content-type", "application/json"
                    )
                    requestMethod = "PUT"
                    doOutput = true
                    val outputWriter = OutputStreamWriter(outputStream)
                    val fcmTokenJson = Gson().toJson(FCMToken(token = token!!))
                    outputWriter.write(fcmTokenJson.toString())
                    outputWriter.flush()
                }.let {
                    if (it.responseCode != 200) {
                        BufferedReader(InputStreamReader(it.errorStream)).use { buf ->
                            val response = StringBuffer()
                            var inputLine = buf.readLine()
                            while (inputLine != null) {
                                response.append(inputLine)
                                inputLine = buf.readLine()
                            }
                            buf.close()
                            println(response.toString())
                        }
                    }
                }
        }.start()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        // Check if message contains a data payload.
        remoteMessage?.data?.isNotEmpty()?.let {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)

            val intent = Intent(NOTIFICATION_ACTION)
            val type = remoteMessage.data["type"]
            intent.putExtra("gameId", remoteMessage.data["gameId"])
            intent.putExtra("type", type)
            if (type == "STORY_STARTED") {
                val nextWriter = Gson().fromJson(remoteMessage.data["nextWriter"], User::class.java)
                intent.putExtra("nextWriter", nextWriter)
            }
            if (type == "USER_JOINED") {
                intent.putExtra("participants", remoteMessage.data["participants"]!!.toInt())
            }
            broadcaster!!.sendBroadcast(intent)
        }

        // Check if message contains a notification payload.
        remoteMessage?.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }
    }

    companion object {
        private const val TAG = "FMS"
        const val NOTIFICATION_ACTION = "fi.hell.storygame.NOTIFICATION_RECEIVED"
    }
}