package com.custom.tvlauncher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var tvRamInfo: TextView
    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvAppsHeader: TextView
    private lateinit var rvApps: RecyclerView
    private lateinit var prefs: SharedPreferences

    private var allInstalledApps: List<AppInfo> = emptyList()
    private val recentPackages = LinkedHashSet<String>()
    private val uiHandler = Handler(Looper.getMainLooper())

    private val themeColors = listOf(
        "Default Slate" to intArrayOf(Color.parseColor("#0F131A"), Color.parseColor("#1D2531")),
        "Midnight Blue" to intArrayOf(Color.parseColor("#060D1F"), Color.parseColor("#122857")),
        "AMOLED Black" to intArrayOf(Color.parseColor("#000000"), Color.parseColor("#090B10")),
        "Deep Violet" to intArrayOf(Color.parseColor("#12091F"), Color.parseColor("#2B174A")),
        "Forest Night" to intArrayOf(Color.parseColor("#071411"), Color.parseColor("#13312A"))
    )

    private val periodicUpdater = object : Runnable {
        override fun run() {
            updateRamDisplay()
            updateClockDisplay()
            uiHandler.postDelayed(this, 4000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("tv_launcher_prefs", Context.MODE_PRIVATE)
        rootLayout = findViewById(R.id.rootLayout)
        tvRamInfo = findViewById(R.id.tvRamInfo)
        tvClock = findViewById(R.id.tvClock)
        tvDate = findViewById(R.id.tvDate)
        tvAppsHeader = findViewById(R.id.tvAppsHeader)
        rvApps = findViewById(R.id.rvApps)

        applySavedTheme()
        setupTopActions()
        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        loadInstalledApps()
        uiHandler.post(periodicUpdater)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(periodicUpdater)
    }

    private fun applySavedTheme() {
        val idx = prefs.getInt("theme_index", 0).coerceIn(0, themeColors.lastIndex)
        val colors = themeColors[idx].second
        rootLayout.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
    }

    private fun updateClockDisplay() {
        val now = Date()
        tvClock.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        tvDate.text = SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(now)
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
        findViewById<View>(R.id.btnWifi).setOnClickListener {
            tryStartActivity(Intent(Settings.ACTION_WIFI_SETTINGS), "Wi-Fi settings unavailable")
        }
        findViewById<View>(R.id.btnRecentApps).setOnClickListener { showRecentAppsDialog() }
        findViewById<View>(R.id.btnVoice).setOnClickListener {
            tryStartActivity(Intent(Intent.ACTION_ASSIST), "Voice Assistant not available")
        }
        findViewById<View>(R.id.btnInputs).setOnClickListener {
            tryStartActivity(Intent("com.android.tv.action.INPUT_SETTINGS"), "TV Inputs menu not supported")
        }
        findViewById<View>(R.id.btnQuickMenu).setOnClickListener { showThemePickerDialog() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showLauncherSettingsDialog() }
    }

    private fun showLauncherSettingsDialog() {
        val options = arrayOf("Change Wallpaper / Theme", "Hidden Apps (Unhide)", "System TV Settings")
        AlertDialog.Builder(this)
            .setTitle("Launcher Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showThemePickerDialog()
                    1 -> showHiddenAppsDialog()
                    2 -> tryStartActivity(Intent(Settings.ACTION_SETTINGS), "Settings unavailable")
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showThemePickerDialog() {
        val names = themeColors.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Wallpaper Theme")
            .setItems(names) { _, which ->
                prefs.edit().putInt("theme_index", which).apply()
                applySavedTheme()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showHiddenAppsDialog() {
        val hiddenSet = prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()
        val hiddenApps = allInstalledApps.filter { hiddenSet.contains(it.packageName) }
        if (hiddenApps.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Hidden Apps")
                .setMessage("Abhi koi app hidden nahi hai. Kisi app par OK button dabaye rakhein (long press).")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val labels = hiddenApps.map { "${it.label} (Unhide)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Hidden Apps (${hiddenApps.size})")
            .setItems(labels) { _, which ->
                val pkg = hiddenApps[which].packageName
                val newHidden = hiddenSet.toMutableSet()
                newHidden.remove(pkg)
                prefs.edit().putStringSet("hidden_apps", newHidden).apply()
                refreshAppsGrid()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun getOrderedPackages(): MutableList<String> {
        val saved = prefs.getString("app_order", "") ?: ""
        val orderList = if (saved.isBlank()) mutableListOf() else saved.split(",").toMutableList()
        for (app in allInstalledApps) {
            if (!orderList.contains(app.packageName)) orderList.add(app.packageName)
        }
        return orderList
    }

    private fun moveAppPosition(pkg: String, direction: Int) {
        val orderList = getOrderedPackages()
        val idx = orderList.indexOf(pkg)
        val targetIdx = idx + direction
        if (idx < 0 || targetIdx < 0 || targetIdx >= orderList.size) return
        val temp = orderList[idx]
        orderList[idx] = orderList[targetIdx]
        orderList[targetIdx] = temp
        prefs.edit().putString("app_order", orderList.joinToString(",")).apply()
        refreshAppsGrid()
    }

    private fun showAppOptionsDialog(app: AppInfo) {
        val pinnedSet = prefs.getStringSet("pinned_apps", emptySet()) ?: emptySet()
        val hiddenSet = prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()
        val isPinned = pinnedSet.contains(app.packageName)

        val items = arrayOf(
            if (isPinned) "Unpin from Favorites" else "★ Pin to Top (Favorite)",
            "⬅ Move Left",
            "➡ Move Right",
            "Hide App from Home",
            "Uninstall App"
        )
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        val newPinned = pinnedSet.toMutableSet()
                        if (isPinned) newPinned.remove(app.packageName) else newPinned.add(app.packageName)
                        prefs.edit().putStringSet("pinned_apps", newPinned).apply()
                        refreshAppsGrid()
                    }
                    1 -> moveAppPosition(app.packageName, -1)
                    2 -> moveAppPosition(app.packageName, 1)
                    3 -> {
                        val newHidden = hiddenSet.toMutableSet()
                        newHidden.add(app.packageName)
                        prefs.edit().putStringSet("hidden_apps", newHidden).apply()
                        refreshAppsGrid()
                    }
                    4 -> {
                        val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                        tryStartActivity(uninstallIntent, "Cannot uninstall ${app.label}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRecentAppsDialog() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningLabels = recentPackages.mapNotNull { pkg ->
            allInstalledApps.find { it.packageName == pkg }?.label ?: pkg
        }.toTypedArray()

        if (runningLabels.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Recent Apps")
                .setMessage("Abhi koi recent background app nahi hai.")
                .setPositiveButton("Clear All Background Apps") { _, _ -> clearAllBackgroundApps(am) }
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
            .setPositiveButton("Clear All Apps") { _, _ -> clearAllBackgroundApps(am) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun clearAllBackgroundApps(am: ActivityManager) {
        for (app in allInstalledApps) {
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

        allInstalledApps = appMap.values.toList()
        rvApps.layoutManager = GridLayoutManager(this, 5)
        refreshAppsGrid()
        rvApps.requestFocus()
    }

    private fun refreshAppsGrid() {
        val pm = packageManager
        val pinnedSet = prefs.getStringSet("pinned_apps", emptySet()) ?: emptySet()
        val hiddenSet = prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()
        val orderList = getOrderedPackages()

        val visible = allInstalledApps
            .filter { !hiddenSet.contains(it.packageName) }
            .map { it.copy(isPinned = pinnedSet.contains(it.packageName)) }
            .sortedWith(compareByDescending<AppInfo> { it.isPinned }.thenBy { orderList.indexOf(it.packageName) })

        tvAppsHeader.text = "Apps (${visible.size})"
        rvApps.adapter = AppAdapter(
            apps = visible,
            onAppClick = { app ->
                val launchIntent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
                    ?: pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    recentPackages.add(app.packageName)
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this, "Cannot open ${app.label}", Toast.LENGTH_SHORT).show()
                }
            },
            onAppLongClick = { app ->
                showAppOptionsDialog(app)
            }
        )
    }
}
