package bukin.labadb

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/** The one system setting this app reads and writes: Wireless debugging on or off. */
object AdbWifiSetting {

    /** Settings.Global.ADB_WIFI_ENABLED. Hidden from the SDK, but marked readable for apps. */
    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

    fun isPermissionGranted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** The current value (0 = off), or null when Android doesn't let the app read it. */
    fun read(resolver: ContentResolver): Int? = try {
        Settings.Global.getInt(resolver, ADB_WIFI_ENABLED, 0)
    } catch (e: SecurityException) {
        null
    }

    /**
     * THE ONLY PLACE IN THE APP THAT WRITES A SYSTEM SETTING. It writes this one key, and only the
     * value 1. Nothing in the app ever writes 0: Android does that itself when the phone leaves
     * Wi-Fi. Throws SecurityException when the permission is missing.
     */
    fun switchOn(resolver: ContentResolver) {
        Settings.Global.putInt(resolver, ADB_WIFI_ENABLED, 1)
    }
}
