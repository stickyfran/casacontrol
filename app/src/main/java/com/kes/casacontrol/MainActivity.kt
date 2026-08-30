
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

            // Los datos se guardan SIEMPRE, incluso si falla la conexion
            prefs.edit()
                .putString("clientId", clientId)
                .putString("clientSecret", clientSecret)
                .putString("regionUrl", regionUrl)
                .putString("uid", uid)
                .putString("access_token", "") // Forzar refresh token
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
                val scenesArray = JSONArray()
                scenes.forEach { scenesArray.put(it) }
                
                prefs.edit().putString("scenes", scenesArray.toString()).apply()
                
                // Logica de renovacion: guardar la primera vez que funciona
                val firstSuccess = prefs.getLong("first_success_time", 0L)
                if (firstSuccess == 0L) {
                    prefs.edit().putLong("first_success_time", System.currentTimeMillis()).apply()
                } else {
                    // Advertencia si han pasado más de 150 días (5 meses)
                    val daysElapsed = (System.currentTimeMillis() - firstSuccess) / (1000 * 60 * 60 * 24)
                    if (daysElapsed > 150) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "ATENCION: Tu prueba de Tuya caducará pronto (han pasado $daysElapsed dias). Renuevala en iot.tuya.com", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "¡Éxito! Se encontraron ${scenes.size} escenas. Datos guardados.", Toast.LENGTH_LONG).show()
                    val intent = Intent(this@MainActivity, SceneWidgetProvider::class.java)
                    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, SceneWidgetProvider::class.java))
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    sendBroadcast(intent)
                }
            } else {
                // Manejo detallado de errores
                val errorMsg = when (api.lastErrorCode) {
                    28841002 -> "Credenciales CORRECTAS, pero el servicio 'IoT Core' está caducado en iot.tuya.com. Renuevalo."
                    1106 -> "Permiso denegado. Renueva 'IoT Core' en Tuya."
                    1004 -> "Client Secret incorrecto."
                    1108 -> "Client ID incorrecto."
                    else -> {
                        if (api.lastError.contains("expire", true)) {
                            "El servicio 'IoT Core' parece estar caducado. Renuevalo en iot.tuya.com."
                        } else if (api.lastError.isNotEmpty()) {
                            "Error de Tuya: ${api.lastError} (Code: ${api.lastErrorCode})"
                        } else {
                            "Error de conexión. Revisa si el UID es correcto."
                        }
                    }
                }
                
                // Si esta caducado, borramos el first_success_time para que cuando lo arregle, vuelva a contar de 0
                if (api.lastErrorCode == 28841002 || api.lastErrorCode == 1106) {
                    prefs.edit().putLong("first_success_time", 0L).apply()
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "$errorMsg (Tus credenciales se han quedado guardadas)", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
