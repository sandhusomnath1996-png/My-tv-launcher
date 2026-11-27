package com.custom.tvlauncher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var tvRamInfo: TextView
    private lateinit var tvAppsHeader: TextView
    private lateinit var rvApps: RecyclerView
    private var installedApps: List<AppInfo> = emptyList()
    private val recentPackages = LinkedHashSet<String>()
    private val ramHandler = Handler(Looper.getMainLooper())

    private val ramUpdater = object : Runnable {
        override fun run() {
            updateRamDisplay()
            ramHandler.postDelayed(this, 4000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvRamInfo = findViewById(R.id.tvRamInfo)
        tvAppsHeader = findViewById(R.id.tvAppsHeader)
        rvApps = findViewById(R.id.rvApps)

        setupTopActions()
        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        ramHandler.post(ramUpdater)
    }

    override fun onPause() {
        super.onPause()
        ramHandler.removeCallbacks(ramUpdater)
    }

    private fun updateRamDisplay() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = (totalMb - availMb).coerceAtLeast(0)
        tvRamInfo.text = "RAM: ${usedMb}MB / ${totalMb}MB"
    }

    private fun setupTopActions() {
        findViewById<View>(R.id.btnRecentApps).setOnClickListener {
            showRecentAppsDialog()
        }

        findViewById<View>(R.id.btnVoice).setOnClickListener {
            val intent = Intent(Intent.ACTION_ASSIST)
            tryStartActivity(intent, "Voice Assistant not available")
        }

        findViewById<View>(R.id.btnInputs).setOnClickListener {
            val intent = Intent("com.android.tv.action.INPUT_SETTINGS")
            tryStartActivity(intent, "TV Inputs menu not supported on this device")
        }

        findViewById<View>(R.id.btnQuickMenu).setOnClickListener {
            tryStartActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS), "Quick Menu unavailable")
        }

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            tryStartActivity(Intent(Settings.ACTION_SETTINGS), "Settings unavailable")
        }
    }

    private fun showRecentAppsDialog() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningLabels = recentPackages.mapNotNull { pkg ->
            installedApps.find { it.packageName == pkg }?.label ?: pkg
        }.toTypedArray()

        if (runningLabels.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Recent Apps")
                .setMessage("Abhi koi recent background app nahi hai. Aap sabhi background apps ko ek saath clear bhi kar sakte hain.")
                .setPositiveButton("Clear All Background Apps") { _, _ ->
                    clearAllBackgroundApps(am)
                }
                .setNegativeButton("Close", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Recent Apps (${runningLabels.size})")
            .setItems(runningLabels) { _, index ->
                val pkg = recentPackages.elementAtOrNull(index) ?: return@setItems
                am.killBackgroundProcesses(pkg)
                recentPackages.remove(pkg)
                updateRamDisplay()
                Toast.makeText(this, "Closed ${runningLabels[index]}", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Clear All Apps") { _, _ ->
                clearAllBackgroundApps(am)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun clearAllBackgroundApps(am: ActivityManager) {
        for (app in installedApps) {
            if (app.packageName != packageName) {
                am.killBackgroundProcesses(app.packageName)
            }
        }
        recentPackages.clear()
        updateRamDisplay()
        Toast.makeText(this, "All background apps cleared", Toast.LENGTH_SHORT).show()
    }

    private fun tryStartActivity(intent: Intent, fallbackMsg: String) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, fallbackMsg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val appMap = LinkedHashMap<String, AppInfo>()

        val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val leanbackApps = pm.queryIntentActivities(leanbackIntent, PackageManager.MATCH_ALL)

        val mobileIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val mobileApps = pm.queryIntentActivities(mobileIntent, PackageManager.MATCH_ALL)

        for (ri in leanbackApps + mobileApps) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName || appMap.containsKey(pkg)) continue
            val label = ri.loadLabel(pm).toString()
            val banner = ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm)
            val drawable = banner ?: ri.loadIcon(pm)
            appMap[pkg] = AppInfo(
                label = label,
                packageName = pkg,
                bannerOrIcon = drawable,
                isBanner = banner != null
            )
        }

        installedApps = appMap.values.toList()
        tvAppsHeader.text = "Apps (${installedApps.size})"

        rvApps.layoutManager = GridLayoutManager(this, 5)
        rvApps.adapter = AppAdapter(installedApps) { app ->
            val launchIntent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
                ?: pm.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                recentPackages.add(app.packageName)
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Cannot open ${app.label}", Toast.LENGTH_SHORT).show()
            }
        }
        rvApps.requestFocus()
    }
}
