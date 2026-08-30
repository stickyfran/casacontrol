package com.kes.casacontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "casacontrol:executescene")
        try {
            // Keep CPU awake up to 20 seconds to allow token refresh + network execution even with screen off
            wakeLock?.acquire(20_000L)
        } catch (e: Exception) {}

        val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        receiverScope.launch {
            try {
                // Android limits BroadcastReceivers to 10 seconds. If we exceed this, the system
                // throws a silent ANR and puts the app in a zombie state until force closed.
                // We wrap the network call in an 8.5s timeout to guarantee pendingResult.finish() runs.
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val activeNetwork = cm?.activeNetworkInfo
                val isConnected = activeNetwork?.isConnected == true

                val result = if (!isConnected) {
                    Pair(false, "Sin conexión a internet")
                } else {
                    // Reduce timeout to 4.5s to free the widget touch queue faster
                    kotlinx.coroutines.withTimeoutOrNull(4500L) {
                        val api = TuyaApiClient(context, clientId, secret, url)
                        val success = api.triggerScene(homeId, sceneId)
                        Pair(success, api.lastError)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (result == null) {
                        Toast.makeText(context, "$sceneName: Red inestable, reintentando conexiones. Toca de nuevo.", Toast.LENGTH_LONG).show()
                    } else if (result.first) {
                        Toast.makeText(context, "¡$sceneName activada!", Toast.LENGTH_SHORT).show()
                    } else {
                        val err = if (result.second.isNotEmpty()) result.second else "Error al ejecutar"
                        Toast.makeText(context, "$sceneName: $err", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (e: Exception) {}
                try {
                    pendingResult.finish()
                } catch (e: Exception) {}
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
                        val effect = try {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        } catch (e: Exception) {
                            VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // USAGE_NOTIFICATION has higher system priority than USAGE_TOUCH
                            // preventing aggressive OEM battery savers from suppressing vibration when screen is off
                            val attrs = android.os.VibrationAttributes.Builder()
                                .setUsage(android.os.VibrationAttributes.USAGE_NOTIFICATION)
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val effect = try {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        } catch (e: Exception) {
                            VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                        vibrator.vibrate(effect)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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