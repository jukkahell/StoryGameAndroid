package fi.hell.storygame

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.isDigitsOnly
import com.google.gson.Gson
import fi.hell.storygame.model.CreateGame
import fi.hell.storygame.model.HttpError
import fi.hell.storygame.model.Language
import fi.hell.storygame.model.Settings
import kotlinx.android.synthetic.main.write_story.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*


class CreateGameActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_game)

        val spinner = findViewById<Spinner>(R.id.language)
        val arrayAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            Language.getLocalesForSpinner(this).map { if (it.displayLanguage.isBlank()) getString(R.string.select_language) else it.displayName }
        )
        spinner.adapter = arrayAdapter
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
        val public = findViewById<ToggleButton>(R.id.is_public).isChecked

        if (title.text.isEmpty()) {
            title.error = getString(R.string.title_must_be_set)
        }
        if (locale.language.isBlank() || locale.country.isBlank()) {
            languageView.error = getString(R.string.language_must_be_selected)
        }
        if (minWords.text.isEmpty() || !minWords.text.isDigitsOnly() || minWords.text.toString().toInt() < 1) {
            minWords.error = getString(R.string.min_words_invalid)
        }
        if (maxWords.text.isEmpty() || !maxWords.text.isDigitsOnly() || maxWords.text.toString().toInt() < 1) {
            maxWords.error = getString(R.string.max_words_invalid)
        }
        if (rounds.text.isEmpty() || !rounds.text.isDigitsOnly() || rounds.text.toString().toInt() < 1) {
            rounds.error = getString(R.string.rounds_invalid)
        }
        if (wordsVisible.text.isNotEmpty() && (wordsVisible.text.toString().toInt() < 0 || !wordsVisible.text.isDigitsOnly())) {
            wordsVisible.error = getString(R.string.words_visible_invalid)
        }
        if (title.error != null || languageView.error != null || minWords.error != null || maxWords.error != null || rounds.error != null || wordsVisible.error != null) {
            return
        }

        Thread {
            var success = false
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
                    requestMethod = "POST"
                    doOutput = true
                    val outputWriter = OutputStreamWriter(outputStream)
                    val gameJson = Gson().toJson(CreateGame(
                        title = title.text.toString(),
                        settings = Settings(
                            "${locale.language}_${locale.country}",
                            public,
                            minWords.text.toString().toInt(),
                            maxWords.text.toString().toInt(),
                            rounds.text.toString().toInt(),
                            wordsVisible = if (wordsVisible.text.isEmpty()) 0 else wordsVisible.text.toString().toInt()
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
                            println(response.toString())
                            val errorResponse = Gson().fromJson(response.toString(), HttpError::class.java)
                            val name = "error_${errorResponse.error}"
                            val id = resources.getIdentifier(name, "string", "fi.hell.storygame")
                            runOnUiThread {
                                story.error = resources.getString(id)
                            }
                        }
                    }
                }

            if (success) {
                runOnUiThread {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                }
            }
        }.start()
    }
}
