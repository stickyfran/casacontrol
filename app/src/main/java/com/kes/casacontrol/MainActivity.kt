package com.kes.casacontrol

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var layoutAuth: ScrollView
    private lateinit var layoutDashboard: View
    private lateinit var rvScenes: RecyclerView
    private lateinit var sceneAdapter: SceneAdapter
    private val scenesList = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layoutAuth = findViewById(R.id.layoutAuth)
        layoutDashboard = findViewById(R.id.layoutDashboard)
        rvScenes = findViewById(R.id.rvScenes)
        
        val btnLogout = findViewById<ImageButton>(R.id.btnLogout)
        btnLogout.setOnClickListener { showAuthForm() }

        setupAuthForm()
        
        val prefs = getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        val scenesStr = prefs.getString("scenes", "") ?: ""
        
        if (scenesStr.isNotEmpty() && prefs.getString("clientId", "")?.isNotEmpty() == true) {
            loadScenesFromPrefs(scenesStr)
            showDashboard()
        } else {
            showAuthForm()
        }
    }

    private fun showAuthForm() {
        layoutAuth.visibility = View.VISIBLE
        layoutDashboard.visibility = View.GONE
    }

    private fun showDashboard() {
        layoutAuth.visibility = View.GONE
        layoutDashboard.visibility = View.VISIBLE
        
        sceneAdapter = SceneAdapter(scenesList, 
            onClick = { scene -> executeScene(scene) },
            onLongClick = { pos, scene -> showEditDialog(pos, scene) }
        )
        rvScenes.layoutManager = LinearLayoutManager(this)
        rvScenes.adapter = sceneAdapter
        
        // Setup Drag and Drop
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                Collections.swap(scenesList, fromPos, toPos)
                sceneAdapter.notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                saveScenesToPrefs()
            }
        })
        itemTouchHelper.attachToRecyclerView(rvScenes)
    }

    private fun executeScene(scene: JSONObject) {
        Toast.makeText(this, "Ejecutando...", Toast.LENGTH_SHORT).show()
        val prefs = getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        val clientId = prefs.getString("clientId", "") ?: ""
        val secret = prefs.getString("clientSecret", "") ?: ""
        val url = prefs.getString("regionUrl", "") ?: ""
        val homeId = prefs.getString("homeId", "") ?: ""
        val sceneId = scene.getString("scene_id")

        GlobalScope.launch(Dispatchers.IO) {
            val api = TuyaApiClient(applicationContext, clientId, secret, url)
            val success = api.triggerScene(homeId, sceneId)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@MainActivity, "Escena activada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Error: ${api.lastError}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showEditDialog(position: Int, scene: JSONObject) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_scene, null)
        val etCustomName = dialogView.findViewById<EditText>(R.id.etCustomName)
        val etEmoji = dialogView.findViewById<EditText>(R.id.etEmoji)
        val etColor = dialogView.findViewById<EditText>(R.id.etColor)

        etCustomName.setText(scene.optString("custom_name", scene.optString("name", "")))
        etEmoji.setText(scene.optString("emoji", "⚡"))
        etColor.setText(scene.optString("color", ""))

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                scene.put("custom_name", etCustomName.text.toString().trim())
                scene.put("emoji", etEmoji.text.toString().trim())
                val col = etColor.text.toString().trim()
                scene.put("color", if (col.startsWith("#")) col else if (col.isNotEmpty()) "#$col" else "")
                
                sceneAdapter.notifyItemChanged(position)
                saveScenesToPrefs()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun loadScenesFromPrefs(jsonStr: String) {
        try {
            val arr = JSONArray(jsonStr)
            scenesList.clear()
            for (i in 0 until arr.length()) {
                scenesList.add(arr.getJSONObject(i))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveScenesToPrefs() {
        val arr = JSONArray()
        scenesList.forEach { arr.put(it) }
        val prefs = getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("scenes", arr.toString()).apply()
        updateAllWidgets()
    }

    private fun updateAllWidgets() {
        // Update List widgets
        var intent = Intent(this, SceneWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        var ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, SceneWidgetProvider::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)

        // Update Grid widgets
        intent = Intent(this, SceneWidgetProviderGrid::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, SceneWidgetProviderGrid::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    private fun setupAuthForm() {
        val prefs = getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        val etClientId = findViewById<EditText>(R.id.etClientId)
        val etClientSecret = findViewById<EditText>(R.id.etClientSecret)
        val etRegionUrl = findViewById<EditText>(R.id.etRegionUrl)
        val etUid = findViewById<EditText>(R.id.etUid)
        val btnSave = findViewById<Button>(R.id.btnSaveAndFetch)

        etClientId.setText(prefs.getString("clientId", ""))
        etClientSecret.setText(prefs.getString("clientSecret", ""))
        etRegionUrl.setText(prefs.getString("regionUrl", "https://openapi.tuyaus.com"))
        etUid.setText(prefs.getString("uid", ""))

        btnSave.setOnClickListener {
            val clientId = etClientId.text.toString().trim()
            val clientSecret = etClientSecret.text.toString().trim()
            val regionUrl = etRegionUrl.text.toString().trim()
            val uid = etUid.text.toString().trim()

            if (clientId.isEmpty() || clientSecret.isEmpty() || uid.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("clientId", clientId)
                .putString("clientSecret", clientSecret)
                .putString("regionUrl", regionUrl)
                .putString("uid", uid)
                .putString("access_token", "")
                .apply()

            Toast.makeText(this, "Conectando con Tuya...", Toast.LENGTH_SHORT).show()
            fetchScenes(clientId, clientSecret, regionUrl, uid)
        }
    }

    private fun fetchScenes(clientId: String, secret: String, url: String, uid: String) {
        GlobalScope.launch(Dispatchers.IO) {
            val api = TuyaApiClient(applicationContext, clientId, secret, url)
            val homes = api.getHomes(uid)
            val prefs = getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
            
            if (homes.isNotEmpty()) {
                val homeId = homes[0].getString("home_id")
                prefs.edit().putString("homeId", homeId).apply()
                    
                val scenes = api.getScenes(homeId)
                
                // Preserve custom properties if scenes already existed
                val oldScenesStr = prefs.getString("scenes", "[]") ?: "[]"
                val oldArr = JSONArray(oldScenesStr)
                val oldMap = HashMap<String, JSONObject>()
                for (i in 0 until oldArr.length()) {
                    val obj = oldArr.getJSONObject(i)
                    oldMap[obj.getString("scene_id")] = obj
                }

                val scenesArray = JSONArray()
                scenes.forEach { newScene ->
                    val sid = newScene.getString("scene_id")
                    if (oldMap.containsKey(sid)) {
                        val oldScene = oldMap[sid]!!
                        if (oldScene.has("custom_name")) newScene.put("custom_name", oldScene.getString("custom_name"))
                        if (oldScene.has("emoji")) newScene.put("emoji", oldScene.getString("emoji"))
                        if (oldScene.has("color")) newScene.put("color", oldScene.getString("color"))
                    }
                    scenesArray.put(newScene)
                }
                
                prefs.edit().putString("scenes", scenesArray.toString()).apply()
                
                val firstSuccess = prefs.getLong("first_success_time", 0L)
                if (firstSuccess == 0L) {
                    prefs.edit().putLong("first_success_time", System.currentTimeMillis()).apply()
                } else {
                    val daysElapsed = (System.currentTimeMillis() - firstSuccess) / (1000 * 60 * 60 * 24)
                    if (daysElapsed > 150) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "ATENCION: Tu prueba de Tuya caducará pronto. Renuevala en iot.tuya.com", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "¡Éxito! Se encontraron ${scenes.size} escenas.", Toast.LENGTH_LONG).show()
                    loadScenesFromPrefs(scenesArray.toString())
                    showDashboard()
                    updateAllWidgets()
                }
            } else {
                val errorMsg = when (api.lastErrorCode) {
                    28841002 -> "Credenciales CORRECTAS, pero el servicio 'IoT Core' está caducado en iot.tuya.com. Renuevalo."
                    1106 -> "Permiso denegado. Renueva 'IoT Core' en Tuya."
                    1004 -> "Client Secret incorrecto."
                    1108 -> "Client ID incorrecto."
                    else -> if (api.lastError.contains("expire", true)) "El servicio 'IoT Core' parece estar caducado. Renuevalo." else "Error de Tuya: ${api.lastError}"
                }
                if (api.lastErrorCode == 28841002 || api.lastErrorCode == 1106) {
                    prefs.edit().putLong("first_success_time", 0L).apply()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "$errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}