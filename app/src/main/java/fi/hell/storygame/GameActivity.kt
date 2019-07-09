package fi.hell.storygame

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.text.style.StyleSpan
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.hell.storygame.model.*
import fi.hell.storygame.service.NotificationService


class GameActivity : AppCompatActivity() {

    private lateinit var game: Game

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = intent.getParcelableExtra("game")
        val authData = AuthService.getAuthData(this) ?: return

        title = game.title
        if (game.status == GameStatus.CREATED && game.owner == authData.userId) {
            setContentView(R.layout.start_game)
            findViewById<TextView>(R.id.participants).text = getString(R.string.participants_count, game.users.size)
        } else if (game.status == GameStatus.FINISHED) {
            storyFinished(authData)
        } else if (game.nextWriter.id == authData.userId) {
            startWriting(authData)
        } else if (game.status == GameStatus.STARTED) {
            waitForTurn()
        } else {
            setContentView(R.layout.activity_wait_for_turn)
            findViewById<TextView>(R.id.wait_info).text = getString(R.string.game_not_started)
        }

        LocalBroadcastManager.getInstance(this).registerReceiver((broadcastReceiver),
            IntentFilter(NotificationService.NOTIFICATION_ACTION)
        )
    }

    private fun getAllStories(authData: AuthData) {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE
        Thread {
            val storiesJson = URL(BuildConfig.BACKEND_URL +"/allStories").openConnection().apply {
                addRequestProperty(
                    "Authorization", "Bearer ${authData.accessToken}"
                )
                addRequestProperty(
                    "game-id", game.id
                )
            }.getInputStream().use {
                it.bufferedReader().use(BufferedReader::readText)
            }

            val stories = Gson().fromJson<List<Story>>(storiesJson, object : TypeToken<List<Story>>() {}.type)
            runOnUiThread {
                findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
                val storyContainer = findViewById<RecyclerView>(R.id.story_container)
                storyContainer.layoutManager = LinearLayoutManager(this)
                storyContainer.adapter = StoriesAdapter(stories, this, authData.userId)
            }
        }.start()
    }

    private fun getPreviousStory(authData: AuthData) {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE
        Thread {
            val story = URL(BuildConfig.BACKEND_URL +"/story").openConnection().apply {
                addRequestProperty(
                    "Authorization", "Bearer ${authData.accessToken}"
                )
                addRequestProperty(
                    "game-id", game.id
                )
            }.getInputStream().use {
                it.bufferedReader().use(BufferedReader::readText)
            }

            runOnUiThread {
                findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
                val previousTextView = findViewById<TextView>(R.id.previousText)
                previousTextView.text = story
                previousTextView.movementMethod = ScrollingMovementMethod.getInstance()
            }
        }.start()
    }

    private fun setTextListener() {
        val storyInput = findViewById<EditText>(R.id.story)
        storyInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val words = s.toString().trim().split(" ")
                if (s != null && words.isNotEmpty()) {
                    val textLength = s.toString().length
                    val highlightedWords = if (game.settings.wordsVisible == 0) words.size else min(game.settings.wordsVisible, words.size)
                    val highlightedWordsLength = words.subList(words.size - highlightedWords, words.size).joinToString(" ").length
                    storyInput.removeTextChangedListener(this)
                    val spannable = SpannableString(s.toString())
                    spannable.setSpan(StyleSpan(Typeface.BOLD), textLength - highlightedWordsLength, textLength, 0)
                    storyInput.setText(spannable)
                    storyInput.setSelection(s.toString().length)
                    storyInput.addTextChangedListener(this)
                }
                findViewById<TextView>(R.id.words_left).text = "${words.size}/${game.settings.minWords}-${game.settings.maxWords}"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    fun share(view: View) {
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.type = "text/plain"
        val shareBody = "https://storygame.hell.fi/${game.id}"
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_default_text))
        sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody)
        startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_title)))
    }

    fun startGame(view: View) {
        val authData = AuthService.getAuthData(this) ?: return
        findViewById<ProgressBar>(R.id.starting_progress).visibility = View.VISIBLE
        findViewById<Button>(R.id.start_button).visibility = View.GONE
        Thread {
            URL(BuildConfig.BACKEND_URL +"/game/${game.id}/start").openConnection()
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
                        game = game.copy(status = GameStatus.STARTED)
                        runOnUiThread {
                            findViewById<ProgressBar>(R.id.starting_progress).visibility = View.GONE
                            Toast.makeText(this, getString(R.string.story_started), Toast.LENGTH_LONG).show()
                        }
                        finish()
                        overridePendingTransition(0, 0);
                        intent.putExtra("game", game)
                        startActivity(intent)
                        overridePendingTransition(0, 0);
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
                                findViewById<ProgressBar>(R.id.starting_progress).visibility = View.GONE
                                findViewById<Button>(R.id.start_button).visibility = View.VISIBLE
                                Toast.makeText(this, resources.getString(id), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
        }.start()
    }

    fun submitText(view: View) {
        val authData = AuthService.getAuthData(this) ?: return
        val story = findViewById<EditText>(R.id.story)
        val words = story.text.toString().split(" ")
        if (words.size < game.settings.minWords) {
             story.error = getString(R.string.too_few_words, game.settings.minWords)
            return
        } else if (words.size > game.settings.maxWords) {
            story.error = getString(R.string.too_many_words, game.settings.maxWords)
            return
        }
        Thread {
            var success = false
            URL(BuildConfig.BACKEND_URL +"/story").openConnection()
                .let {
                    it as HttpURLConnection
                }
                .apply {
                    addRequestProperty(
                        "Authorization", "Bearer ${authData.accessToken}"
                    )
                    addRequestProperty(
                        "game-id", game.id
                    )
                    addRequestProperty(
                        "content-type", "application/json"
                    )
                    requestMethod = "POST"
                    doOutput = true
                    val outputWriter = OutputStreamWriter(outputStream)
                    val storyJson = Gson().toJson(Story(story.text.toString(), authData.userId))
                    outputWriter.write(storyJson.toString())
                    outputWriter.flush()
                }.let {
                    if (it.responseCode == 201) {
                        success = true
                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.story_sent_successfully), Toast.LENGTH_LONG).show()
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

    private fun startWriting(authData: AuthData) {
        setContentView(R.layout.write_story)
        getPreviousStory(authData)
        setTextListener()
    }

    private fun storyStarted(nextWriter: User, authData: AuthData) {
        if (nextWriter.id == authData.userId) {
            startWriting(authData)
        } else {
            game.nextWriter = nextWriter
            waitForTurn()
        }
    }

    private fun storyFinished(authData: AuthData) {
        setContentView(R.layout.finished_story)
        findViewById<TextView>(R.id.title).text = game.title
        getAllStories(authData)
    }

    private fun waitForTurn() {
        setContentView(R.layout.activity_wait_for_turn)
        findViewById<TextView>(R.id.wait_info).text =
            getString(R.string.wait_info, game.nextWriter.username)
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null) return
            val authData = AuthService.getAuthData(context) ?: return
            when (intent?.getStringExtra("type")) {
                "NEXT_WRITER" -> startWriting(authData)
                "STORY_STARTED" -> storyStarted(intent.getParcelableExtra("nextWriter"), authData)
                "STORY_FINISHED" -> storyFinished(authData)
            }
        }
    }
}