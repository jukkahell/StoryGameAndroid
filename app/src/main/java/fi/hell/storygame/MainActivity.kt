package fi.hell.storygame

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.tabs.TabLayout
import com.google.firebase.iid.FirebaseInstanceId
import com.google.gson.Gson
import fi.hell.storygame.model.AuthData
import fi.hell.storygame.model.FCMToken
import fi.hell.storygame.model.HttpError
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager: ViewPager = findViewById(R.id.view_pager)
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = findViewById(R.id.tabs)
        tabs.setupWithViewPager(viewPager)
    }

    override fun onResume() {
        super.onResume()

        val authData = AuthService.getAuthData(this) ?: return
        getFCMToken(authData)

        val joinPath = if (intent.data != null) intent.data!!.path else intent.getStringExtra("joinPath")
        if (joinPath != null) {
            val gameId = joinPath.replace("/", "")
            Thread {
                try {
                    URL(BuildConfig.BACKEND_URL +"/game/$gameId/join").openConnection()
                        .let {
                            it as HttpURLConnection
                        }
                        .apply {
                            addRequestProperty(
                                "Authorization", "Bearer ${authData.accessToken}"
                            )
                            connectTimeout = 10000
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
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            resources.getString(R.string.unable_to_connect_backend),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()
        }
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
                    try {
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
                                connectTimeout = 10000
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
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                resources.getString(R.string.unable_to_connect_backend),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.start()
            })
    }
}
