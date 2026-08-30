package com.kes.casacontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExecuteSceneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sceneId = intent.getStringExtra("scene_id") ?: return
        
        // Instant Haptic Feedback on widget tap
        triggerHapticFeedback(context)

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

    companion object {
        fun triggerHapticFeedback(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator?.vibrate(
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(35)
                }
            } catch (e: Exception) {
                // Ignore vibration failure on devices without vibrator motor
            }
        }
    }
}