package com.example.blockbadapps

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class WebsiteBlockerService : AccessibilityService() {

    // ─── Incognito detection strings ────────────────────────────────────────
    // These view IDs appear only in private/incognito tab UIs across browsers.
    private val incognitoIndicatorIds = setOf(
        "com.android.chrome:id/incognito_badge",
        "com.android.chrome:id/incognito_icon",
        "com.brave.browser:id/incognito_badge",
        "com.brave.browser:id/incognito_icon",
        "org.mozilla.firefox:id/private_browsing_icon",
        "com.microsoft.emmx:id/incognito_badge",
        "com.opera.browser:id/private_mode_icon"
    )

    // Content descriptions used as a fallback when IDs aren't found.
    private val incognitoDescriptions = setOf(
        "incognito", "private", "privado", "incógnito", "navegacion privada"
    )

    // ─── Main event handler ──────────────────────────────────────────────────
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName ?: return
        val rootNode    = rootInActiveWindow ?: return

        // ── 1. Incognito check (runs first — highest priority) ───────────────
        if (isIncognitoActive(rootNode)) {
            rootNode.recycle()
            handleIncognito()
            return
        }

        // ── 2. URL / keyword check (existing logic, unchanged) ───────────────
        val prefs        = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val blockedSites = prefs.getStringSet(MainActivity.BLOCKED_SITES_KEY, emptySet()) ?: emptySet()

        if (blockedSites.isEmpty()) { rootNode.recycle(); return }

        val urlNode = findUrlNode(rootNode)
        val url     = urlNode?.text?.toString()?.lowercase()
        urlNode?.recycle()

        if (url != null) {
            for (site in blockedSites) {
                val cleaned = site.trimEnd('/')
                if (url.contains(cleaned)) {
                    val idx      = url.indexOf(cleaned)
                    val end      = idx + cleaned.length
                    val validStart = idx == 0 || url[idx - 1] in listOf('.', '/')
                    val validEnd   = end == url.length || url[end] in listOf('.', '/')
                    if (validStart && validEnd) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        handleBlockActions(packageName)
                        break
                    }
                }
            }
        }

        rootNode.recycle()
    }

    // ─── Incognito detection ─────────────────────────────────────────────────
    /**
     * Returns true if any node in the current window looks like an incognito indicator.
     * Checks both view resource IDs and content descriptions.
     */
    private fun isIncognitoActive(root: AccessibilityNodeInfo): Boolean {
        // Check by View ID
        for (id in incognitoIndicatorIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                nodes.forEach { it.recycle() }
                return true
            }
        }

        // Fallback: traverse visible nodes looking for incognito content descriptions
        return containsIncognitoNode(root)
    }

    private fun containsIncognitoNode(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""

        if (incognitoDescriptions.any { desc.contains(it) || text.contains(it) }) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsIncognitoNode(child)) { child.recycle(); return true }
            child.recycle()
        }
        return false
    }

    private fun handleIncognito() {
        StreakManager(applicationContext).logBlockedAttempt()
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "Modo incógnito bloqueado", Toast.LENGTH_SHORT).show()
            val intent = Intent(applicationContext, BlockedScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }
    }

    // ─── URL bar finder (unchanged) ──────────────────────────────────────────
    private fun findUrlNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val browserUrlBarIds = listOf(
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.duckduckgo.mobile.android:id/omnibarTextInput",
            "com.brave.browser:id/url_bar",
            "com.microsoft.emmx:id/url_bar",
            "com.opera.browser:id/url_field",
            "com.opera.mini.native:id/url_field"
        )
        for (id in browserUrlBarIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        return null
    }

    // ─── Block actions (unchanged) ───────────────────────────────────────────
    private fun handleBlockActions(packageName: CharSequence?) {
        // Registrar intento bloqueado
        StreakManager(applicationContext).logBlockedAttempt()

        // Lanzar pantalla motivacional en lugar del Toast + URL
        Handler(Looper.getMainLooper()).post {
            val intent = Intent(applicationContext, BlockedScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }
    }

    private fun openMotivationalPage(packageName: CharSequence?) {
        val url    = "https://ieelcorozal.com/"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (packageName != null) setPackage(packageName.toString())
        }
        try { startActivity(intent) }
        catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }
            catch (_: Exception) { goToHomeScreen() }
        }
    }

    private fun openMotivationalVideo() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=zBxBT3k7Jb0")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try { startActivity(intent) } catch (_: Exception) { goToHomeScreen() }
    }

    private fun goToHomeScreen() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun showBlockToast() {
        Toast.makeText(applicationContext, "Mantente enfocado!", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}
    override fun onServiceConnected() { super.onServiceConnected() }
}