package com.fly8.clone

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.fly8.clone.backend.CloneBackend
import com.fly8.clone.backend.NativeProfileBackend
import com.fly8.clone.connection.ConnectionManager
import com.fly8.clone.connection.ConnectionUi
import com.fly8.clone.model.CloneEntry
import com.fly8.clone.model.ProfileKind
import com.fly8.clone.model.SessionState
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import rikka.shizuku.Shizuku
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val uiBackground = Color.rgb(0, 0, 1)
    private val surface = Color.rgb(1, 2, 5)
    private val panel = Color.rgb(1, 19, 42)
    private val primary = Color.rgb(23, 110, 148)
    private val secondary = Color.rgb(1, 48, 76)
    private val uiText = Color.rgb(252, 253, 253)
    private val pink = Color.rgb(245, 218, 218)
    private val muted = Color.rgb(98, 103, 115)
    private val line = Color.rgb(18, 50, 74)
    private val good = Color.rgb(90, 201, 168)

    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var headerActions: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var store: CloneStore
    private lateinit var activityLog: ActivityLogStore

    private val clones = mutableListOf<CloneEntry>()
    private val executor = Executors.newSingleThreadExecutor()
    private val connectionManager by lazy { ConnectionManager(applicationContext) }
    private val backend: CloneBackend get() = NativeProfileBackend(connectionManager.bridge())
    private val settings by lazy { getSharedPreferences("fly8_settings", MODE_PRIVATE) }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            refreshStatus()
            renderCurrentPage()
        }
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread {
            refreshStatus()
            renderCurrentPage()
        }
    }

    private enum class Page { DASHBOARD, SESSIONS, ABOUT, APPS, RECOVERY, LOGS, SETTINGS }

    private var page = Page.DASHBOARD

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = CloneStore(this)
        activityLog = ActivityLogStore(this)
        clones.addAll(store.load())
        setContentView(buildRoot())
        renderCurrentPage()
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        syncProfiles()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (settings.getBoolean("refresh_profiles", true)) syncProfiles()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(uiBackground)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        titleView = TextView(this).apply {
            textSize = 27f
            setTextColor(uiText)
            typeface = Typeface.DEFAULT_BOLD
        }
        top.addView(titleView, LinearLayout.LayoutParams(0, dp(44), 1f))

        headerActions = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(headerActions, LinearLayout.LayoutParams(-2, dp(44)))
        header.addView(top)

        subtitleView = TextView(this).apply {
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(2), 0, dp(7))
        }
        header.addView(subtitleView)

        statusView = TextView(this).apply {
            textSize = 11f
            setTextColor(primary)
            setPadding(0, dp(4), 0, dp(8))
        }
        header.addView(statusView)
        root.addView(header)

        scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(uiBackground)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(18))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(buildBottomNav(), LinearLayout.LayoutParams(-1, dp(72)))
        return root
    }

    private fun buildBottomNav(): View {
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(10))
            setBackgroundColor(surface)
        }
        bar.addView(navItem("▦", "Dashboard", Page.DASHBOARD), navWeight())
        bar.addView(navItem("▣", "Sessions", Page.SESSIONS), navWeight())
        bar.addView(navItem("ⓘ", "About", Page.ABOUT), navWeight())
        return bar
    }

    private fun navItem(icon: String, label: String, target: Page): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(4), dp(7), dp(4))
            setOnClickListener {
                page = target
                renderCurrentPage()
            }
        }
        val iconView = TextView(this).apply {
            text = icon
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 10f
            gravity = Gravity.CENTER
        }
        card.addView(iconView, LinearLayout.LayoutParams(-1, dp(26)))
        card.addView(labelView, LinearLayout.LayoutParams(-1, dp(20)))
        card.tag = target
        return card
    }

    private fun renderCurrentPage() {
        content.removeAllViews()
        headerActions.removeAllViews()

        when (page) {
            Page.DASHBOARD -> renderDashboard()
            Page.SESSIONS -> renderSessions()
            Page.ABOUT -> renderAbout()
            Page.APPS -> renderApps()
            Page.RECOVERY -> renderRecovery()
            Page.LOGS -> renderLogs()
            Page.SETTINGS -> renderSettings()
        }
        refreshStatus()
    }

    private fun configureHeader(title: String, subtitle: String, back: Boolean = false) {
        titleView.text = title
        subtitleView.text = subtitle
        if (back) {
            headerActions.addView(actionButton("‹") {
                page = Page.DASHBOARD
                renderCurrentPage()
            })
        }
    }

    private fun renderDashboard() {
        configureHeader("Dashboard", "FLY8 • lightweight session control")
        headerActions.addView(actionButton("Apps") {
            page = Page.APPS
            renderCurrentPage()
        })
        headerActions.addView(actionButton("Logs") { page = Page.LOGS; renderCurrentPage() })
        headerActions.addView(actionButton("⋮") { showQuickMenu() })

        addSection("Overview", "Your device, backend and configured sessions.")
        addControlCard()
        addResourceCard()
        addRecoveryCard()
        addActivityCard()

        addSection("Design target", "Built for low RAM, low CPU overhead and predictable background work.")
        addInfoCard("Lightweight by design", "Static session previews, bounded logs, explicit background work and no embedded Android runtime.")
    }

    private fun addControlCard() {
        val ready = connectionReady()
        val method = connectionManager.currentMethod()
        val card = card()
        val body = vertical()
        body.addView(textPair("Control state", when {
            !ready -> "No control connection is ready. Choose Wireless debugging, Shizuku, or Root."
            else -> "Controls connected through " + connectionManager.statusText() + ". FLY8 can manage supported clone profiles."
        }, 16f))
        body.addView(badge(
            when {
                !ready -> "CONNECT CONTROLS"
                else -> method.name.replace('_', ' ').uppercase(Locale.ROOT)
            },
            if (ready) good else primary
        ))
        val actions = horizontal()
        actions.addView(actionButton("Connect controls") { showConnectControls() }, buttonWeight())
        actions.addView(actionButton("Manage instances") {
            page = Page.APPS
            renderCurrentPage()
        }, buttonWeight())
        body.addView(actions)
        card.addView(body)
        content.addView(card)
    }

    private fun addResourceCard() {
        val memory = getSystemService(ActivityManager::class.java)?.let {
            ActivityManager.MemoryInfo().also(it::getMemoryInfo)
        }
        val totalRam = memory?.totalMem ?: 0L
        val availableRam = memory?.availMem ?: 0L
        val usedRam = (totalRam - availableRam).coerceAtLeast(0L)
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorage = stat.blockCountLong * stat.blockSizeLong
        val freeStorage = stat.availableBlocksLong * stat.blockSizeLong
        val usedStorage = (totalStorage - freeStorage).coerceAtLeast(0L)
        val running = clones.count { it.lastKnownState == SessionState.RUNNING }
        val chipset = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE

        val card = card()
        val body = vertical()
        val top = horizontal()
        top.addView(metric("Running apps", running.toString(), "sessions"), 0, metricWeight())
        top.addView(metric("Chipset", chipset.ifBlank { "Unknown" }, "system"), 0, metricWeight())
        body.addView(top)
        body.addView(divider())
        body.addView(sectionLabel("Processing"))
        body.addView(smallText("Host CPU is reported by Android only when available; FLY8 does not poll it continuously."))
        body.addView(smallText("Preview capture is disabled by default. Opening an Android session is required for a real app display; FLY8 does not maintain a continuous screenshot stream."))
        body.addView(divider())
        body.addView(sectionLabel("Storage"))
        body.addView(progressRow(formatBytes(usedStorage) + " / " + formatBytes(totalStorage) + " used", usedStorage.toDouble() / totalStorage.coerceAtLeast(1L)))
        body.addView(sectionLabel("RAM"))
        body.addView(progressRow(formatBytes(usedRam) + " / " + formatBytes(totalRam) + " used", usedRam.toDouble() / totalRam.coerceAtLeast(1L)))
        card.addView(body)
        content.addView(card)
    }

    private fun addRecoveryCard() {
        val enabled = clones.count { it.recoveryEnabled }
        val card = card()
        val body = vertical()
        body.addView(textPair("FLY8 Auto Recovery", if (enabled == 0) "Paused • add a session to configure recovery." else enabled.toString() + " session(s) have recovery enabled.", 18f))
        val fallback = settings.getString("recovery_fallback_target", "") ?: ""
        body.addView(actionButton(if (fallback.isBlank()) "Shared fallback target: not set" else "Shared fallback target: " + fallback) {
            editSharedFallbackTarget()
        })
        body.addView(smallText("Scheduled recovery uses Android alarms and performs work only when an alarm fires. No permanent polling loop is required."))
        val actions = horizontal()
        actions.addView(actionButton("Manage recovery") {
            page = Page.RECOVERY
            renderCurrentPage()
        }, buttonWeight())
        actions.addView(actionButton("Test next restart") {
            val target = clones.firstOrNull()
            if (target == null) toast("Create a session first.")
            else launchClone(target, true)
        }, buttonWeight())
        body.addView(actions)
        card.addView(body)
        content.addView(card)
    }

    private fun addActivityCard() {
        val logs = activityLog.load()
        val card = card()
        val body = vertical()
        body.addView(textPair("Recovery activity", if (logs.isEmpty()) "No recovery activity yet." else "Latest FLY8 session events.", 17f))
        logs.take(4).forEach {
            val pieces = it.split("|", limit = 2)
            val whenText = pieces.firstOrNull()?.toLongOrNull()?.let {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))
            } ?: "—"
            val message = pieces.getOrElse(1) { it }
            body.addView(smallText(whenText + "  •  " + message))
        }
        body.addView(actionButton("View all activity") {
            showTextDialog("Recovery activity", logs.joinToString("\n\n") { row ->
                val parts = row.split("|", limit = 2)
                val time = parts.firstOrNull()?.toLongOrNull()?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "Unknown"
                time + "\n" + parts.getOrElse(1) { row }
            }.ifBlank { "No activity yet." })
        })
        card.addView(body)
        content.addView(card)
    }

    private fun renderSessions() {
        configureHeader("Sessions", "Grid view • " + clones.size + " configured")
        headerActions.addView(actionButton("▦") { toast("Grid view is the default compact layout.") })
        headerActions.addView(actionButton("▶") { launchAllSequential() })
        headerActions.addView(actionButton("↻") { restartRunningSessions() })
        headerActions.addView(actionButton("■") { stopAllSessions() })
        headerActions.addView(actionButton("⚙") {
            page = Page.RECOVERY
            renderCurrentPage()
        })
        if (clones.isEmpty()) {
            addInfoCard("No running sessions", "Connect controls, then choose a clone from Apps. Your session definitions stay stored locally.")
            addFullButton("Choose a clone") {
                page = Page.APPS
                renderCurrentPage()
            }
            return
        }

        addSection("Live sessions", clones.size.toString() + " configured • static previews for low overhead")
        clones.chunked(2).forEach { pair ->
            val row = horizontal()
            pair.forEach { clone ->
                row.addView(sessionCard(clone), LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginEnd = dp(5)
                })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            content.addView(row)
        }
        addFullButton("Add app") {
            page = Page.APPS
            renderCurrentPage()
        }
    }

    private fun sessionCard(clone: CloneEntry): View {
        val card = card()
        val body = vertical()
        val top = horizontal()
        val icon = appIcon(clone.packageName)
        if (icon != null) {
            top.addView(ImageView(this).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, LinearLayout.LayoutParams(dp(34), dp(34)))
        }
        val info = vertical()
        info.addView(TextView(this).apply {
            text = clone.label
            textSize = 14f
            setTextColor(uiText)
            typeface = Typeface.DEFAULT_BOLD
        })
        info.addView(TextView(this).apply {
            text = "#" + (clones.indexOf(clone) + 1) + " • user " + clone.userId + " • " + if (clone.profileKind == ProfileKind.CLONE_PROFILE) "clone profile" else "secondary user" + "\n" + clone.packageName
            textSize = 9f
            setTextColor(muted)
            maxLines = 2
        })
        top.addView(info, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(8) })
        top.addView(TextView(this).apply {
            text = "⋮"
            textSize = 20f
            setTextColor(muted)
            setOnClickListener { showSessionMenu(clone) }
        }, LinearLayout.LayoutParams(dp(24), dp(42)))
        body.addView(top)

        val preview = MaterialCardView(this).apply {
            radius = dp(10).toFloat()
            setCardBackgroundColor(surface)
            strokeWidth = dp(1)
            strokeColor = line
        }
        preview.addView(FrameLayoutCompat(this).apply {
            val image = appIcon(clone.packageName)
            if (image != null) {
                addView(ImageView(this@MainActivity).apply {
                    setImageDrawable(image)
                    alpha = 0.65f
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }, FrameLayoutCompat.lp(dp(52), dp(52), Gravity.CENTER))
            }
            addView(TextView(this@MainActivity).apply {
                text = when (clone.lastKnownState) {
                    SessionState.RUNNING -> "RUNNING • OPEN APP TO VIEW"
                    SessionState.UNAVAILABLE -> "UNAVAILABLE"
                    SessionState.STOPPED -> "STOPPED"
                    SessionState.UNKNOWN -> "READY"
                }
                textSize = 10f
                setTextColor(if (clone.lastKnownState == SessionState.RUNNING) good else muted)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(190, 1, 2, 5))
            }, FrameLayoutCompat.lp(-1, dp(28), Gravity.BOTTOM))
        })
        body.addView(preview, LinearLayout.LayoutParams(-1, dp(135)).apply { topMargin = dp(8) })

        val state = TextView(this).apply {
            text = stateText(clone)
            textSize = 10f
            setTextColor(primary)
            setPadding(dp(4), dp(7), dp(4), dp(2))
        }
        body.addView(state)

        val actions = horizontal()
        actions.addView(actionButton("Start") { launchClone(clone) }, smallButtonWeight())
        actions.addView(actionButton("Stop") { stopClone(clone) }, smallButtonWeight())
        body.addView(actions, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(2) })

        card.addView(body)
        card.setOnClickListener { showSessionMenu(clone) }
        return card
    }

    private fun renderApps() {
        configureHeader("Apps", "Installed launcher apps • choose one to create a FLY8 session", true)
        headerActions.addView(actionButton("Refresh") { renderCurrentPage() })

        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { resolve ->
                val info = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (info.packageName == packageName) null
                else InstalledApp(
                    info.loadLabel(pm).toString(),
                    info.packageName,
                    info.loadIcon(pm)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.ROOT) }

        addInfoCard("Installed apps", apps.size.toString() + " launchable apps detected. FLY8 stores only the selected package and session metadata. Existing sessions for a package are shown as configured and are not duplicated.")
        apps.take(200).forEach { app ->
            val row = card()
            val line = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
            line.addView(ImageView(this).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(dp(44), dp(44)))
            line.addView(vertical().apply {
                addView(TextView(this@MainActivity).apply {
                    text = app.label
                    textSize = 15f
                    setTextColor(uiText)
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(this@MainActivity).apply {
                    text = app.packageName
                    textSize = 10f
                    setTextColor(muted)
                })
            }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(10) })
            line.addView(actionButton("Add") { createClone(app.packageName, app.label) })
            row.addView(line)
            content.addView(row)
        }
    }

    private fun renderRecovery() {
        configureHeader("Recovery", "Per-session restart and rejoin controls", true)
        headerActions.addView(actionButton("Settings") {
            page = Page.SETTINGS
            renderCurrentPage()
        })
        addInfoCard("How recovery works", "Recovery is opt-in per session. Scheduled restarts use Android alarms so FLY8 does not need a constantly running polling loop.")
        if (clones.isEmpty()) {
            addInfoCard("No sessions", "Add an app first.")
            return
        }

        clones.forEach { clone ->
            val card = card()
            val body = vertical()
            body.addView(textPair(clone.label, clone.packageName + " • profile " + clone.userId + " • " + if (clone.profileKind == ProfileKind.CLONE_PROFILE) "clone profile" else "secondary user", 16f))
            val enabled = SwitchMaterial(this).apply {
                text = "Scheduled recovery"
                setTextColor(uiText)
                isChecked = clone.recoveryEnabled
                setOnCheckedChangeListener { _, checked ->
                    updateClone(clone.userId) {
                        it.copy(recoveryEnabled = checked, lastActionAt = System.currentTimeMillis())
                    }
                    scheduleRecovery(clone.userId)
                }
            }
            body.addView(enabled)
            val interval = actionButton("Interval: " + clone.recoveryIntervalMinutes + " min") {
                chooseRecoveryInterval(clone)
            }
            body.addView(interval)
            val network = SwitchMaterial(this).apply {
                text = "Restart after network change"
                setTextColor(uiText)
                isChecked = clone.restartOnNetworkChange
                setOnCheckedChangeListener { _, checked ->
                    updateClone(clone.userId) { it.copy(restartOnNetworkChange = checked) }
                    if (checked) toast("Network-triggered recovery is device-dependent; the scheduler currently stores this preference.")
                }
            }
            body.addView(network)
            val target = actionButton(if (clone.rejoinTarget.isBlank()) "Rejoin target: not set" else "Rejoin target: " + clone.rejoinTarget) {
                editRejoinTarget(clone)
            }
            body.addView(target)
            val stalled = SwitchMaterial(this).apply {
                text = "Recover stalled sessions"
                setTextColor(uiText)
                isChecked = clone.stalledDetectionEnabled
                setOnCheckedChangeListener { _, checked ->
                    updateClone(clone.userId) { it.copy(stalledDetectionEnabled = checked) }
                    if (checked) toast("Stalled-screen detection is stored as an opt-in preference; no continuous screenshot polling is enabled.")
                }
            }
            body.addView(stalled)
            val ip = SwitchMaterial(this).apply {
                text = "Restart after public IP change"
                setTextColor(uiText)
                isChecked = clone.restartOnPublicIpChange
                setOnCheckedChangeListener { _, checked ->
                    updateClone(clone.userId) { it.copy(restartOnPublicIpChange = checked) }
                    if (checked) toast("Public-IP detection is intentionally not polled continuously.")
                }
            }
            body.addView(ip)
            body.addView(actionButton("Test restart now") { launchClone(clone, true) })
            card.addView(body)
        }
    }

    private fun renderLogs() {
        configureHeader("Logs", "Local session and recovery activity", true)
        headerActions.addView(actionButton("Clear") {
            AlertDialog.Builder(this)
                .setTitle("Clear local logs?")
                .setMessage("This removes only FLY8's local activity history.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    activityLog.clear()
                    renderCurrentPage()
                }
                .show()
        })
        val logs = activityLog.load()
        addInfoCard("Activity history", "Latest " + logs.size + " retained event(s). FLY8 keeps the log bounded to avoid unbounded disk growth.")
        if (logs.isEmpty()) {
            addInfoCard("No activity yet", "Session starts, stops, recovery events and failures will appear here.")
        } else {
            logs.forEach { row ->
                val parts = row.split("|", limit = 2)
                val time = parts.firstOrNull()?.toLongOrNull()?.let {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                } ?: "Unknown time"
                addInfoCard(time, parts.getOrElse(1) { row })
            }
        }
    }

    private fun editSharedFallbackTarget() {
        val input = EditText(this).apply {
            setText(settings.getString("recovery_fallback_target", ""))
            hint = "Roblox game link or place ID"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Shared recovery target")
            .setMessage("Used as a fallback value stored locally for recovery configuration. It does not log into Roblox.")
            .setView(input)
            .setNegativeButton("Clear") { _, _ ->
                settings.edit().remove("recovery_fallback_target").apply()
                renderCurrentPage()
            }
            .setNeutralButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().trim()
                settings.edit().putString("recovery_fallback_target", value).apply()
                renderCurrentPage()
            }
            .show()
    }

    private fun renderSettings() {
        configureHeader("Settings", "Local preferences and compatibility", true)
        addSection("Performance", "Conservative defaults for low-end Android devices.")
        addSettingSwitch("Low-resource mode", "Avoid continuous preview refresh and keep UI work event-driven.", true) {
            settings.edit().putBoolean("low_resource", it).apply()
        }
        addSettingSwitch("Refresh profiles on resume", "Reconcile saved profile IDs when FLY8 comes back to the foreground.", true) {
            settings.edit().putBoolean("refresh_profiles", it).apply()
        }
        addSettingSwitch("Confirm destructive actions", "Ask before removing a clone profile or clearing its data.", true) {
            settings.edit().putBoolean("confirm_destructive", it).apply()
        }

        addSection("Compatibility", "What this build can and cannot guarantee.")
        addInfoCard("Profile backend", "FLY8 can manage native clone profiles and, where supported, secondary users. The actual limit is controlled by Android/OEM policy.")
        addInfoCard("Control methods", "Wireless debugging, Shizuku, and Root are independent control methods. Pick the one your device supports. FLY8 does not require an activation account.")
        addInfoCard("Package", packageName + " • target SDK " + Build.VERSION.SDK_INT)
    }

    private fun addSettingSwitch(title: String, body: String, default: Boolean, changed: (Boolean) -> Unit) {
        val key = when (title) {
            "Low-resource mode" -> "low_resource"
            "Refresh profiles on resume" -> "refresh_profiles"
            else -> "confirm_destructive"
        }
        val card = card()
        val box = vertical()
        box.addView(textPair(title, body, 15f))
        val sw = SwitchMaterial(this).apply {
            isChecked = settings.getBoolean(key, default)
            text = if (isChecked) "Enabled" else "Disabled"
            setTextColor(primary)
            setOnCheckedChangeListener { _, checked ->
                text = if (checked) "Enabled" else "Disabled"
                changed(checked)
            }
        }
        box.addView(sw)
        card.addView(box)
        content.addView(card)
    }

    private fun renderAbout() {
        configureHeader("About", "FLY8 • independent lightweight Android session manager")
        headerActions.addView(actionButton("Settings") {
            page = Page.SETTINGS
            renderCurrentPage()
        })

        val brand = card()
        val body = vertical()
        body.gravity = Gravity.CENTER_HORIZONTAL
        body.addView(ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.fly8_logo))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, LinearLayout.LayoutParams(dp(82), dp(82)).apply { bottomMargin = dp(8) })
        body.addView(TextView(this).apply {
            text = "FLY8 Clone"
            textSize = 20f
            setTextColor(uiText)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        body.addView(TextView(this).apply {
            text = "Version 0.1.0-beta02"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
        })
        body.addView(TextView(this).apply {
            text = "Independent session management without an activation gate."
            textSize = 12f
            setTextColor(uiText)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(8))
        })
        brand.addView(body)
        content.addView(brand)

        addRowCard("How to use", "Create a session from Apps, then manage it from Sessions.") {
            showTextDialog("How to use", "1. Start Shizuku and grant FLY8 access.\n\n2. Open Apps and choose an installed launcher app.\n\n3. Create the clone profile and wait for the operation to finish.\n\n4. Start or stop the session from Sessions.\n\n5. Open Recovery to configure scheduled restart behavior.")
        }
        addRowCard("Open-source notices", "Third-party libraries used by FLY8.") {
            showTextDialog("Open-source notices", "Shizuku API/provider 13.1.5 • MIT License\n\nAndroidX • applicable AndroidX licenses\n\nMaterial Components • applicable Material Components license\n\nThese notices describe dependencies actually used by the independent FLY8 build.")
        }
        addRowCard("Privacy", "Local-first storage and no account requirement.") {
            showTextDialog("Privacy", "FLY8 stores session metadata locally on the device. It does not require an account or remote activation service. The app picker reads installed application metadata so you can choose a package to clone.")
        }
        addRowCard("Project notes", "Backend limits and compatibility.") {
            showTextDialog("Project notes", "FLY8 is an independent application. Its session engine uses Android profile primitives, while control is provided by Wireless debugging, Shizuku, or Root. OEM behavior differs, and this build does not claim unlimited simultaneous instances.")
        }
        addRowCard("Credits", "FLY8 design and implementation.") {
            showTextDialog("Credits", "FLY8 Clone is independently implemented from scratch. The eye/window artwork supplied for this project is used as FLY8 branding inspiration and launcher artwork.")
        }
    }

    private fun addRowCard(title: String, body: String, clicked: () -> Unit) {
        val card = card()
        val row = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { clicked() }
        }
        row.addView(vertical().apply {
            addView(textPair(title, body, 15f))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(muted)
        }, LinearLayout.LayoutParams(dp(24), dp(50)))
        card.addView(row)
        content.addView(card)
    }

    private fun addSection(title: String, subtitle: String) {
        content.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(primary)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(3), dp(10), dp(3), dp(3))
        })
        content.addView(TextView(this).apply {
            text = subtitle
            textSize = 11f
            setTextColor(muted)
            setPadding(dp(3), 0, dp(3), dp(8))
        })
    }

    private fun addInfoCard(title: String, body: String) {
        val card = card()
        card.addView(textPair(title, body, 15f))
        content.addView(card)
    }

    private fun addFullButton(label: String, click: () -> Unit) {
        content.addView(actionButton(label, click), LinearLayout.LayoutParams(-1, dp(48)).apply {
            bottomMargin = dp(10)
        })
    }

    private fun card() = MaterialCardView(this).apply {
        radius = dp(18).toFloat()
        setCardBackgroundColor(panel)
        strokeWidth = dp(1)
        strokeColor = line
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        }
    }

    private fun textPair(title: String, body: String, size: Float) = TextView(this).apply {
        text = title + "\n" + body
        textSize = size
        setTextColor(uiText)
        setPadding(dp(14), dp(13), dp(14), dp(4))
        if (size >= 16f) typeface = Typeface.DEFAULT_BOLD
    }

    private fun smallText(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        setTextColor(muted)
        setPadding(dp(14), dp(3), dp(14), dp(5))
    }

    private fun sectionLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        setTextColor(primary)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(14), dp(7), dp(14), dp(3))
    }

    private fun horizontal() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun vertical() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    private fun metric(title: String, value: String, sub: String) = TextView(this).apply {
        text = title + "\n" + value + "\n" + sub
        textSize = 11f
        setTextColor(uiText)
        setPadding(dp(14), dp(12), dp(10), dp(10))
    }

    private fun progressRow(label: String, fraction: Double): View {
        val box = vertical()
        box.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(uiText)
            setPadding(dp(14), dp(2), dp(14), dp(5))
        })
        val track = MaterialCardView(this).apply {
            radius = dp(3).toFloat()
            setCardBackgroundColor(secondary)
            strokeWidth = 0
        }
        val fill = View(this).apply { setBackgroundColor(primary) }
        track.addView(fill, LinearLayout.LayoutParams(
            (dp(320) * fraction.coerceIn(0.04, 1.0)).toInt().coerceAtLeast(dp(8)),
            dp(4)
        ))
        box.addView(track, LinearLayout.LayoutParams(-1, dp(6)).apply {
            setMargins(dp(14), 0, dp(14), dp(8))
        })
        return box
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(line)
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(7)
            bottomMargin = dp(7)
        }
    }

    private fun badge(label: String, color: Int) = TextView(this).apply {
        text = label
        textSize = 9f
        setTextColor(uiBackground)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(9), dp(5), dp(9), dp(5))
        background = GradientDrawable().apply {
            cornerRadius = dp(7).toFloat()
            setColor(color)
        }
        layoutParams = LinearLayout.LayoutParams(-2, dp(26)).apply {
            leftMargin = dp(14)
            bottomMargin = dp(10)
        }
    }

    private fun actionButton(label: String, click: () -> Unit) =
        MaterialButton(this).apply {
            text = label
            textSize = 11f
            minHeight = dp(40)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { click() }
        }

    private fun buttonWeight() = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
        marginEnd = dp(5)
        topMargin = dp(6)
    }

    private fun smallButtonWeight() = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
        marginEnd = dp(4)
    }

    private fun navWeight() = LinearLayout.LayoutParams(0, -1, 1f)

    private fun createClone(packageName: String, label: String) {
        if (!connectionReady()) {
            showTextDialog("Connect controls", "Start Shizuku and grant FLY8 permission before creating a session.")
            return
        }
        if (clones.any { it.packageName == packageName }) {
            showTextDialog("Already configured", "A FLY8 session for " + packageName + " already exists. Use Sessions to manage it.")
            return
        }
        toast("Creating session…")
        executor.execute {
            val result = runCatching { backend.create(packageName, label) }.getOrElse { Result.failure(it) }
            runOnUiThread {
                result.onSuccess {
                    clones.add(it)
                    store.save(clones)
                    activityLog.add("Created session: " + label + " • " + it.profileKind.name.lowercase())
                    RecoveryScheduler.schedule(this, it)
                    page = Page.SESSIONS
                    renderCurrentPage()
                    toast(if (it.profileKind == ProfileKind.CLONE_PROFILE) "Session created with native clone profile." else "Session created with secondary-user fallback.")
                }.onFailure { error ->
                    activityLog.add("Create failed: " + label)
                    AlertDialog.Builder(this)
                        .setTitle("Could not create session")
                        .setMessage(error.message ?: "Android rejected the profile operation.")
                        .setPositiveButton("Close", null)
                        .show()
                }
            }
        }
    }

    private fun launchClone(clone: CloneEntry, fromRecovery: Boolean = false) {
        executor.execute {
            val result = backend.launch(clone)
            runOnUiThread {
                result.onSuccess {
                    updateClone(clone.userId) {
                        it.copy(lastKnownState = SessionState.RUNNING, lastActionAt = System.currentTimeMillis())
                    }
                    activityLog.add((if (fromRecovery) "Recovery restart: " else "Started: ") + clone.label)
                    renderCurrentPage()
                    if (!fromRecovery) toast("Session started.")
                    if (clone.rejoinTarget.isNotBlank() && fromRecovery) activityLog.add("Rejoin target saved for " + clone.label + ": " + clone.rejoinTarget)
                }.onFailure {
                    activityLog.add("Launch failed: " + clone.label)
                    toast(it.message ?: "Launch failed.")
                }
            }
        }
    }

    private fun stopClone(clone: CloneEntry) {
        executor.execute {
            val result = backend.stop(clone)
            runOnUiThread {
                result.onSuccess {
                    updateClone(clone.userId) {
                        it.copy(lastKnownState = SessionState.STOPPED, lastActionAt = System.currentTimeMillis())
                    }
                    activityLog.add("Stopped: " + clone.label)
                    renderCurrentPage()
                    toast("Session stopped.")
                }.onFailure {
                    toast(it.message ?: "Stop failed.")
                }
            }
        }
    }

    private fun clearClone(clone: CloneEntry) {
        val work = {
            executor.execute {
                val result = backend.clear(clone)
                runOnUiThread {
                    result.onSuccess {
                        updateClone(clone.userId) { it.copy(lastActionAt = System.currentTimeMillis()) }
                        activityLog.add("Cleared data: " + clone.label)
                        renderCurrentPage()
                        toast("Clone data cleared.")
                    }.onFailure {
                        toast(it.message ?: "Clear failed.")
                    }
                }
            }
        }
        if (confirmDestructive()) {
            AlertDialog.Builder(this)
                .setTitle("Clear session data?")
                .setMessage("This clears " + clone.packageName + " inside profile " + clone.userId + ".")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ -> work() }
                .show()
        } else work()
    }

    private fun removeClone(clone: CloneEntry) {
        val work = {
            executor.execute {
                RecoveryScheduler.cancel(this, clone)
                val result = backend.remove(clone)
                runOnUiThread {
                    result.onSuccess {
                        clones.removeAll { it.userId == clone.userId }
                        store.save(clones)
                        activityLog.add("Removed session: " + clone.label)
                        renderCurrentPage()
                        toast("Session removed.")
                    }.onFailure {
                        toast(it.message ?: "Remove failed.")
                    }
                }
            }
        }
        if (confirmDestructive()) {
            AlertDialog.Builder(this)
                .setTitle("Remove session?")
                .setMessage("This removes the Android clone profile and its stored data.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove") { _, _ -> work() }
                .show()
        } else work()
    }

    private fun renameClone(clone: CloneEntry) {
        val input = EditText(this).apply {
            setText(clone.label)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("Rename session")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().trim().ifBlank { clone.label }
                updateClone(clone.userId) { it.copy(label = value) }
                activityLog.add("Renamed session to: " + value)
                renderCurrentPage()
            }
            .show()
    }

    private fun editRejoinTarget(clone: CloneEntry) {
        val input = EditText(this).apply {
            setText(clone.rejoinTarget)
            hint = "Roblox game link or place ID"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Rejoin target")
            .setMessage("This is stored locally as a recovery target. FLY8 does not log into your account or guarantee a successful join.")
            .setView(input)
            .setNegativeButton("Clear") { _, _ ->
                updateClone(clone.userId) { it.copy(rejoinTarget = "") }
                renderCurrentPage()
            }
            .setNeutralButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                updateClone(clone.userId) { it.copy(rejoinTarget = input.text.toString().trim()) }
                activityLog.add("Updated recovery target: " + clone.label)
                renderCurrentPage()
            }
            .show()
    }

    private fun chooseRecoveryInterval(clone: CloneEntry) {
        val values = arrayOf("5 minutes", "15 minutes", "30 minutes", "60 minutes", "120 minutes")
        val minutes = intArrayOf(5, 15, 30, 60, 120)
        val checked = minutes.indexOf(clone.recoveryIntervalMinutes).coerceAtLeast(1)
        AlertDialog.Builder(this)
            .setTitle("Recovery interval")
            .setSingleChoiceItems(values, checked) { dialog, which ->
                val next = minutes[which]
                updateClone(clone.userId) { it.copy(recoveryIntervalMinutes = next) }
                scheduleRecovery(clone.userId)
                dialog.dismiss()
                renderCurrentPage()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showConnectControls() {
        ConnectionUi.show(this, connectionManager) {
            refreshStatus()
            renderCurrentPage()
        }
    }

    private fun showQuickMenu() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Apps", "Recovery", "Settings", "Recheck profiles")) { _, which ->
                when (which) {
                    0 -> page = Page.APPS
                    1 -> page = Page.RECOVERY
                    2 -> page = Page.SETTINGS
                    else -> syncProfiles()
                }
                renderCurrentPage()
            }
            .show()
    }

    private fun showSessionMenu(clone: CloneEntry) {
        AlertDialog.Builder(this)
            .setTitle(clone.label)
            .setItems(arrayOf("Start", "Stop", "Clear data", "Rename", "Recovery settings", "Remove")) { _, which ->
                when (which) {
                    0 -> launchClone(clone)
                    1 -> stopClone(clone)
                    2 -> clearClone(clone)
                    3 -> renameClone(clone)
                    4 -> {
                        page = Page.RECOVERY
                        renderCurrentPage()
                    }
                    5 -> removeClone(clone)
                }
            }
            .show()
    }

    private fun stopAllSessions() {
        val snapshot = clones.filter { it.lastKnownState == SessionState.RUNNING }
        if (snapshot.isEmpty()) {
            toast("No sessions are marked running.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Stop running sessions?")
            .setMessage("FLY8 will stop " + snapshot.size + " session(s). Saved app data remains intact.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Stop") { _, _ ->
                executor.execute {
                    snapshot.forEachIndexed { index, clone ->
                        val result = backend.stop(clone)
                        runOnUiThread {
                            if (result.isSuccess) {
                                updateClone(clone.userId) {
                                    it.copy(lastKnownState = SessionState.STOPPED, lastActionAt = System.currentTimeMillis())
                                }
                                activityLog.add("Stopped in batch: " + clone.label)
                            } else {
                                activityLog.add("Batch stop failed: " + clone.label)
                            }
                            renderCurrentPage()
                        }
                        if (index < snapshot.lastIndex) Thread.sleep(1000)
                    }
                }
            }
            .show()
    }

    private fun launchAllSequential() {
        if (clones.isEmpty()) {
            toast("No configured sessions.")
            return
        }
        val snapshot = clones.toList()
        toast("Starting " + snapshot.size + " sessions one at a time…")
        executor.execute {
            snapshot.forEachIndexed { index, clone ->
                val result = backend.launch(clone)
                if (result.isSuccess) {
                    runOnUiThread {
                        updateClone(clone.userId) {
                            it.copy(lastKnownState = SessionState.RUNNING, lastActionAt = System.currentTimeMillis())
                        }
                        activityLog.add("Batch started: " + clone.label)
                    }
                } else {
                    runOnUiThread { activityLog.add("Batch launch failed: " + clone.label) }
                }
                if (index < snapshot.lastIndex) Thread.sleep(10_000)
            }
            runOnUiThread { renderCurrentPage() }
        }
    }

    private fun restartRunningSessions() {
        val running = clones.filter { it.lastKnownState == SessionState.RUNNING }
        if (running.isEmpty()) {
            toast("No sessions are marked running.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Restart running sessions?")
            .setMessage("FLY8 will restart " + running.size + " running session(s) one at a time, with a 10-second gap to reduce load. Saved recovery targets are not automatically guaranteed to rejoin.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restart") { _, _ ->
                executor.execute {
                    running.forEachIndexed { index, clone ->
                        val result = backend.stop(clone)
                        Thread.sleep(1000)
                        val started = if (result.isSuccess) backend.launch(clone) else Result.failure<Unit>(IllegalStateException("Stop failed"))
                        runOnUiThread {
                            if (started.isSuccess) {
                                updateClone(clone.userId) { it.copy(lastKnownState = SessionState.RUNNING, lastActionAt = System.currentTimeMillis()) }
                                activityLog.add("Restarted: " + clone.label)
                            } else {
                                updateClone(clone.userId) { it.copy(lastKnownState = SessionState.STOPPED, lastActionAt = System.currentTimeMillis()) }
                                activityLog.add("Restart failed: " + clone.label)
                            }
                            renderCurrentPage()
                        }
                        if (index < running.lastIndex) Thread.sleep(10_000)
                    }
                }
            }
            .show()
    }

    private fun updateClone(userId: Int, transform: (CloneEntry) -> CloneEntry) {
        val index = clones.indexOfFirst { it.userId == userId }
        if (index >= 0) {
            clones[index] = transform(clones[index])
            store.save(clones)
        }
    }

    private fun scheduleRecovery(userId: Int) {
        val clone = clones.firstOrNull { it.userId == userId } ?: return
        RecoveryScheduler.schedule(this, clone)
        if (!clone.recoveryEnabled) RecoveryScheduler.cancel(this, clone)
        store.save(clones)
    }

    private fun syncProfiles() {
        executor.execute {
            val bridge = connectionManager.bridge()
            val ready = runCatching { bridge.use { it.check().ok } }.getOrDefault(false)
            if (!ready) return@execute

            val result = bridge.use { it.run("pm list users") }
            if (!result.ok) return@execute

            val existing = Regex("UserInfo\\{([0-9]+):")
                .findAll(result.output)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .toSet()

            runOnUiThread {
                var changed = false
                clones.indices.forEach { index ->
                    val clone = clones[index]
                    val next = clone.copy(
                        lastKnownState = if (existing.contains(clone.userId)) {
                            if (clone.lastKnownState == SessionState.UNAVAILABLE) SessionState.UNKNOWN else clone.lastKnownState
                        } else {
                            SessionState.UNAVAILABLE
                        }
                    )
                    if (next != clone) {
                        clones[index] = next
                        changed = true
                    }
                }
                if (changed) store.save(clones)
                renderCurrentPage()
            }
        }
    }

    private fun connectionReady(): Boolean = connectionManager.isReady()

    private fun refreshStatus() {
        if (!::statusView.isInitialized) return
        statusView.text = when {
            connectionReady() -> "Controls connected • " + connectionManager.statusText() + " • Android " + Build.VERSION.RELEASE + " • " + clones.size + " sessions"
            else -> "Controls disconnected • choose Wireless debugging, Shizuku, or Root • " + clones.size + " sessions"
        }
    }

    private fun confirmDestructive() = settings.getBoolean("confirm_destructive", true)

    private fun stateText(clone: CloneEntry): String {
        val state = clone.lastKnownState.name.replace('_', ' ')
        val recovery = if (clone.recoveryEnabled) "ON" else "OFF"
        return state + " • recovery " + recovery + " • last action " +
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(clone.lastActionAt))
    }

    private fun appIcon(packageName: String) =
        runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()

    private fun formatBytes(value: Long): String {
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            value >= gb -> String.format(Locale.US, "%.2f GB", value / gb)
            value >= mb -> String.format(Locale.US, "%.0f MB", value / mb)
            value >= kb -> String.format(Locale.US, "%.0f KB", value / kb)
            else -> value.toString() + " B"
        }
    }

    private fun metricWeight() = LinearLayout.LayoutParams(0, dp(82), 1f)
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    private fun showTextDialog(title: String, body: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("Close", null).show()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable
)

private class FrameLayoutCompat(context: android.content.Context) : android.widget.FrameLayout(context) {
    companion object {
        fun lp(width: Int, height: Int, gravity: Int) =
            android.widget.FrameLayout.LayoutParams(width, height).apply { this.gravity = gravity }
    }
}
