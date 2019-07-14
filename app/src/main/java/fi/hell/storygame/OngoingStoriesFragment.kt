package fi.hell.storygame

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.hell.storygame.model.AuthData
import fi.hell.storygame.model.Game
import fi.hell.storygame.service.NotificationService.Companion.NOTIFICATION_ACTION
import kotlinx.android.synthetic.main.fragment_ongoing_stories.*
import java.io.BufferedReader
import java.net.URL

class OngoingStoriesFragment: Fragment() {

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(sectionNumber: Int): OngoingStoriesFragment {
            return OngoingStoriesFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ongoing_stories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fab.setOnClickListener {
            val intent = Intent(context, CreateGameActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        val authData = AuthService.getAuthData(context!!) ?: return
        getGames(authData)

        activity?.let {
            LocalBroadcastManager.getInstance(it).registerReceiver(broadcastReceiver,
                IntentFilter(NOTIFICATION_ACTION)
            )
        }
    }

    override fun onPause() {
        super.onPause()
        activity?.let { LocalBroadcastManager.getInstance(it).unregisterReceiver(broadcastReceiver) }
    }

    private fun getGames(authData: AuthData) {
        val gameList = view!!.findViewById<RecyclerView>(R.id.gameList)
        Thread {
            try {
                val gamesJson = URL(BuildConfig.BACKEND_URL + "/games").openConnection().apply {
                    setRequestProperty(
                        "Authorization", "Bearer ${authData.accessToken}"
                    )
                    connectTimeout = 10000
                }.getInputStream().use {
                    it.bufferedReader().use(BufferedReader::readText)
                }

                val games =
                    Gson().fromJson<List<Game>>(gamesJson, object : TypeToken<List<Game>>() {}.type)
                val sortedGames = games
                    .sortedByDescending { it.nextWriter.id == authData.userId }
                    .sortedWith(compareBy { it.status.pos })
                activity!!.runOnUiThread {
                    gameList.layoutManager = LinearLayoutManager(context)
                    gameList.adapter = GameListAdapter(sortedGames, context!!, authData.userId)
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.unable_to_connect_backend),
                        Toast.LENGTH_LONG
                    ).show()
                }
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
