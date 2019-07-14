package fi.hell.storygame

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.hell.storygame.model.*
import fi.hell.storygame.service.NotificationService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min


class GameActivity : AppCompatActivity() {

    private lateinit var game: Game

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = intent.getParcelableExtra("game")
        val authData = AuthService.getAuthData(this) ?: return

        val part = (game.stories.size - game.stories.size % game.users.size) / game.users.size + 1
        title = "${game.title} ($part/${game.settings.roundsPerUser})"

        if (game.status == GameStatus.CREATED && game.owner == authData.userId) {
            setContentView(R.layout.start_game)
            usersJoined(game.users.size)
        } else if (game.status == GameStatus.FINISHED) {
            title = game.title
            storyFinished(authData)
        } else if (game.nextWriter.id == authData.userId) {
            startWriting(authData, game.stories.size)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val authData = AuthService.getAuthData(this) ?: return false
        if (game.owner == authData.userId) {
            menuInflater.inflate(R.menu.menu_game_owner, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.app_bar_delete) {
            deleteStory()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun deleteStory() {
        val authData = AuthService.getAuthData(this) ?: return
        Thread {
            try {
                URL(BuildConfig.BACKEND_URL + "/game/${game.id}").openConnection()
                    .let {
                        it as HttpURLConnection
                    }
                    .apply {
                        addRequestProperty(
                            "Authorization", "Bearer ${authData.accessToken}"
                        )
                        connectTimeout = 10000
                        requestMethod = "DELETE"
                        doOutput = false
                    }.let {
                        if (it.responseCode == 200) {
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    getString(R.string.story_deleted),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            finish()
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
                                    Toast.makeText(this, resources.getString(id), Toast.LENGTH_LONG)
                                        .show()
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

    private fun getAllStories(authData: AuthData) {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE
        Thread {
            try {
                val storiesJson =
                    URL(BuildConfig.BACKEND_URL + "/allStories").openConnection().apply {
                        addRequestProperty(
                            "Authorization", "Bearer ${authData.accessToken}"
                        )
                        addRequestProperty(
                            "game-id", game.id
                        )
                    }.getInputStream().use {
                        it.bufferedReader().use(BufferedReader::readText)
                    }

                val stories = Gson().fromJson<List<Story>>(
                    storiesJson,
                    object : TypeToken<List<Story>>() {}.type
                )
                runOnUiThread {
                    findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
                    val storyContainer = findViewById<RecyclerView>(R.id.story_container)
                    storyContainer.layoutManager = LinearLayoutManager(this)
                    storyContainer.adapter = StoriesAdapter(stories, this, authData.userId)
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

    private fun getPreviousStory(authData: AuthData) {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE
        Thread {
            try {
                val story = URL(BuildConfig.BACKEND_URL + "/story").openConnection().apply {
                    addRequestProperty(
                        "Authorization", "Bearer ${authData.accessToken}"
                    )
                    addRequestProperty(
                        "game-id", game.id
                    )
                    connectTimeout = 10000
                }.getInputStream().use {
                    it.bufferedReader().use(BufferedReader::readText)
                }

                runOnUiThread {
                    findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
                    val previousTextView = findViewById<TextView>(R.id.previousText)
                    previousTextView.text = story
                    previousTextView.movementMethod = ScrollingMovementMethod.getInstance()
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

    private fun setTextListener() {
        val storyInput = findViewById<EditText>(R.id.story)
        storyInput.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {
                val words = s.toString().trim().split(" ")
                if (s != null && words.isNotEmpty()) {
                    val textLength = s.toString().trim().length
                    val highlightedWords = if (game.settings.wordsVisible == 0) words.size else min(game.settings.wordsVisible, words.size)
                    val highlightedWordsLength = words.subList(words.size - highlightedWords, words.size).joinToString(" ").length
                    val spannable = SpannableString(s.toString().trim())
                    if (game.settings.wordsVisible > 0) {
                        spannable.setSpan(
                            ForegroundColorSpan(Color.MAGENTA),
                            textLength - highlightedWordsLength,
                            textLength,
                            0
                        )
                    }
                    findViewById<EditText>(R.id.highlighted_story).setText(spannable)
                }
                findViewById<TextView>(R.id.words_left).text = "${words.size}/${game.settings.minWords}-${game.settings.maxWords}"
                if (words.size > game.settings.maxWords) {
                    findViewById<TextView>(R.id.words_left).setTextColor(Color.RED)
                } else {
                    findViewById<TextView>(R.id.words_left).setTextColor(Color.BLACK)
                }
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
            try {
                URL(BuildConfig.BACKEND_URL + "/game/${game.id}/start").openConnection()
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
                            game = game.copy(status = GameStatus.STARTED)
                            runOnUiThread {
                                findViewById<ProgressBar>(R.id.starting_progress).visibility =
                                    View.GONE
                                Toast.makeText(
                                    this,
                                    getString(R.string.story_started),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            finish()
                            overridePendingTransition(0, 0)
                            intent.putExtra("game", game)
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
                                val errorResponse =
                                    Gson().fromJson(response.toString(), HttpError::class.java)
                                val name = "error_${errorResponse.error}"
                                val id =
                                    resources.getIdentifier(name, "string", "fi.hell.storygame")
                                runOnUiThread {
                                    findViewById<ProgressBar>(R.id.starting_progress).visibility =
                                        View.GONE
                                    findViewById<Button>(R.id.start_button).visibility =
                                        View.VISIBLE
                                    Toast.makeText(this, resources.getString(id), Toast.LENGTH_LONG)
                                        .show()
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

    fun submitText(view: View) {
        val authData = AuthService.getAuthData(this) ?: return
        val story = findViewById<EditText>(R.id.story)
        val words = story.text.toString().trim().split(" ")
        if (words.size < game.settings.minWords) {
             story.error = getString(R.string.too_few_words, game.settings.minWords)
            return
        } else if (words.size > game.settings.maxWords) {
            story.error = getString(R.string.too_many_words, game.settings.maxWords)
            return
        }
        Thread {
            var success = false
            try {
                URL(BuildConfig.BACKEND_URL + "/story").openConnection()
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
                        connectTimeout = 10000
                        requestMethod = "POST"
                        doOutput = true
                        val outputWriter = OutputStreamWriter(outputStream)
                        val storyJson =
                            Gson().toJson(Story(story.text.trim().toString(), authData.userId))
                        outputWriter.write(storyJson.toString())
                        outputWriter.flush()
                    }.let {
                        if (it.responseCode == 201) {
                            success = true
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    getString(R.string.story_sent_successfully),
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
                                    story.error = resources.getString(id)
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

    private fun startWriting(authData: AuthData, storiesCount: Int) {
        setContentView(R.layout.write_story)
        getPreviousStory(authData)
        setTextListener()

        // If last part going on
        if (storiesCount + 1 >= game.settings.roundsPerUser * game.users.size) {
            findViewById<Button>(R.id.submit_story).text = getString(R.string.finalize_story)
            findViewById<EditText>(R.id.highlighted_story).hint = getString(R.string.finalize_story)
        }
    }

    private fun storyStarted(nextWriter: User, authData: AuthData) {
        if (nextWriter.id == authData.userId) {
            startWriting(authData, game.stories.size)
        } else {
            game.nextWriter = nextWriter
            waitForTurn()
        }
    }

    private fun storyFinished(authData: AuthData) {
        setContentView(R.layout.finished_story)
        getAllStories(authData)
    }

    private fun waitForTurn() {
        setContentView(R.layout.activity_wait_for_turn)
        findViewById<TextView>(R.id.wait_info).text =
            getString(R.string.wait_info, game.nextWriter.username)
    }

    private fun usersJoined(participants: Int) {
        findViewById<TextView>(R.id.participants).text = getString(R.string.participants_count, participants)
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null) return
            val authData = AuthService.getAuthData(context) ?: return
            when (intent?.getStringExtra("type")) {
                "NEXT_WRITER" -> startWriting(authData, game.stories.size + 1)
                "STORY_STARTED" -> storyStarted(intent.getParcelableExtra("nextWriter"), authData)
                "STORY_FINISHED" -> storyFinished(authData)
                "USER_JOINED" -> usersJoined(intent.getIntExtra("participants", 0))
            }
        }
    }
}