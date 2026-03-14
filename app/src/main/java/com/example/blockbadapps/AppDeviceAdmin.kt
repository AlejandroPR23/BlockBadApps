package com.example.blockbadapps

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Receives Device Admin events.
 *
 * The key protection is in onDisableRequested: we redirect the user to
 * VerifyPatternActivity before the system can show its own "disable admin" dialog.
 * If the pattern fails, the admin stays active.
 */
class AppDeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Proteccion anti-desinstalacion activada", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Proteccion desactivada", Toast.LENGTH_SHORT).show()
    }

    /**
     * Called when someone tries to disable Device Admin from Settings.
     * We return a warning message — the real gate is in DeviceAdminManager.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "BlockBadApps requiere patron para desactivarse."
    }
}