package fi.hell.storygame

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.gson.Gson
import fi.hell.storygame.model.CreateGame
import fi.hell.storygame.model.HttpError
import fi.hell.storygame.model.LoginRequest
import fi.hell.storygame.model.Settings
import kotlinx.android.synthetic.main.write_story.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
    }

    fun register(view: View) {
        findViewById<TextView>(R.id.error).visibility = View.GONE
        val username = findViewById<EditText>(R.id.username)
        val password = findViewById<EditText>(R.id.password)

        if (username.text == null || username.text.length < 2) {
            username.error = getString(R.string.invalid_username)
        }
        if (password.text == null || password.text.length <= 5) {
            password.error = getString(R.string.invalid_password)
        }
        if (password.error != null || username.error != null) {
            return
        }

        Thread {
            var success = false
            URL(BuildConfig.BACKEND_URL +"/user").openConnection()
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
                    val userJson = Gson().toJson(
                        LoginRequest(
                            username = username.text.toString(),
                            password = password.text.toString()
                        )
                    )
                    outputWriter.write(userJson.toString())
                    outputWriter.flush()
                }.let {
                    if (it.responseCode == 201) {
                        success = true
                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.registered_successfully), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        BufferedReader(InputStreamReader(it.errorStream)).use { buf ->
                            val response = StringBuffer()
                            var inputLine = buf.readLine()
                            while (inputLine != null) {
                                response.append(inputLine)
                                inputLine = buf.readLine()
                            }
                            buf.close()
                            println(response.toString())
                            val errorResponse = Gson().fromJson(response.toString(), HttpError::class.java)
                            val name = "error_${errorResponse.error}"
                            val id = resources.getIdentifier(name, "string", "fi.hell.storygame")
                            runOnUiThread {
                                findViewById<TextView>(R.id.error).text = resources.getString(id)
                                findViewById<TextView>(R.id.error).visibility = View.VISIBLE
                            }
                        }
                    }
                }

            if (success) {
                runOnUiThread {
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.putExtra("username", username.text.toString())
                    startActivity(intent)
                }
            }
        }.start()
    }
}
