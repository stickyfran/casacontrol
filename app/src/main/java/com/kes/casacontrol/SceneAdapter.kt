package com.kes.casacontrol

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class SceneAdapter(
    val scenes: MutableList<JSONObject>,
    private val onClick: (JSONObject) -> Unit,
    private val onEditClick: (Int, JSONObject) -> Unit
) : RecyclerView.Adapter<SceneAdapter.SceneViewHolder>() {

    var isEditMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class SceneViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvOriginalName: TextView = view.findViewById(R.id.tvOriginalName)
        val tvHiddenBadge: TextView = view.findViewById(R.id.tvHiddenBadge)
        val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
        val container: LinearLayout = view.findViewById(R.id.itemContainer)
        val btnEditScene: ImageButton = view.findViewById(R.id.btnEditScene)
        val ivDragHandle: ImageView = view.findViewById(R.id.ivDragHandle)
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
        val isHidden = scene.optBoolean("is_hidden", false)

        val displayName = if (customName.isNotEmpty()) customName else defaultName
        holder.tvName.text = displayName
        holder.tvEmoji.text = emoji

        if (customName.isNotEmpty() && customName != defaultName) {
            holder.tvOriginalName.text = "Original: $defaultName"
            holder.tvOriginalName.visibility = View.VISIBLE
        } else {
            holder.tvOriginalName.visibility = View.GONE
        }

        // Hidden badge
        if (isHidden) {
            holder.tvHiddenBadge.visibility = View.VISIBLE
            holder.container.alpha = if (isEditMode) 0.65f else 0.5f
        } else {
            holder.tvHiddenBadge.visibility = View.GONE
            holder.container.alpha = 1.0f
        }

        // Color handling
        if (colorHex.isNotEmpty()) {
            try {
                holder.container.setBackgroundColor(Color.parseColor(colorHex))
                holder.tvName.setTextColor(Color.WHITE)
                holder.tvOriginalName.setTextColor(Color.parseColor("#DDFFFFFF"))
            } catch (e: Exception) {
                holder.container.setBackgroundResource(R.drawable.widget_item_bg)
            }
        } else {
            holder.container.setBackgroundResource(R.drawable.widget_item_bg)
        }

        // Edit mode visuals
        if (isEditMode) {
            holder.btnEditScene.visibility = View.VISIBLE
            holder.ivDragHandle.alpha = 1.0f
            holder.container.setOnClickListener { onEditClick(position, scene) }
        } else {
            holder.btnEditScene.visibility = View.GONE
            holder.ivDragHandle.alpha = 0.3f
            holder.container.setOnClickListener { onClick(scene) }
        }

        holder.btnEditScene.setOnClickListener { onEditClick(position, scene) }
        holder.container.setOnLongClickListener {
            onEditClick(position, scene)
            true
        }
    }

    override fun getItemCount() = scenes.size
}