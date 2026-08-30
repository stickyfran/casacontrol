package com.kes.casacontrol

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONArray
import org.json.JSONObject

class SceneRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
) : RemoteViewsService.RemoteViewsFactory {
    private val scenesList = mutableListOf<JSONObject>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        scenesList.clear()
        val prefs = context.getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        val scenesStr = prefs.getString("scenes", "") ?: ""
        if (scenesStr.isNotEmpty()) {
            try {
                val arr = JSONArray(scenesStr)
                val allNonHidden = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (!obj.optBoolean("is_hidden", false)) {
                        allNonHidden.add(obj)
                    }
                }

                val widgetConfigStr = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    prefs.getString("widget_${appWidgetId}_scenes", null)
                } else null

                if (widgetConfigStr != null) {
                    val allowedSet = mutableSetOf<String>()
                    val allowedArr = JSONArray(widgetConfigStr)
                    for (i in 0 until allowedArr.length()) {
                        allowedSet.add(allowedArr.getString(i))
                    }
                    allNonHidden.filterTo(scenesList) { allowedSet.contains(it.optString("scene_id")) }
                } else {
                    scenesList.addAll(allNonHidden)
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

        val sceneId = scene.optString("scene_id")
        val fillInIntent = Intent().apply {
            action = "com.kes.casacontrol.ACTION_EXECUTE_SCENE"
            data = android.net.Uri.parse("casacontrol://scene/$sceneId")
            putExtra("scene_id", sceneId)
            putExtra("scene_name", nameToDisplay)
        }
        rv.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long {
        return if (position in 0 until scenesList.size) {
            scenesList[position].optString("scene_id").hashCode().toLong()
        } else {
            position.toLong()
        }
    }
    override fun hasStableIds(): Boolean = true
}