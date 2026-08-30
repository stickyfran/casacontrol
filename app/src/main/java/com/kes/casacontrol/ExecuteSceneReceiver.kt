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
        // Instant Haptic Feedback on widget tap immediately
        triggerHapticFeedback(context)

        val sceneId = intent.getStringExtra("scene_id")
            ?: intent.data?.lastPathSegment
            ?: return

        val sceneName = intent.getStringExtra("scene_name") ?: "Escena"

        val prefs = context.getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
        val clientId = prefs.getString("clientId", "") ?: ""
        val secret = prefs.getString("clientSecret", "") ?: ""
        val url = prefs.getString("regionUrl", "") ?: ""
        val homeId = prefs.getString("homeId", "") ?: ""

        if (clientId.isEmpty() || homeId.isEmpty()) {
            Toast.makeText(context, "Abre la app y configúrala primero", Toast.LENGTH_SHORT).show()
            return
        }

        // goAsync prevents Android from killing the BroadcastReceiver process before coroutine finishes
        val pendingResult = goAsync()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val api = TuyaApiClient(context, clientId, secret, url)
                val success = api.triggerScene(homeId, sceneId)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, "¡$sceneName activada!", Toast.LENGTH_SHORT).show()
                    } else {
                        val err = if (api.lastError.isNotEmpty()) api.lastError else "Error al ejecutar"
                        Toast.makeText(context, "$sceneName: $err", Toast.LENGTH_SHORT).show()
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
                    val vibrator = vibratorManager?.defaultVibrator
                    if (vibrator != null && vibrator.hasVibrator()) {
                        val effect = VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val attrs = android.os.VibrationAttributes.Builder()
                                .setUsage(android.os.VibrationAttributes.USAGE_TOUCH)
                                .build()
                            vibrator.vibrate(effect, attrs)
                        } else {
                            vibrator.vibrate(effect)
                        }
                        return
                    }
                }
                
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(45)
                    }
                }
            } catch (e: Exception) {
                // Ignore vibration failure
            }
        }
    }
}