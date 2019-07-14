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
import kotlinx.android.synthetic.main.game_row.view.*

class FinishedGameListAdapter(private val items : List<Game>, private val context: Context) : RecyclerView.Adapter<FinishedGamesViewHolder>() {

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FinishedGamesViewHolder {
        return FinishedGamesViewHolder(LayoutInflater.from(context).inflate(R.layout.finished_game_row, parent, false))
    }

    override fun onBindViewHolder(holder: FinishedGamesViewHolder, position: Int) {
        holder.title.text = items[position].title
        holder.lang.setImageResource(context.resources.getIdentifier("flag_" + items[position].settings.locale.split("_")[1].toLowerCase(),"drawable","fi.hell.storygame"))
        holder.itemView.setOnClickListener {
            val intent = Intent(context, GameActivity::class.java)
            intent.putExtra("game", items[position])
            context.startActivity(intent)
        }
    }
}

class FinishedGamesViewHolder (view: View) : RecyclerView.ViewHolder(view) {
    val title: TextView = view.title
    val lang: ImageView = view.lang
}