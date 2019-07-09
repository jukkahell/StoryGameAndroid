package fi.hell.storygame

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import fi.hell.storygame.model.LoginRequest
import fi.hell.storygame.model.LoginResponse
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class LoginActivity: AppCompatActivity()  {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
    }

    override fun onResume() {
        super.onResume()

        val username = intent.getStringExtra("username")
        if (username != null) {
            findViewById<EditText>(R.id.username).text.append(username)
        }
        val authData = AuthService.getAuthData(this, false)
        if (authData != null) {
            openMainActivity()
        }
    }

    fun login(view: View) {
        val username = findViewById<EditText>(R.id.username)
        val password = findViewById<EditText>(R.id.password)
        findViewById<ProgressBar>(R.id.loading).visibility = View.VISIBLE
        Thread {
            URL(BuildConfig.BACKEND_URL +"/login").openConnection()
                .let {
                    it as HttpURLConnection
                }
                .apply {
                    addRequestProperty(
                        "content-type", "application/json"
                    )
                    requestMethod = "POST"
                    doOutput = true
                    val outputWriter = OutputStreamWriter(outputStream)
                    val loginJson = Gson().toJson(
                        LoginRequest(
                            username = username.text.toString(),
                            password = password.text.toString()
                        )
                    )
                    outputWriter.write(loginJson.toString())
                    outputWriter.flush()
                }.let {
                    runOnUiThread {
                        findViewById<ProgressBar>(R.id.loading).visibility = View.GONE
                    }
                    if (it.responseCode == 201) {
                        val reader = InputStreamReader(it.inputStream, "UTF-8")
                        val loginResponse = Gson().fromJson(reader, LoginResponse::class.java)
                        AuthService.saveUserData(applicationContext, loginResponse)
                        openMainActivity()
                    } else {
                        runOnUiThread {
                            runOnUiThread {
                                Toast.makeText(this, getString(R.string.login_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
        }.start()
    }

    fun openMainActivity() {
        val mainIntent = Intent(this, MainActivity::class.java)
        if (intent.data != null) {
            mainIntent.putExtra("joinPath", intent.data!!.path)
        }
        startActivity(mainIntent)
    }

    fun register(view: View) {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }
}