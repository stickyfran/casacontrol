package com.kes.casacontrol

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
                // Force token refresh on config change
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
            if (homes.isNotEmpty()) {
                val homeId = homes[0].getString("home_id")
                getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
                    .edit().putString("homeId", homeId).apply()
                    
                val scenes = api.getScenes(homeId)
                val scenesArray = JSONArray()
                scenes.forEach { scenesArray.put(it) }
                
                getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
                    .edit().putString("scenes", scenesArray.toString()).apply()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Exito! Se encontraron ${scenes.size} escenas.", Toast.LENGTH_LONG).show()
                    // Update widgets
                    val intent = Intent(this@MainActivity, SceneWidgetProvider::class.java)
                    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, SceneWidgetProvider::class.java))
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    sendBroadcast(intent)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error, revisa tus claves o tu UID", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}