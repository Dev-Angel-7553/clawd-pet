package com.clawd.pet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val prefs = Prefs(context)
        if (!prefs.enabled || !prefs.autostart) return
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return
        PetOverlayService.start(context)
    }
}
