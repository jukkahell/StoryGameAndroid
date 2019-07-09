package fi.hell.storygame

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import fi.hell.storygame.model.Game
import fi.hell.storygame.model.GameStatus
import kotlinx.android.synthetic.main.game_row.view.*

class GameListAdapter(private val items : List<Game>, private val context: Context, private val userId: String) : RecyclerView.Adapter<GamesViewHolder>() {

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GamesViewHolder {
        return GamesViewHolder(LayoutInflater.from(context).inflate(R.layout.game_row, parent, false))
    }

    override fun onBindViewHolder(holder: GamesViewHolder, position: Int) {
        holder.title.text = items[position].title
        holder.lang.setImageResource(context.resources.getIdentifier("flag_" + items[position].settings.locale.split("_")[1].toLowerCase(),"drawable","fi.hell.storygame"))
        val startedImage =
            when {
                items[position].status == GameStatus.CREATED -> android.R.drawable.presence_offline
                items[position].status == GameStatus.FINISHED -> android.R.drawable.presence_busy
                items[position].nextWriter.id == userId -> android.R.drawable.presence_online
                else -> android.R.drawable.presence_away
            }
        holder.started.setImageResource(startedImage)

        holder.itemView.setOnClickListener {
            val intent = Intent(context, GameActivity::class.java)
            intent.putExtra("game", items[position])
            context.startActivity(intent)
        }
    }
}

class GamesViewHolder (view: View) : RecyclerView.ViewHolder(view) {
    val title: TextView = view.title
    val lang: ImageView = view.lang
    val started: ImageView = view.started
    val nextWriter: TextView = view.next_writer
}