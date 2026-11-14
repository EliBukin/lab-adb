package bukin.labadb

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import bukin.labadb.core.ConfigCheck
import bukin.labadb.core.EventLog
import bukin.labadb.core.HintTarget
import bukin.labadb.core.LabConfig
import bukin.labadb.core.Parsed

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

    /** What the app last did with the instant trigger, so only a change gets an event. */
    var instantTriggerArmed: Boolean
        get() = prefs.getBoolean(KEY_INSTANT_TRIGGER_ARMED, false)
        set(value) = prefs.edit().putBoolean(KEY_INSTANT_TRIGGER_ARMED, value).apply()

    /** The "ANXEL address" field as saved. Empty (the default) means hints are off. */
    val hintTargetText: String get() = prefs.getString(KEY_HINT_TARGET, null).orEmpty()

    /** Where hints go, or null when not set (or unreadable): then nothing is sent. */
    fun hintTarget(): HintTarget? = (HintTarget.parse(hintTargetText) as? Parsed.Ok)?.value

    fun saveHintTarget(target: HintTarget?) {
        putTexts(KEY_HINT_TARGET to (target?.toString() ?: ""))
    }

    /** When the last hint packet went out (wall clock), or null for never. */
    val lastHintSentMillis: Long? get() = getHandle(KEY_HINT_SENT_AT)

    /** The port in the last hint packet (0 = unknown) and the connection it went out on. */
    val lastHintPort: Int get() = prefs.getInt(KEY_HINT_PORT, 0)
    val lastHintHandle: Long? get() = getHandle(KEY_HINT_HANDLE)

    fun recordHintSent(timeMillis: Long, handle: Long, port: Int) {
        prefs.edit()
            .putLong(KEY_HINT_SENT_AT, timeMillis)
            .putLong(KEY_HINT_HANDLE, handle)
            .putInt(KEY_HINT_PORT, port)
            .apply()
    }

    /** How the last hint run ended, so a heartbeat is listed only when that changes. */
    var lastHintOutcome: String?
        get() = prefs.getString(KEY_HINT_OUTCOME, null)
        set(value) = prefs.edit().putString(KEY_HINT_OUTCOME, value).apply()

    /** After a restart Android numbers networks from scratch, so an old handle means nothing. */
    fun forgetFailedConnection() {
        failedHandle = null
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
        private const val KEY_INSTANT_TRIGGER_ARMED = "instant_trigger_armed"
        private const val KEY_EVENTS = "events"
        private const val KEY_HINT_TARGET = "hint_target"
        private const val KEY_HINT_SENT_AT = "hint_sent_at"
        private const val KEY_HINT_PORT = "hint_port"
        private const val KEY_HINT_HANDLE = "hint_handle"
        private const val KEY_HINT_OUTCOME = "hint_outcome"
        private val LOCK = Any()
    }
}
