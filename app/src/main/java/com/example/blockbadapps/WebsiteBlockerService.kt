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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return

        val sharedPreferences = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val blockedSites = sharedPreferences.getStringSet(MainActivity.BLOCKED_SITES_KEY, emptySet()) ?: emptySet()

        if (blockedSites.isEmpty()) {
            rootNode.recycle()
            return
        }

        val urlNode = findUrlNode(rootNode)
        val url = urlNode?.text?.toString()?.lowercase()

        urlNode?.recycle()

        if (url != null) {
            for (site in blockedSites) {
                if (url.contains(site)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)

                    Handler(Looper.getMainLooper()).postDelayed({
                        showBlockToast()
                        // UPDATED ACTION: Call the new function to open the video
                        openMotivationalVideo()
                    }, 200)

                    break
                }
            }
        }

        rootNode.recycle()
    }

    // NEW FUNCTION: Opens the specific YouTube video
    private fun openMotivationalVideo() {
        val videoUrl = "https://www.youtube.com/watch?v=zBxBT3k7Jb0"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback in case YouTube is not installed or something goes wrong
            Toast.makeText(applicationContext, "Could not open video.", Toast.LENGTH_SHORT).show()
            goToHomeScreen()
        }
    }

    private fun findUrlNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val browserUrlBarIds = listOf(
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/url_bar_title",
            "com.duckduckgo.mobile.android:id/omnibarTextInput",
            "com.brave.browser:id/url_bar",
            "com.microsoft.emmx:id/url_bar"
        )

        for (id in browserUrlBarIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                return nodes[0]
            }
        }
        return null
    }

    // This is now a fallback function
    private fun goToHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun showBlockToast() {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            Toast.makeText(applicationContext, "Stay Focused!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
    }
}

