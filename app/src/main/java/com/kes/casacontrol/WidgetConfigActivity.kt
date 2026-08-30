package com.kes.casacontrol

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import org.json.JSONArray
import org.json.JSONObject

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val allScenes = mutableListOf<JSONObject>()
    private val selectedSceneIds = mutableSetOf<String>()
    private lateinit var adapter: WidgetConfigAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.activity_widget_config)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val rvWidgetScenes = findViewById<RecyclerView>(R.id.rvWidgetScenes)
        val btnSelectAll = findViewById<Button>(R.id.btnSelectAll)
        val btnDeselectAll = findViewById<Button>(R.id.btnDeselectAll)
        val btnSaveWidgetConfig = findViewById<Button>(R.id.btnSaveWidgetConfig)

        loadScenes()

        adapter = WidgetConfigAdapter(allScenes, selectedSceneIds) {
            // Check state changed
        }
        rvWidgetScenes.layoutManager = LinearLayoutManager(this)
        rvWidgetScenes.adapter = adapter

        btnSelectAll.setOnClickListener {
            selectedSceneIds.clear()
            allScenes.forEach { scene ->
                val id = scene.optString("scene_id")
                if (id.isNotEmpty()) selectedSceneIds.add(id)
            }
            adapter.notifyDataSetChanged()
        }

        btnDeselectAll.setOnClickListener {
            selectedSceneIds.clear()
            adapter.notifyDataSetChanged()
        }

        btnSaveWidgetConfig.setOnClickListener {
            saveWidgetConfig()
        }
    }

    private fun loadScenes() {
        val prefs = getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        val scenesStr = prefs.getString("scenes", "[]") ?: "[]"
        try {
            val arr = JSONArray(scenesStr)
            allScenes.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (!obj.optBoolean("is_hidden", false)) {
                    allScenes.add(obj)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val savedSelectionStr = prefs.getString("widget_${appWidgetId}_scenes", null)
        selectedSceneIds.clear()
        if (savedSelectionStr != null) {
            try {
                val arr = JSONArray(savedSelectionStr)
                for (i in 0 until arr.length()) {
                    selectedSceneIds.add(arr.getString(i))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // By default, select all non-hidden scenes
            allScenes.forEach { scene ->
                val id = scene.optString("scene_id")
                if (id.isNotEmpty()) selectedSceneIds.add(id)
            }
        }
    }

    private fun saveWidgetConfig() {
        val prefs = getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray()
        selectedSceneIds.forEach { arr.put(it) }
        prefs.edit().putString("widget_${appWidgetId}_scenes", arr.toString()).apply()

        // Notify widget update
        val appWidgetManager = AppWidgetManager.getInstance(this)
        
        // Broadcast update for list widget
        val intentList = Intent(this, SceneWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        sendBroadcast(intentList)

        // Broadcast update for grid widget
        val intentGrid = Intent(this, SceneWidgetProviderGrid::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        sendBroadcast(intentGrid)

        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_grid_view)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)
        Toast.makeText(this, "Widget configurado con ${selectedSceneIds.size} escenas", Toast.LENGTH_SHORT).show()
        finish()
    }

    class WidgetConfigAdapter(
        private val scenes: List<JSONObject>,
        private val selectedIds: MutableSet<String>,
        private val onSelectionChanged: () -> Unit
    ) : RecyclerView.Adapter<WidgetConfigAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvOriginalName: TextView = view.findViewById(R.id.tvOriginalName)
            val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
            val cbSelected: MaterialCheckBox = view.findViewById(R.id.cbSelected)
            val container: LinearLayout = view.findViewById(R.id.itemContainer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_widget_config_scene, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val scene = scenes[position]
            val sceneId = scene.optString("scene_id")
            val defaultName = scene.optString("name", "Escena")
            val customName = scene.optString("custom_name", "")
            val emoji = scene.optString("emoji", "⚡")
            val colorHex = scene.optString("color", "")

            val displayName = if (customName.isNotEmpty()) customName else defaultName
            holder.tvName.text = displayName
            holder.tvEmoji.text = emoji

            if (customName.isNotEmpty() && customName != defaultName) {
                holder.tvOriginalName.text = "Original: $defaultName"
                holder.tvOriginalName.visibility = View.VISIBLE
            } else {
                holder.tvOriginalName.visibility = View.GONE
            }

            val isChecked = selectedIds.contains(sceneId)
            holder.cbSelected.isChecked = isChecked

            holder.container.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION && pos >= 0 && pos < scenes.size) {
                    val id = scenes[pos].optString("scene_id")
                    if (selectedIds.contains(id)) {
                        selectedIds.remove(id)
                        holder.cbSelected.isChecked = false
                    } else {
                        selectedIds.add(id)
                        holder.cbSelected.isChecked = true
                    }
                    onSelectionChanged()
                }
            }
        }

        override fun getItemCount(): Int = scenes.size
    }
}
