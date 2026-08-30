package com.kes.casacontrol

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
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
    private lateinit var btnToggleEditMode: MaterialButton
    private lateinit var tvModeBanner: TextView
    private val scenesList = mutableListOf<JSONObject>()

    private val presetEmojis = listOf(
        "⚡", "💡", "🛋️", "🌙", "☀️", "🚪", "📺", "❄️", 
        "🔥", "☕", "🎵", "🔒", "🍳", "🏠", "🌿", "🎮", "🚿", "🧹", "🎬", "🛏️"
    )

    private val presetColors = listOf(
        Pair("Azul", "#2563EB"),
        Pair("Cian", "#0891B2"),
        Pair("Verde", "#16A34A"),
        Pair("Morado", "#9333EA"),
        Pair("Rosa", "#DB2777"),
        Pair("Naranja", "#EA580C"),
        Pair("Ámbar", "#D97706"),
        Pair("Rojo", "#DC2626"),
        Pair("Grafito", "#334155"),
        Pair("Predeterminado", "")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layoutAuth = findViewById(R.id.layoutAuth)
        layoutDashboard = findViewById(R.id.layoutDashboard)
        rvScenes = findViewById(R.id.rvScenes)
        btnToggleEditMode = findViewById(R.id.btnToggleEditMode)
        tvModeBanner = findViewById(R.id.tvModeBanner)
        
        val btnLogout = findViewById<ImageButton>(R.id.btnLogout)
        btnLogout.setOnClickListener { showAuthForm() }

        val btnRefresh = findViewById<ImageButton>(R.id.btnRefreshScenes)
        btnRefresh.setOnClickListener { refreshScenesFromTuya() }

        btnToggleEditMode.setOnClickListener {
            toggleEditMode()
        }

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

    private fun toggleEditMode() {
        val newMode = !sceneAdapter.isEditMode
        sceneAdapter.isEditMode = newMode
        if (newMode) {
            btnToggleEditMode.text = "✅ Listo"
            tvModeBanner.text = "✏️ Modo Edición activo: Toca cualquier escena para personalizarla o arrastra para ordenar"
            tvModeBanner.setTextColor(Color.parseColor("#F59E0B"))
        } else {
            btnToggleEditMode.text = "✏️ Editar"
            tvModeBanner.text = "💡 Modo Normal: Toca una escena para activarla"
            tvModeBanner.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun showAuthForm() {
        layoutAuth.visibility = View.VISIBLE
        layoutDashboard.visibility = View.GONE
    }

    private fun showDashboard() {
        layoutAuth.visibility = View.GONE
        layoutDashboard.visibility = View.VISIBLE
        
        sceneAdapter = SceneAdapter(
            scenesList, 
            onClick = { scene -> executeScene(scene) },
            onEditClick = { pos, scene -> showEditDialog(pos, scene) }
        )
        rvScenes.layoutManager = LinearLayoutManager(this)
        rvScenes.adapter = sceneAdapter
        
        // Drag and Drop support
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
        val sceneName = scene.optString("custom_name", scene.optString("name", "Escena"))
        Toast.makeText(this, "Ejecutando $sceneName...", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@MainActivity, "¡$sceneName activada!", Toast.LENGTH_SHORT).show()
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
        
        val tvPreviewName = dialogView.findViewById<TextView>(R.id.tvPreviewName)
        val tvPreviewEmoji = dialogView.findViewById<TextView>(R.id.tvPreviewEmoji)
        val previewContainer = dialogView.findViewById<LinearLayout>(R.id.previewContainer)
        val layoutEmojiPalette = dialogView.findViewById<LinearLayout>(R.id.layoutEmojiPalette)
        val layoutColorPalette = dialogView.findViewById<LinearLayout>(R.id.layoutColorPalette)

        val defaultName = scene.optString("name", "Escena")
        val currentCustomName = scene.optString("custom_name", defaultName)
        val currentEmoji = scene.optString("emoji", "⚡")
        val currentColor = scene.optString("color", "")

        etCustomName.setText(currentCustomName)
        etEmoji.setText(currentEmoji)
        etColor.setText(currentColor)

        fun updatePreview() {
            val nameText = etCustomName.text.toString().trim()
            tvPreviewName.text = if (nameText.isNotEmpty()) nameText else defaultName
            val emojiText = etEmoji.text.toString().trim()
            tvPreviewEmoji.text = if (emojiText.isNotEmpty()) emojiText else "⚡"

            val col = etColor.text.toString().trim()
            if (col.isNotEmpty()) {
                try {
                    val colorInt = Color.parseColor(if (col.startsWith("#")) col else "#$col")
                    previewContainer.setBackgroundColor(colorInt)
                } catch (e: Exception) {
                    previewContainer.setBackgroundResource(R.drawable.widget_item_bg)
                }
            } else {
                previewContainer.setBackgroundResource(R.drawable.widget_item_bg)
            }
        }

        updatePreview()

        // Text change listeners for real-time preview
        etCustomName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        })
        etEmoji.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        })
        etColor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Build Emoji Quick Palette
        for (emoji in presetEmojis) {
            val emojiBtn = TextView(this).apply {
                text = emoji
                textSize = 22f
                setPadding(16, 8, 16, 8)
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    etEmoji.setText(emoji)
                }
            }
            layoutEmojiPalette.addView(emojiBtn)
        }

        // Build Color Quick Palette
        for ((name, hex) in presetColors) {
            val colorBtn = Button(this).apply {
                text = if (hex.isEmpty()) "Default" else ""
                layoutParams = LinearLayout.LayoutParams(90, 90).apply {
                    setMargins(8, 4, 8, 4)
                }
                if (hex.isNotEmpty()) {
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor(hex))
                    }
                    background = shape
                } else {
                    setBackgroundResource(R.drawable.widget_item_bg)
                }
                setOnClickListener {
                    etColor.setText(hex)
                }
            }
            layoutColorPalette.addView(colorBtn)
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val finalName = etCustomName.text.toString().trim()
                val finalEmoji = etEmoji.text.toString().trim()
                val finalColor = etColor.text.toString().trim()

                scene.put("custom_name", finalName)
                scene.put("emoji", if (finalEmoji.isNotEmpty()) finalEmoji else "⚡")
                val formattedColor = if (finalColor.isNotEmpty()) {
                    if (finalColor.startsWith("#")) finalColor else "#$finalColor"
                } else ""
                scene.put("color", formattedColor)
                
                sceneAdapter.notifyItemChanged(position)
                saveScenesToPrefs()
                Toast.makeText(this, "Escena guardada", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Restablecer") { _, _ ->
                scene.remove("custom_name")
                scene.remove("emoji")
                scene.remove("color")
                sceneAdapter.notifyItemChanged(position)
                saveScenesToPrefs()
                Toast.makeText(this, "Escena restablecida a valores por defecto", Toast.LENGTH_SHORT).show()
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
        var intent = Intent(this, SceneWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(
                ComponentName(application, SceneWidgetProvider::class.java)
            )
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)

        intent = Intent(this, SceneWidgetProviderGrid::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(
                ComponentName(application, SceneWidgetProviderGrid::class.java)
            )
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)
    }

    private fun refreshScenesFromTuya() {
        val prefs = getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        val clientId = prefs.getString("clientId", "") ?: ""
        val secret = prefs.getString("clientSecret", "") ?: ""
        val url = prefs.getString("regionUrl", "") ?: ""
        val uid = prefs.getString("uid", "") ?: ""

        if (clientId.isNotEmpty() && secret.isNotEmpty() && uid.isNotEmpty()) {
            Toast.makeText(this, "Sincronizando con Tuya...", Toast.LENGTH_SHORT).show()
            fetchScenes(clientId, secret, url, uid)
        } else {
            showAuthForm()
        }
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