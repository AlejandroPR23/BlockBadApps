package com.example.blockbadapps

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Centralizes all Device Admin logic so MainActivity stays clean.
 */
class DeviceAdminManager(private val context: Context) {

    val adminComponent = ComponentName(context, AppDeviceAdmin::class.java)

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    /** True if this app is currently an active Device Administrator. */
    fun isAdminActive(): Boolean = dpm.isAdminActive(adminComponent)

    /**
     * Returns an Intent that launches the system "Activate device admin" screen.
     * Start this with startActivityForResult — result is RESULT_OK if the user accepted.
     */
    fun buildActivationIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Necesario para evitar que la app sea desinstalada sin patron de desbloqueo."
            )
        }

    /**
     * Removes Device Admin rights.
     * Only call this AFTER verifying the unlock pattern — never call it directly.
     */
    fun deactivateAdmin() {
        dpm.removeActiveAdmin(adminComponent)
    }
}