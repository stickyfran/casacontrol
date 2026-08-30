package com.kes.casacontrol

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONArray
import org.json.JSONObject

class SceneRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private val scenesList = mutableListOf<JSONObject>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        scenesList.clear()
        val prefs = context.getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        val scenesStr = prefs.getString("scenes", "") ?: ""
        if (scenesStr.isNotEmpty()) {
            try {
                val arr = JSONArray(scenesStr)
                for (i in 0 until arr.length()) {
                    scenesList.add(arr.getJSONObject(i))
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onDestroy() { scenesList.clear() }

    override fun getCount(): Int = scenesList.size

    override fun getViewAt(position: Int): RemoteViews {
        val scene = scenesList[position]
        val defaultName = scene.optString("name", "Escena")
        val customName = scene.optString("custom_name", "")
        val emoji = scene.optString("emoji", "⚡")
        val colorHex = scene.optString("color", "")
        
        val nameToDisplay = if (customName.isNotEmpty()) customName else defaultName

        val rv = RemoteViews(context.packageName, R.layout.widget_list_item)
        rv.setTextViewText(R.id.widget_item_name, nameToDisplay)
        rv.setTextViewText(R.id.widget_item_emoji, emoji)
        
        if (colorHex.isNotEmpty()) {
            try {
                val colorInt = Color.parseColor(colorHex)
                rv.setInt(R.id.widget_item_container, "setBackgroundColor", colorInt)
            } catch (e: Exception) {}
        } else {
            rv.setInt(R.id.widget_item_container, "setBackgroundResource", R.drawable.widget_item_bg)
        }

        val fillInIntent = Intent().apply {
            putExtra("scene_id", scene.optString("scene_id"))
        }
        rv.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}