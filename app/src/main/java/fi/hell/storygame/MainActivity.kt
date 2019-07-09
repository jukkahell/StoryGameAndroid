package fi.hell.storygame

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager

import kotlinx.android.synthetic.main.activity_main.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.hell.storygame.model.*
import fi.hell.storygame.service.NotificationService.Companion.NOTIFICATION_ACTION
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener {
            val intent = Intent(this, CreateGameActivity::class.java)
            startActivity(intent)
        }

        LocalBroadcastManager.getInstance(this).registerReceiver((broadcastReceiver),
            IntentFilter(NOTIFICATION_ACTION)
        )
    }

    override fun onResume() {
        super.onResume()

        val authData = AuthService.getAuthData(this) ?: return
        getFCMToken(authData)

        val joinPath = if (intent.data != null) intent.data!!.path else intent.getStringExtra("joinPath")
        if (joinPath != null) {
            val gameId = joinPath.replace("/", "")
            Thread {
                URL(BuildConfig.BACKEND_URL +"/game/$gameId/join").openConnection()
                    .let {
                        it as HttpURLConnection
                    }
                    .apply {
                        addRequestProperty(
                            "Authorization", "Bearer ${authData.accessToken}"
                        )
                        requestMethod = "PUT"
                        doOutput = false
                    }.let {
                        if (it.responseCode == 202) {
                            runOnUiThread {
                                Toast.makeText(this, getString(R.string.joined), Toast.LENGTH_LONG).show()
                            }
                            finish()
                            overridePendingTransition(0, 0)
                            intent.data = null
                            intent.removeExtra("joinPath")
                            startActivity(intent)
                            overridePendingTransition(0, 0)
                        } else {
                            BufferedReader(InputStreamReader(it.errorStream)).use { buf ->
                                val response = StringBuffer()
                                var inputLine = buf.readLine()
                                while (inputLine != null) {
                                    response.append(inputLine)
                                    inputLine = buf.readLine()
                                }
                                buf.close()
                                val errorResponse = Gson().fromJson(response.toString(), HttpError::class.java)
                                val name = "error_${errorResponse.error}"
                                val id = resources.getIdentifier(name, "string", "fi.hell.storygame")
                                runOnUiThread {
                                    Toast.makeText(this, resources.getString(id), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
            }.start()
        }

        getGames(authData)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            return true
        } else if (item.itemId == R.id.action_logout) {
            AuthService.logout(this)
            val logoutIntent = Intent(this, LoginActivity::class.java)
            logoutIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(logoutIntent)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun getFCMToken(authData: AuthData) {
        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("MainActivity", "getInstanceId failed", task.exception)
                    return@OnCompleteListener
                }

                // Get new Instance ID token
                val token = task.result?.token ?: return@OnCompleteListener
                // If token hasn't changed
                if (token == authData.fcmToken) return@OnCompleteListener
                AuthService.updateFCMToken(this, token)

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
                            val fcmTokenJson = Gson().toJson(FCMToken(token = token))
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
            })
    }

    private fun getGames(authData: AuthData) {
        val gameList = findViewById<RecyclerView>(R.id.gameList)
        Thread {
            val gamesJson = URL(BuildConfig.BACKEND_URL +"/games").openConnection().apply {
                setRequestProperty(
                    "Authorization", "Bearer ${authData.accessToken}"
                )
            }.getInputStream().use {
                it.bufferedReader().use(BufferedReader::readText)
            }

            val games = Gson().fromJson<List<Game>>(gamesJson, object : TypeToken<List<Game>>() {}.type)
            val sortedGames = games
                .sortedByDescending { it.nextWriter.id == authData.userId }
                .sortedWith(compareBy { it.status.pos })
            runOnUiThread {
                gameList.layoutManager = LinearLayoutManager(this)
                gameList.adapter = GameListAdapter(sortedGames, this, authData.userId)
            }
        }.start()
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null) return
            val authData = AuthService.getAuthData(context) ?: return
            when (intent?.getStringExtra("type")) {
                "NEXT_WRITER" -> getGames(authData)
                "STORY_STARTED" -> getGames(authData)
            }
        }
    }
}
