package fi.hell.storygame

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.hell.storygame.model.AuthData
import fi.hell.storygame.model.Game
import kotlinx.android.synthetic.main.fragment_ongoing_stories.*
import java.io.BufferedReader
import java.net.URL

class FinishedStoriesFragment: Fragment() {

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
        fun newInstance(sectionNumber: Int): FinishedStoriesFragment {
            return FinishedStoriesFragment().apply {
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
        return inflater.inflate(R.layout.fragment_finished_stories, container, false)
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
    }

    private fun getGames(authData: AuthData) {
        val gameList = view!!.findViewById<RecyclerView>(R.id.gameList)
        Thread {
            try {
                val gamesJson =
                    URL(BuildConfig.BACKEND_URL + "/finishedGames").openConnection().apply {
                        setRequestProperty(
                            "Authorization", "Bearer ${authData.accessToken}"
                        )
                        connectTimeout = 10000
                    }.getInputStream().use {
                        it.bufferedReader().use(BufferedReader::readText)
                    }

                val games =
                    Gson().fromJson<List<Game>>(gamesJson, object : TypeToken<List<Game>>() {}.type)
                activity!!.runOnUiThread {
                    gameList.layoutManager = LinearLayoutManager(context)
                    gameList.adapter = FinishedGameListAdapter(games, context!!)
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
}