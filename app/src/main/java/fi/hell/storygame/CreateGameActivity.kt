package fi.hell.storygame

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.isDigitsOnly
import com.google.gson.Gson
import fi.hell.storygame.model.*
import kotlinx.android.synthetic.main.write_story.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL


class CreateGameActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_game)
        title = getString(R.string.create_new_story)

        val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0]
        } else {
            resources.configuration.locale
        }
        val spinner = findViewById<Spinner>(R.id.language)
        val arrayAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            Language.getLocalesForSpinner(this).map { if (it.displayLanguage.isBlank()) getString(R.string.select_language) else it.displayName }
        )
        spinner.adapter = arrayAdapter
        val position = Language.getLocalesForSpinner(this).indexOf(currentLocale)
        spinner.setSelection(position)
    }

    fun createGame(view: View) {
        val authData = AuthService.getAuthData(this) ?: return

        val title = findViewById<EditText>(R.id.story_title)
        val locale = Language.getLocalesForSpinner(this)[findViewById<Spinner>(R.id.language).selectedItemPosition]
        val languageView = findViewById<Spinner>(R.id.language).selectedView as TextView
        val minWords = findViewById<EditText>(R.id.min_words)
        val maxWords = findViewById<EditText>(R.id.max_words)
        val rounds = findViewById<EditText>(R.id.rounds_per_user)
        val wordsVisible = findViewById<EditText>(R.id.words_visible)
        val privacy = PrivacyMode.values()[findViewById<Spinner>(R.id.privacy).selectedItemPosition]
        val maxParticipants = findViewById<EditText>(R.id.max_participants)

        if (title.text.isBlank()) {
            title.error = getString(R.string.title_must_be_set)
        }
        if (locale.language.isBlank() || locale.country.isBlank()) {
            languageView.error = getString(R.string.language_must_be_selected)
        }
        if (minWords.text.isBlank() || !minWords.text.isDigitsOnly() || minWords.text.toString().toInt() < 1) {
            minWords.error = getString(R.string.min_words_invalid)
        }
        if (maxWords.text.isBlank() || !maxWords.text.isDigitsOnly() || maxWords.text.toString().toInt() < 1) {
            maxWords.error = getString(R.string.max_words_invalid)
        } else if (minWords.text.isDigitsOnly() && minWords.text.toString().toInt() > maxWords.text.toString().toInt()) {
            maxWords.error = getString(R.string.max_words_smaller_than_min_words)
        }
        if (rounds.text.isBlank() || !rounds.text.isDigitsOnly() || rounds.text.toString().toInt() < 1) {
            rounds.error = getString(R.string.rounds_invalid)
        }
        if (wordsVisible.text.isNotBlank() && (!wordsVisible.text.isDigitsOnly() || wordsVisible.text.toString().toInt() < 0)) {
            wordsVisible.error = getString(R.string.words_visible_invalid)
        }
        if (title.error != null || languageView.error != null || minWords.error != null || maxWords.error != null || rounds.error != null || wordsVisible.error != null) {
            return
        }
        if (maxParticipants.text.isNotBlank() && (!maxParticipants.text.isDigitsOnly() || maxParticipants.text.toString().toInt() < 2)) {
            wordsVisible.error = getString(R.string.max_participants_invalid)
        }

        val localeString = if (!locale.language.isBlank()) "${locale.language}_${locale.country}" else ""

        Thread {
            var success = false
            try {
                URL(BuildConfig.BACKEND_URL +"/game").openConnection()
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
                        requestMethod = "POST"
                        doOutput = true
                        val outputWriter = OutputStreamWriter(outputStream)
                        val gameJson = Gson().toJson(CreateGame(
                            title = title.text.toString(),
                            settings = Settings(
                                localeString,
                                privacy,
                                minWords.text.toString().toInt(),
                                maxWords.text.toString().toInt(),
                                rounds.text.toString().toInt(),
                                wordsVisible = if (wordsVisible.text.isEmpty()) 0 else wordsVisible.text.toString().toInt(),
                                maxParticipants = if (maxParticipants.text.isEmpty()) 0 else maxParticipants.text.toString().toInt()
                            )
                        ))
                        outputWriter.write(gameJson.toString())
                        outputWriter.flush()
                    }.let {
                        if (it.responseCode == 201) {
                            success = true
                            runOnUiThread {
                                Toast.makeText(this, getString(R.string.story_created_successfully), Toast.LENGTH_LONG).show()
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
                                val errorResponse = Gson().fromJson(response.toString(), HttpError::class.java)
                                if (errorResponse.error.contains(", ")) {
                                    var errors = ""
                                    errorResponse.error.split(", ").forEach { error ->
                                        val id = resources.getIdentifier(error, "string", "fi.hell.storygame")
                                        errors += "\n" + resources.getString(id)
                                    }
                                    runOnUiThread {
                                        findViewById<TextView>(R.id.error).text = errors
                                    }
                                } else {
                                    val name = "error_${errorResponse.error}"
                                    val id =
                                        resources.getIdentifier(name, "string", "fi.hell.storygame")
                                    if (id == 0) {
                                        resources.getIdentifier(errorResponse.error, "string", "fi.hell.storygame")
                                    }
                                    runOnUiThread {
                                        findViewById<TextView>(R.id.error).text = resources.getString(id)
                                    }
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

            if (success) {
                runOnUiThread {
                    finish()
                }
            }
        }.start()
    }
}
