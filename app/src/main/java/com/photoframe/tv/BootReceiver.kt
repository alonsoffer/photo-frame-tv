package com.photoframe.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.photoframe.tv.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Relaunches the frame after the device boots, if the user enabled "start on power-on".
 * Some manufacturers send QUICKBOOT_POWERON instead of the standard BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, ACTION_QUICKBOOT_POWERON -> Unit
            else -> return
        }

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val enabled = SettingsRepository(appContext).settings.first().startOnBoot
                if (enabled) {
                    val launch = Intent(appContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(launch)
                }
            } catch (_: Throwable) {
                // Never crash the boot broadcast.
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
