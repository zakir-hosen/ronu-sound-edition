package com.example.data

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.example.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val launcherApps: LauncherApps? =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    suspend fun loadLaunchableApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val selfPackage = context.packageName
        val entriesByPackage = LinkedHashMap<String, AppEntry>()

        // Primary query via LauncherApps (recommended modern Android launcher API)
        val activities = runCatching {
            launcherApps?.getActivityList(null, Process.myUserHandle()).orEmpty()
        }.getOrDefault(emptyList())

        for (info in activities) {
            val pkg = info.componentName.packageName
            if (pkg == selfPackage) continue
            val label = info.label?.toString()?.trim().orEmpty()
            if (label.isEmpty()) continue
            entriesByPackage.putIfAbsent(
                pkg,
                AppEntry(
                    label = label,
                    packageName = pkg,
                    activityName = info.componentName.className,
                    isKnownDistracting = isKnownDistractingApp(pkg, label)
                )
            )
        }

        // Supplement with PackageManager queryIntentActivities for full compatibility
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(mainIntent, 0)
        }

        for (resolveInfo in resolveInfos) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val pkg = activityInfo.packageName
            if (pkg == selfPackage || entriesByPackage.containsKey(pkg)) continue
            val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim().orEmpty()
            if (label.isEmpty()) continue
            entriesByPackage[pkg] = AppEntry(
                label = label,
                packageName = pkg,
                activityName = activityInfo.name,
                isKnownDistracting = isKnownDistractingApp(pkg, label)
            )
        }

        entriesByPackage.values.sortedWith(
            compareBy<AppEntry> { it.label.lowercase() }.thenBy { it.packageName }
        )
    }

    fun selectDefaultPinnedPackages(installedApps: List<AppEntry>): List<String> {
        if (installedApps.isEmpty()) return emptyList()

        val selected = mutableListOf<AppEntry>()
        val usedPackages = HashSet<String>()

        for (keywordGroup in ESSENTIAL_APP_PATTERNS) {
            if (selected.size >= DEFAULT_PINNED_COUNT) break
            val match = installedApps.firstOrNull { app ->
                app.packageName !in usedPackages &&
                    keywordGroup.any { pattern ->
                        app.packageName.contains(pattern, ignoreCase = true) ||
                            app.label.equals(pattern, ignoreCase = true) ||
                            app.label.contains(pattern, ignoreCase = true)
                    }
            }
            if (match != null) {
                selected.add(match)
                usedPackages.add(match.packageName)
            }
        }

        // Ensure 5–6 apps are pinned even on minimal devices/emulators
        for (app in installedApps) {
            if (selected.size >= DEFAULT_PINNED_COUNT) break
            if (app.packageName !in usedPackages && !app.isKnownDistracting) {
                selected.add(app)
                usedPackages.add(app.packageName)
            }
        }

        for (app in installedApps) {
            if (selected.size >= DEFAULT_PINNED_COUNT) break
            if (app.packageName !in usedPackages) {
                selected.add(app)
                usedPackages.add(app.packageName)
            }
        }

        return selected.map { it.packageName }
    }

    fun launchApp(app: AppEntry): Boolean {
        val component = ComponentName(app.packageName, app.activityName)
        val launchedViaLauncherApps = runCatching {
            launcherApps?.startMainActivity(component, Process.myUserHandle(), null, null)
            true
        }.getOrDefault(false)

        if (launchedViaLauncherApps) return true

        val fallbackIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                this.component = component
            }
        fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            context.startActivity(fallbackIntent)
            true
        }.getOrDefault(false)
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun isDefaultHomeLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    fun openDefaultHomeSettings() {
        val homeSettingsIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching {
            context.startActivity(homeSettingsIntent)
            true
        }.getOrDefault(false)

        if (!started && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                runCatching {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
    }

    private fun isKnownDistractingApp(packageName: String, label: String): Boolean {
        val pkgLower = packageName.lowercase()
        val labelLower = label.lowercase()
        return DISTRACTING_PACKAGE_FRAGMENTS.any { pkgLower.contains(it) } ||
            DISTRACTING_LABELS.any { labelLower == it || labelLower.contains(it) }
    }

    companion object {
        private const val DEFAULT_PINNED_COUNT = 6

        private val ESSENTIAL_APP_PATTERNS = listOf(
            listOf("dialer", "telecom", "phone"),
            listOf("messaging", "mms", "messages"),
            listOf("chrome", "browser", "firefox", "duckduckgo"),
            listOf("gm", "gmail", "email", "mail"),
            listOf("keep", "notes", "obsidian", "notion"),
            listOf("camera"),
            listOf("clock", "deskclock"),
            listOf("maps"),
            listOf("calendar")
        )

        private val DISTRACTING_PACKAGE_FRAGMENTS = listOf(
            "instagram",
            "musically",
            "zhiliaoapp",
            "tiktok",
            "youtube",
            "twitter",
            "reddit",
            "facebook",
            "katana",
            "snapchat",
            "barcelona", // Threads
            "pinterest",
            "discord",
            "twitch",
            "netflix",
            "tumblr"
        )

        private val DISTRACTING_LABELS = listOf(
            "instagram",
            "tiktok",
            "youtube",
            "twitter",
            "reddit",
            "facebook",
            "snapchat",
            "threads",
            "pinterest",
            "discord",
            "twitch",
            "netflix"
        )
    }
}
