package bukin.labadb

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import bukin.labadb.core.ConfigCheck
import bukin.labadb.core.EventLog
import bukin.labadb.core.LabConfig

/**
 * The app's own state, in its private SharedPreferences (credential-encrypted storage: the app
 * acts only after the first unlock). These are app preferences, not system settings.
 */
class Store(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The in-app switch. Off by default. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    val subnetText: String get() = prefs.getString(KEY_SUBNET, null).orEmpty()
    val gatewayText: String get() = prefs.getString(KEY_GATEWAY, null).orEmpty()

    /** The saved lab settings, or null when missing or invalid: then the app does nothing. */
    fun labConfig(): LabConfig? = (LabConfig.parse(subnetText, gatewayText) as? ConfigCheck.Valid)?.config

    fun saveLab(config: LabConfig) {
        putTexts(KEY_SUBNET to config.subnet.toString(), KEY_GATEWAY to config.gatewayText)
    }

    /** The connection (Network.networkHandle) where the last switch-on didn't stick. */
    var failedHandle: Long?
        get() = getHandle(KEY_FAILED_HANDLE)
        set(value) = setHandle(KEY_FAILED_HANDLE, value)

    /** The Wi-Fi connection the last check looked at, to tell a new join from a re-sent one. */
    var lastSeenHandle: Long?
        get() = getHandle(KEY_LAST_SEEN_HANDLE)
        set(value) = setHandle(KEY_LAST_SEEN_HANDLE, value)

    /** After a restart Android numbers networks from scratch, so old handles mean nothing. */
    fun forgetConnections() {
        prefs.edit().remove(KEY_FAILED_HANDLE).remove(KEY_LAST_SEEN_HANDLE).apply()
    }

    fun events(): EventLog = synchronized(LOCK) {
        EventLog.decode(prefs.getString(KEY_EVENTS, null).orEmpty())
    }

    /** Adds an event to the in-app list and to logcat (tag LabAdb). Never pass addresses here. */
    fun log(message: String) {
        Log.i(TAG, message)
        synchronized(LOCK) {
            val log = events()
            log.add(System.currentTimeMillis(), message)
            putTexts(KEY_EVENTS to log.encode())
        }
    }

    private fun getHandle(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null

    private fun setHandle(key: String, value: Long?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key) else editor.putLong(key, value)
        editor.apply()
    }

    /** Text values go through here. App-private preferences only, never a system setting. */
    private fun putTexts(vararg entries: Pair<String, String>) {
        val editor = prefs.edit()
        for ((key, value) in entries) editor.putString(key, value)
        editor.apply()
    }

    companion object {
        const val TAG = "LabAdb"
        private const val FILE = "lab_adb"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SUBNET = "subnet"
        private const val KEY_GATEWAY = "gateway"
        private const val KEY_FAILED_HANDLE = "failed_handle"
        private const val KEY_LAST_SEEN_HANDLE = "last_seen_handle"
        private const val KEY_EVENTS = "events"
        private val LOCK = Any()
    }
}
