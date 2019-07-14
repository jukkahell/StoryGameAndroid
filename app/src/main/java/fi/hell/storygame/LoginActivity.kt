package fi.hell.storygame

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import fi.hell.storygame.model.HttpError
import fi.hell.storygame.model.LoginRequest
import fi.hell.storygame.model.LoginResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class LoginActivity: AppCompatActivity()  {

    private lateinit var viewPager: ViewPager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val loginPagerAdapter = LoginPagerAdapter(this, supportFragmentManager)
        viewPager = findViewById(R.id.login_pager)
        viewPager.adapter = loginPagerAdapter
        val tabs: TabLayout = findViewById(R.id.tabs)
        tabs.setupWithViewPager(viewPager)
    }

    override fun onResume() {
        super.onResume()
        if (!isInternetAvailable()) {
            setContentView(R.layout.no_internet)
            return
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
            try {
                URL(BuildConfig.BACKEND_URL + "/login").openConnection()
                    .let {
                        it as HttpURLConnection
                    }
                    .apply {
                        addRequestProperty(
                            "content-type", "application/json"
                        )
                        connectTimeout = 10000
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
                                    Toast.makeText(
                                        this,
                                        getString(R.string.login_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
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

    fun register(view: View) {
        findViewById<TextView>(R.id.error).visibility = View.GONE
        val username = findViewById<EditText>(R.id.register_username)
        val password = findViewById<EditText>(R.id.register_password)

        if (username.text == null || username.text.length < 2) {
            username.error = getString(R.string.invalid_username)
        }
        if (password.text == null || password.text.length < 8) {
            password.error = getString(R.string.invalid_password)
        }
        if (password.error != null || username.error != null) {
            return
        }

        Thread {
            var success = false
            try {
                URL(BuildConfig.BACKEND_URL + "/user").openConnection()
                    .let {
                        it as HttpURLConnection
                    }
                    .apply {
                        addRequestProperty(
                            "content-type", "application/json"
                        )
                        connectTimeout = 10000
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
                                Toast.makeText(
                                    this,
                                    getString(R.string.registered_successfully),
                                    Toast.LENGTH_LONG
                                ).show()
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
                                val errorResponse =
                                    Gson().fromJson(response.toString(), HttpError::class.java)
                                val name = "error_${errorResponse.error}"
                                val id =
                                    resources.getIdentifier(name, "string", "fi.hell.storygame")
                                runOnUiThread {
                                    findViewById<TextView>(R.id.error).text =
                                        resources.getString(id)
                                    findViewById<TextView>(R.id.error).visibility = View.VISIBLE
                                }
                            }
                        }
                    }
            }  catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        resources.getString(R.string.unable_to_connect_backend),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            if (success) {
                runOnUiThread {
                    findViewById<EditText>(R.id.username).text = username.text
                    viewPager.currentItem = 0
                }
            }
        }.start()
    }

    private fun openMainActivity() {
        val mainIntent = Intent(this, MainActivity::class.java)
        if (intent.data != null) {
            mainIntent.putExtra("joinPath", intent.data!!.path)
        }
        startActivity(mainIntent)
    }

    private fun isInternetAvailable(): Boolean {
        var connected: InetAddress? = null
        try {
            val future =
                Executors.newSingleThreadExecutor().submit(Callable<InetAddress> {
                    try {
                        InetAddress.getByName("storygame.hell.fi")
                    } catch (e: java.lang.Exception) {
                        null
                    }
                })
            connected = future.get(5000, TimeUnit.MILLISECONDS)
            future.cancel(true)
        } catch (e: java.lang.Exception) {}
        println(connected)
        return connected != null && connected.toString() != ""
    }
}