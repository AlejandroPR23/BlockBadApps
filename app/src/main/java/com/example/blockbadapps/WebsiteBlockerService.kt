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
        val packageName = event?.packageName ?: return
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
                val cleanedSite = site.trimEnd('/')

                if (url.contains(cleanedSite)) {
                    val index = url.indexOf(cleanedSite)
                    val endIndex = index + cleanedSite.length
                    val isStartValid = (index == 0) || (url[index - 1] == '.' || url[index - 1] == '/')
                    val isEndValid = (endIndex == url.length) || (url[endIndex] == '.' || url[endIndex] == '/')

                    if (isStartValid && isEndValid) {
                        performGlobalAction(GLOBAL_ACTION_BACK)

                        handleBlockActions(packageName)

                        break
                    }
                }
            }
        }

        rootNode.recycle()
    }

    private fun openMotivationalPage(packageName: CharSequence?) {
        val motivationalPageUrl = "https://ieelcorozal.com/"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(motivationalPageUrl))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        if (packageName != null) {
            intent.setPackage(packageName.toString())
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            if (packageName != null) {
                val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(motivationalPageUrl))
                genericIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    startActivity(genericIntent)
                } catch (e2: Exception) {
                    goToHomeScreen()
                }
            } else {
                goToHomeScreen()
            }
        }
    }

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
            if (nodes.isNotEmpty()) {
                return nodes[0]
            }
        }
        return null
    }

    private fun openMotivationalVideo() {
        val videoUrl = "https://www.youtube.com/watch?v=zBxBT3k7Jb0"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            startActivity(intent)
        } catch (e: Exception) {
            goToHomeScreen()
        }
    }


    private fun handleBlockActions(packageName: CharSequence?) {
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post { showBlockToast() }

        mainHandler.postDelayed({
            openMotivationalPage(packageName)
        }, 500)

        mainHandler.postDelayed({
            openMotivationalVideo()
        }, 60000)
    }

    private fun goToHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun showBlockToast() {
        Toast.makeText(applicationContext, "Stay Focused!", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
    }
}

