package com.example.blockbadapps

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.net.VpnService
import android.os.Build
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
import android.app.admin.DevicePolicyManager
import androidx.work.WorkManager

class MainActivity : AppCompatActivity() {

    // ── UI Elements ──────────────────────────────────────────────────────────
    private lateinit var statusTextView: TextView
    private lateinit var enableServiceButton: Button
    private lateinit var vpnStatusTextView: TextView        // NEW
    private lateinit var vpnToggleButton: Button            // NEW
    private lateinit var siteEditText: EditText
    private lateinit var addButton: Button
    private lateinit var importButton: Button
    private lateinit var setupPatternButton: Button
    private lateinit var keywordsRecyclerView: RecyclerView

    // ── Data ─────────────────────────────────────────────────────────────────
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var keywordAdapter: KeywordAdapter
    private lateinit var patternManager: PatternManager
    private lateinit var deviceAdminManager: DeviceAdminManager
    private lateinit var progressButton: Button

    private var hasVerifiedPattern = false
    private var isLaunchingSystemDialog = false


    companion object {
        const val PREFS_NAME       = "BlockBadAppsPrefs"
        const val BLOCKED_SITES_KEY = "BlockedSites"
    }

    // ── Launchers ────────────────────────────────────────────────────────────
    private val setupPatternLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Patron configurado", Toast.LENGTH_SHORT).show()
            hasVerifiedPattern = true
        }
    }

    private val verifyPatternLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            hasVerifiedPattern = true
        } else {
            finishAffinity()
        }
    }

    // NEW: Handles the system "Allow VPN?" dialog
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLaunchingSystemDialog = false              // ← NUEVA LÍNEA
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "Permiso VPN denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        patternManager    = PatternManager(this)
        deviceAdminManager = DeviceAdminManager(this)
        bindViews()
        setupRecyclerView()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()

        if (!hasVerifiedPattern) {
            if (!patternManager.isPatternSet()) {
                setupPatternLauncher.launch(Intent(this, SetupPatternActivity::class.java))
            } else {
                verifyPatternLauncher.launch(Intent(this, VerifyPatternActivity::class.java))
            }
        } else {
            StreakManager(this).initStreakIfNeeded()
            updateServiceStatus()
            updateVpnStatus()          // NEW
            updateAdminStatus()
            loadAndDisplayKeywords()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isLaunchingSystemDialog) {              // ← CONDICIÓN NUEVA
            hasVerifiedPattern = false
        }
    }

    // ── Setup ────────────────────────────────────────────────────────────────
    private fun bindViews() {
        statusTextView        = findViewById(R.id.statusTextView)
        enableServiceButton   = findViewById(R.id.enableServiceButton)
        vpnStatusTextView     = findViewById(R.id.vpnStatusTextView)    // NEW
        vpnToggleButton       = findViewById(R.id.vpnToggleButton)       // NEW
        siteEditText          = findViewById(R.id.siteEditText)
        addButton             = findViewById(R.id.addButton)
        importButton          = findViewById(R.id.importButton)
        setupPatternButton    = findViewById(R.id.setupPatternButton)
        progressButton = findViewById(R.id.progressButton)
        keywordsRecyclerView  = findViewById(R.id.keywordsRecyclerView)
    }

    private fun setupRecyclerView() {
        keywordAdapter = KeywordAdapter { removeKeyword(it) }
        keywordsRecyclerView.layoutManager = LinearLayoutManager(this)
        keywordsRecyclerView.adapter       = keywordAdapter
    }

    private fun setupClickListeners() {
        enableServiceButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        vpnToggleButton.setOnClickListener { toggleVpn() }
        addButton.setOnClickListener { addKeyword(siteEditText.text.toString()) }
        importButton.setOnClickListener { filePickerLauncher.launch(arrayOf("text/plain")) }
        setupPatternButton.setOnClickListener {
            if (deviceAdminManager.isAdminActive()) {               // FASE 2
                // Admin ya activo → solo permite cambiar el patrón
                setupPatternLauncher.launch(Intent(this, SetupPatternActivity::class.java))
            } else {
                // Admin no activo → activarlo primero
                activateDeviceAdmin()                               // FASE 2
            }
        }
        progressButton.setOnClickListener {
            startActivity(Intent(this, ProgressActivity::class.java))
        }
    }

    // ── VPN controls (NEW) ───────────────────────────────────────────────────
    private fun toggleVpn() {
        if (BlockingVpnService.isRunning) {
            stopVpnService()
        } else {
            val permIntent = VpnService.prepare(this)
            if (permIntent != null) {
                isLaunchingSystemDialog = true
                vpnPermissionLauncher.launch(permIntent)
            } else {
                startVpnService()
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, BlockingVpnService::class.java).apply {
            action = BlockingVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // FASE 2: Persist the intent so the Watchdog knows to keep VPN alive
        getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(VpnWatchdogWorker.PREF_VPN_ENABLED, true).apply()
        VpnWatchdogWorker.schedule(this)                            // FASE 2

        vpnToggleButton.postDelayed({ updateVpnStatus() }, 600)
    }

    private fun stopVpnService() {
        startService(Intent(this, BlockingVpnService::class.java).apply {
            action = BlockingVpnService.ACTION_STOP
        })
        // FASE 2: Tell the Watchdog to stop reviving the VPN
        getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(VpnWatchdogWorker.PREF_VPN_ENABLED, false).apply()
        VpnWatchdogWorker.cancel(this)                              // FASE 2

        vpnToggleButton.postDelayed({ updateVpnStatus() }, 600)
    }

    private fun updateVpnStatus() {
        if (BlockingVpnService.isRunning) {
            vpnStatusTextView.text = "Activo"
            vpnStatusTextView.setTextColor(getColor(android.R.color.holo_green_dark))
            vpnToggleButton.text = "Desactivar VPN"
        } else {
            vpnStatusTextView.text = "Inactivo"
            vpnStatusTextView.setTextColor(getColor(android.R.color.holo_red_dark))
            vpnToggleButton.text = "Activar VPN"
        }
    }

    // ── Keyword management (unchanged) ───────────────────────────────────────
    private fun loadAndDisplayKeywords() {
        val keywordSet = sharedPreferences.getStringSet(BLOCKED_SITES_KEY, emptySet()) ?: emptySet()
        keywordAdapter.submitList(keywordSet.sorted())
    }

    private fun addKeyword(keyword: String) {
        val clean = keyword.trim().lowercase()
        if (clean.isEmpty()) return
        val current = sharedPreferences.getStringSet(BLOCKED_SITES_KEY, mutableSetOf())
            ?.toMutableSet() ?: mutableSetOf()
        if (current.add(clean)) {
            saveKeywords(current)
            siteEditText.text.clear()
            Toast.makeText(this, "'$clean' agregado", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "'$clean' ya esta en la lista", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeKeyword(keyword: String) {
        val current = sharedPreferences.getStringSet(BLOCKED_SITES_KEY, mutableSetOf())
            ?.toMutableSet() ?: mutableSetOf()
        if (current.remove(keyword)) {
            saveKeywords(current)
            Toast.makeText(this, "'$keyword' eliminado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveKeywords(keywords: Set<String>) {
        sharedPreferences.edit().putStringSet(BLOCKED_SITES_KEY, keywords).apply()
        loadAndDisplayKeywords()
    }

    // ── File import (unchanged) ──────────────────────────────────────────────
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { readKeywordsFromUri(it) } }

    private fun readKeywordsFromUri(uri: Uri) {
        val newKeywords = mutableSetOf<String>()
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                    val kw = line.trim().lowercase()
                    if (kw.isNotEmpty()) newKeywords.add(kw)
                }
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Error leyendo archivo", Toast.LENGTH_SHORT).show()
            return
        }

        val current  = sharedPreferences.getStringSet(BLOCKED_SITES_KEY, mutableSetOf())
            ?.toMutableSet() ?: mutableSetOf()
        val before   = current.size
        current.addAll(newKeywords)

        if (current.size > before) {
            saveKeywords(current)
            Toast.makeText(this, "Agregadas ${current.size - before} palabras", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "No hay palabras nuevas en el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Service status (unchanged) ───────────────────────────────────────────
    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled()) {
            statusTextView.text = "Habilitado"
            statusTextView.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            statusTextView.text = "Deshabilitado"
            statusTextView.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.id.contains(packageName) }
    }

    // ── Adapter (unchanged) ──────────────────────────────────────────────────
    class KeywordAdapter(private val onDeleteClicked: (String) -> Unit) :
        ListAdapter<String, KeywordAdapter.KeywordViewHolder>(KeywordDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeywordViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_keyword, parent, false)
            return KeywordViewHolder(view)
        }

        override fun onBindViewHolder(holder: KeywordViewHolder, position: Int) {
            holder.bind(getItem(position), onDeleteClicked)
        }

        class KeywordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val keywordTextView: TextView  = itemView.findViewById(R.id.keywordTextView)
            private val deleteButton: ImageButton  = itemView.findViewById(R.id.deleteButton)

            fun bind(keyword: String, onDeleteClicked: (String) -> Unit) {
                keywordTextView.text = keyword
                deleteButton.setOnClickListener { onDeleteClicked(keyword) }
            }
        }

        class KeywordDiffCallback : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
            override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        }
    }

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLaunchingSystemDialog = false
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Proteccion anti-desinstalacion activada", Toast.LENGTH_SHORT).show()
            // Now let them set the pattern
            setupPatternLauncher.launch(Intent(this, SetupPatternActivity::class.java))
        } else {
            Toast.makeText(this, "Sin administrador de dispositivo el bloqueo es eludible", Toast.LENGTH_LONG).show()
        }
    }

    private fun activateDeviceAdmin() {
        isLaunchingSystemDialog = true
        deviceAdminLauncher.launch(deviceAdminManager.buildActivationIntent())
    }

    private fun updateAdminStatus() {
        val adminActive = deviceAdminManager.isAdminActive()
        setupPatternButton.text = if (adminActive)
            "Cambiar patron de desbloqueo"
        else
            "Activar proteccion (recomendado)"
    }
}