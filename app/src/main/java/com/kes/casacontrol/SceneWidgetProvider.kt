package com.kes.casacontrol

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class SceneWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val clientId = prefs.getString("clientId", "") ?: ""
                val secret = prefs.getString("clientSecret", "") ?: ""
                val url = prefs.getString("regionUrl", "") ?: ""
                if (clientId.isNotEmpty() && secret.isNotEmpty()) {
                    TuyaApiClient(context.applicationContext, clientId, secret, url).getValidToken()
                }
            } catch (e: Exception) {}
        }

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.removeAllViews(R.id.widget_list_container)

            val scenesList = mutableListOf<JSONObject>()
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

                    val widgetConfigStr = prefs.getString("widget_${appWidgetId}_scenes", null)
                    if (widgetConfigStr != null) {
                        val allowedSet = mutableSetOf<String>()
                        val allowedArr = JSONArray(widgetConfigStr)
                        for (i in 0 until allowedArr.length()) allowedSet.add(allowedArr.getString(i))
                        allNonHidden.filterTo(scenesList) { allowedSet.contains(it.optString("scene_id")) }
                    } else {
                        scenesList.addAll(allNonHidden)
                    }
                } catch (e: Exception) {}
            }

            if (scenesList.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty_view, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_empty_view, android.view.View.GONE)
                for (scene in scenesList) {
                    val sceneId = scene.optString("scene_id")
                    val defaultName = scene.optString("name", "Escena")
                    val customName = scene.optString("custom_name", "")
                    val emoji = scene.optString("emoji", "⚡")
                    val colorHex = scene.optString("color", "")
                    val nameToDisplay = if (customName.isNotEmpty()) customName else defaultName

                    val itemView = RemoteViews(context.packageName, R.layout.widget_list_item)
                    itemView.setTextViewText(R.id.widget_item_name, nameToDisplay)
                    itemView.setTextViewText(R.id.widget_item_emoji, emoji)
                    
                    if (colorHex.isNotEmpty()) {
                        try {
                            itemView.setInt(R.id.widget_item_container, "setBackgroundColor", Color.parseColor(colorHex))
                        } catch (e: Exception) {}
                    } else {
                        itemView.setInt(R.id.widget_item_container, "setBackgroundResource", R.drawable.widget_item_bg)
                    }

                    val clickIntent = Intent(context, ExecuteSceneReceiver::class.java).apply {
                        action = "com.kes.casacontrol.ACTION_EXECUTE_SCENE"
                        data = android.net.Uri.parse("casacontrol://scene/$sceneId")
                        putExtra("scene_id", sceneId)
                        putExtra("scene_name", nameToDisplay)
                    }
                    
                    val clickPendingIntent = PendingIntent.getBroadcast(
                        context,
                        sceneId.hashCode(),
                        clickIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    itemView.setOnClickPendingIntent(R.id.widget_item_container, clickPendingIntent)
                    views.addView(R.id.widget_list_container, itemView)
                }
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = context.getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove("widget_${appWidgetId}_scenes")
        }
        editor.apply()
    }
}
