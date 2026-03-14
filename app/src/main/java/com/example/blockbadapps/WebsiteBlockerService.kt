package com.example.blockbadapps

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WebsiteBlockerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName ?: return
        val rootNode    = rootInActiveWindow ?: return

        val prefs        = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val blockedSites = prefs.getStringSet(MainActivity.BLOCKED_SITES_KEY, emptySet())
            ?: emptySet()

        if (blockedSites.isEmpty()) {
            rootNode.recycle()
            return
        }

        // Fix 3 aplicado aquí: reciclar urlNode siempre con try/finally
        var urlNode: AccessibilityNodeInfo? = null
        try {
            urlNode = findUrlNode(rootNode)
            val url = urlNode?.text?.toString()?.lowercase() ?: return

            for (site in blockedSites) {
                val cleaned    = site.trimEnd('/')
                val index      = url.indexOf(cleaned)
                if (index == -1) continue

                val end        = index + cleaned.length
                val validStart = index == 0 || url[index - 1] in listOf('.', '/')
                val validEnd   = end == url.length || url[end] in listOf('.', '/')

                if (validStart && validEnd) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    handleBlockActions(packageName)
                    break
                }
            }
        } finally {
            urlNode?.recycle()   // Fix 3: se recicla SIEMPRE, incluso si hay excepción
            rootNode.recycle()
        }
    }

    private fun handleBlockActions(packageName: CharSequence?) {
        // Fix 4: inicializar racha antes de loguear
        val sm = StreakManager(applicationContext)
        sm.initStreakIfNeeded()
        sm.logBlockedAttempt()

        Handler(Looper.getMainLooper()).post {
            val intent = Intent(applicationContext, BlockedScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }
    }

    private fun findUrlNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val ids = listOf(
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.duckduckgo.mobile.android:id/omnibarTextInput",
            "com.brave.browser:id/url_bar",
            "com.microsoft.emmx:id/url_bar",
            "com.opera.browser:id/url_field",
            "com.opera.mini.native:id/url_field"
        )
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        return null
    }

    override fun onInterrupt() {}
    override fun onServiceConnected() { super.onServiceConnected() }
}