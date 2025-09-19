package com.example.blockbadapps

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var statusTextView: TextView
    private lateinit var enableServiceButton: Button
    private lateinit var siteEditText: EditText
    private lateinit var addButton: Button
    private lateinit var importButton: Button
    private lateinit var keywordsRecyclerView: RecyclerView

    // Data
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var keywordAdapter: KeywordAdapter

    companion object {
        const val PREFS_NAME = "BlockBadAppsPrefs"
        const val BLOCKED_SITES_KEY = "BlockedSites"
    }

    // --- Activity Lifecycle ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize everything
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        bindViews()
        setupRecyclerView()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        loadAndDisplayKeywords()
    }

    // --- Setup ---

    private fun bindViews() {
        statusTextView = findViewById(R.id.statusTextView)
        enableServiceButton = findViewById(R.id.enableServiceButton)
        siteEditText = findViewById(R.id.siteEditText)
        addButton = findViewById(R.id.addButton)
        importButton = findViewById(R.id.importButton)
        keywordsRecyclerView = findViewById(R.id.keywordsRecyclerView)
    }

    private fun setupRecyclerView() {
        keywordAdapter = KeywordAdapter { keyword ->
            // This is the delete action that will be passed to the adapter
            removeKeyword(keyword)
        }
        keywordsRecyclerView.layoutManager = LinearLayoutManager(this)
        keywordsRecyclerView.adapter = keywordAdapter
    }

    private fun setupClickListeners() {
        enableServiceButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addButton.setOnClickListener {
            addKeyword(siteEditText.text.toString())
        }
        importButton.setOnClickListener {
            filePickerLauncher.launch(arrayOf("text/plain"))
        }
    }

    // --- Keyword Management ---

    private fun loadAndDisplayKeywords() {
        val keywordSet = sharedPreferences.getStringSet(BLOCKED_SITES_KEY, emptySet()) ?: emptySet()
        // submitList is part of ListAdapter and handles the diffing automatically
        keywordAdapter.submitList(keywordSet.sorted())
    }

    private fun addKeyword(keyword: String) {
        val cleanKeyword = keyword.trim().lowercase()
        if (cleanKeyword.isEmpty()) return

        val currentKeywords = sharedPreferences.getStringSet(BLOCKED_SITES_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        if (currentKeywords.add(cleanKeyword)) {
            saveKeywords(currentKeywords)
            siteEditText.text.clear()
            Toast.makeText(this, "'$cleanKeyword' added to blocklist", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "'$cleanKeyword' is already on the blocklist", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeKeyword(keyword: String) {
        val currentKeywords = sharedPreferences.getStringSet(BLOCKED_SITES_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (currentKeywords.remove(keyword)) {
            saveKeywords(currentKeywords)
            Toast.makeText(this, "'$keyword' removed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveKeywords(keywords: Set<String>) {
        sharedPreferences.edit().putStringSet(BLOCKED_SITES_KEY, keywords).apply()
        loadAndDisplayKeywords() // Reload and refresh the list
    }

    // --- File Import ---

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { readKeywordsFromUri(it) }
    }

    private fun readKeywordsFromUri(uri: Uri) {
        val newKeywordsFromFile = mutableSetOf<String>()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).forEachLine { line ->
                    val keyword = line.trim().lowercase()
                    if (keyword.isNotEmpty()) newKeywordsFromFile.add(keyword)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading file", Toast.LENGTH_SHORT).show()
            return
        }

        val currentKeywords = sharedPreferences.getStringSet(BLOCKED_SITES_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val originalSize = currentKeywords.size
        currentKeywords.addAll(newKeywordsFromFile)

        if (currentKeywords.size > originalSize) {
            saveKeywords(currentKeywords)
            val addedCount = currentKeywords.size - originalSize
            Toast.makeText(this, "Added $addedCount new keywords", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "No new keywords found in file", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Service Status ---

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled()) {
            statusTextView.text = "Enabled"
            statusTextView.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            statusTextView.text = "Disabled"
            statusTextView.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.id.contains(packageName) }
    }

    // --- RecyclerView Adapter (Upgraded to ListAdapter with DiffUtil) ---

    class KeywordAdapter(private val onDeleteClicked: (String) -> Unit) :
        ListAdapter<String, KeywordAdapter.KeywordViewHolder>(KeywordDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeywordViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_keyword, parent, false)
            return KeywordViewHolder(view)
        }

        override fun onBindViewHolder(holder: KeywordViewHolder, position: Int) {
            val keyword = getItem(position)
            holder.bind(keyword, onDeleteClicked)
        }

        class KeywordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val keywordTextView: TextView = itemView.findViewById(R.id.keywordTextView)
            private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

            fun bind(keyword: String, onDeleteClicked: (String) -> Unit) {
                keywordTextView.text = keyword
                deleteButton.setOnClickListener {
                    onDeleteClicked(keyword)
                }
            }
        }

        // This class calculates the difference between two lists for efficient updates
        class KeywordDiffCallback : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }
        }
    }
}

