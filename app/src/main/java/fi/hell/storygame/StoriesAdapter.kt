package fi.hell.storygame

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import fi.hell.storygame.model.Story
import kotlinx.android.synthetic.main.story_row.view.*

class StoriesAdapter(private val items : List<Story>, private val context: Context, private val userId: String) : RecyclerView.Adapter<StoryViewHolder>() {

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
        return StoryViewHolder(LayoutInflater.from(context).inflate(R.layout.story_row, parent, false))
    }

    override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
        holder.story.text = items[position].text
    }
}

class StoryViewHolder (view: View) : RecyclerView.ViewHolder(view) {
    val story: TextView = view.story
}