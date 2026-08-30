package com.kes.casacontrol

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class SceneAdapter(
    val scenes: MutableList<JSONObject>,
    private val onClick: (JSONObject) -> Unit,
    private val onLongClick: (Int, JSONObject) -> Unit
) : RecyclerView.Adapter<SceneAdapter.SceneViewHolder>() {

    class SceneViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
        val container: LinearLayout = view.findViewById(R.id.itemContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SceneViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scene_dashboard, parent, false)
        return SceneViewHolder(view)
    }

    override fun onBindViewHolder(holder: SceneViewHolder, position: Int) {
        val scene = scenes[position]
        val defaultName = scene.optString("name", "Escena")
        val customName = scene.optString("custom_name", "")
        val emoji = scene.optString("emoji", "⚡")
        val colorHex = scene.optString("color", "")

        holder.tvName.text = if (customName.isNotEmpty()) customName else defaultName
        holder.tvEmoji.text = emoji

        if (colorHex.isNotEmpty()) {
            try {
                holder.container.setBackgroundColor(Color.parseColor(colorHex))
                // Simple auto-contraste para texto (podria mejorarse pero funciona)
            } catch (e: Exception) {
                // Ignore invalid colors
            }
        } else {
            // Revertir a default
            holder.container.setBackgroundResource(android.R.color.transparent)
        }

        holder.container.setOnClickListener { onClick(scene) }
        holder.container.setOnLongClickListener { 
            onLongClick(position, scene)
            true
        }
    }

    override fun getItemCount() = scenes.size
}