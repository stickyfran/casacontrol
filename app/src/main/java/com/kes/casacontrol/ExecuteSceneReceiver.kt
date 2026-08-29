package com.kes.casacontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExecuteSceneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sceneId = intent.getStringExtra("scene_id") ?: return
        val prefs = context.getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        val clientId = prefs.getString("clientId", "") ?: ""
        val secret = prefs.getString("clientSecret", "") ?: ""
        val url = prefs.getString("regionUrl", "") ?: ""
        val homeId = prefs.getString("homeId", "") ?: ""

        if (clientId.isEmpty() || homeId.isEmpty()) {
            Toast.makeText(context, "Abre la app y configúrala primero", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "Ejecutando...", Toast.LENGTH_SHORT).show()

        // goAsync prevents Android from killing the BroadcastReceiver process before coroutine finishes
        val pendingResult = goAsync()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val api = TuyaApiClient(context, clientId, secret, url)
                val success = api.triggerScene(homeId, sceneId)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, "Escena ejecutada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Error al ejecutar", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}