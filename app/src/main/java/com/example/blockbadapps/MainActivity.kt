package com.example.blockbadapps

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var websiteInput: EditText
    private lateinit var addButton: Button
    private lateinit var blockedSitesListView: ListView
    private lateinit var enableServiceButton: Button
    private lateinit var serviceStatusText: TextView

    private val blockedSites = mutableSetOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        const val PREFS_NAME = "BlockBadAppsPrefs"
        const val BLOCKED_SITES_KEY = "blockedSites"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        websiteInput = findViewById(R.id.website_input)
        addButton = findViewById(R.id.add_button)
        blockedSitesListView = findViewById(R.id.blocked_sites_list)
        enableServiceButton = findViewById(R.id.enable_service_button)
        serviceStatusText = findViewById(R.id.service_status_text)

        // Setup SharedPreferences for storing the blocklist
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        loadBlockedSites()

        // Setup the adapter to display the list of blocked sites
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, blockedSites.toList())
        blockedSitesListView.adapter = adapter

        // Set click listener for the "Add" button
        addButton.setOnClickListener {
            val site = websiteInput.text.toString().trim().lowercase()
            if (site.isNotEmpty()) {
                if (blockedSites.add(site)) {
                    saveBlockedSites()
                    updateListView()
                    websiteInput.text.clear()
                    Toast.makeText(this, "$site added to blocklist", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "$site is already on the list", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Set long click listener on list items to remove a site
        blockedSitesListView.setOnItemLongClickListener { _, _, position, _ ->
            val siteToRemove = adapter.getItem(position)
            if (siteToRemove != null) {
                blockedSites.remove(siteToRemove)
                saveBlockedSites()
                updateListView()
                Toast.makeText(this, "$siteToRemove removed", Toast.LENGTH_SHORT).show()
            }
            true // Consume the long click event
        }

        // Set click listener to open accessibility settings
        enableServiceButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Find 'BlockBadApps' and enable it.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update the service status whenever the app is brought to the foreground
        updateServiceStatus()
    }

    private fun loadBlockedSites() {
        val savedSites = sharedPreferences.getStringSet(BLOCKED_SITES_KEY, emptySet())
        blockedSites.clear()
        savedSites?.let {
            blockedSites.addAll(it)
        }
    }

    private fun saveBlockedSites() {
        with(sharedPreferences.edit()) {
            putStringSet(BLOCKED_SITES_KEY, blockedSites)
            apply()
        }
    }

    private fun updateListView() {
        adapter.clear()
        adapter.addAll(blockedSites.toList().sorted())
        adapter.notifyDataSetChanged()
    }

    /**
     * UPDATED FUNCTION
     * This now correctly references AccessibilityServiceInfo.FEEDBACK_GENERIC
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        // CORRECTED LINE: The constant is from AccessibilityServiceInfo
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)

        for (service in enabledServices) {
            if (service.id == "$packageName/.WebsiteBlockerService") {
                return true
            }
        }
        return false
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled()) {
            serviceStatusText.text = "Service Status: Enabled"
            serviceStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            serviceStatusText.text = "Service Status: Disabled"
            serviceStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }
}

