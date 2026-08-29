package com.kes.casacontrol

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONArray
import org.json.JSONObject

class SceneWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return SceneRemoteViewsFactory(this.applicationContext)
    }
}

class SceneRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private val scenes = mutableListOf<JSONObject>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        scenes.clear()
        val prefs = context.getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        val scenesStr = prefs.getString("scenes", "[]")
        try {
            val arr = JSONArray(scenesStr)
            for (i in 0 until arr.length()) {
                scenes.add(arr.getJSONObject(i))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDestroy() { scenes.clear() }
    override fun getCount(): Int = scenes.size

    override fun getViewAt(position: Int): RemoteViews {
        val scene = scenes[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_list_item)
        rv.setTextViewText(R.id.tvSceneName, scene.optString("name", "Escena"))

        val fillInIntent = Intent().apply {
            putExtra("scene_id", scene.optString("scene_id"))
        }
        rv.setOnClickFillInIntent(R.id.llSceneItem, fillInIntent)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}\n