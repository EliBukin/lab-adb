package bukin.labadb

import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import bukin.labadb.core.ConfigCheck
import bukin.labadb.core.Event
import bukin.labadb.core.LabConfig
import bukin.labadb.core.LabMatcher
import bukin.labadb.core.MatchResult
import bukin.labadb.core.Trigger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The one screen: the switch, the lab subnet, status lines, Test now and the event list.
 * It never reads the Intent that started it, so other apps can't pass it anything.
 */
class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var enabledSwitch: Switch
    private lateinit var subnetField: EditText
    private lateinit var gatewayField: EditText
    private lateinit var formMessage: TextView
    private lateinit var testButton: Button
    private lateinit var timeFormat: DateTimeFormatter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = Store(this)

        // Edge to edge on every Android version (Android 15+ forces it; older ones opt in here),
        // so one inset rule keeps the content clear of the system bars, cutout and keyboard.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
        }
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
            val bars = insets.getInsets(types)
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }

        enabledSwitch = findViewById(R.id.enabled_switch)
        subnetField = findViewById(R.id.subnet_field)
        gatewayField = findViewById(R.id.gateway_field)
        formMessage = findViewById(R.id.form_message)
        testButton = findViewById(R.id.test_button)

        if (savedInstanceState == null) {
            subnetField.setText(store.subnetText)
            gatewayField.setText(store.gatewayText)
        }
        enabledSwitch.isChecked = store.enabled
        enabledSwitch.setOnCheckedChangeListener { _, isChecked -> onSwitchChanged(isChecked) }
        findViewById<Button>(R.id.save_button).setOnClickListener { onSave() }
        testButton.setOnClickListener { runCheck(Trigger.TEST_NOW, manual = true) }
        findViewById<Button>(R.id.app_settings_button).setOnClickListener { openAppSettings() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (Arming.sync(this).armed) runCheck(Trigger.APP_OPENED)
    }

    private fun onSwitchChanged(isChecked: Boolean) {
        if (isChecked == store.enabled) return
        if (isChecked && store.labConfig() == null) {
            enabledSwitch.isChecked = false
            showFormMessage(getString(R.string.save_subnet_first), isError = true)
            return
        }
        store.enabled = isChecked
        Arming.sync(this)
        if (isChecked) {
            runCheck(Trigger.SWITCHED_ON)
        } else {
            store.log(getString(R.string.setting_switch_off))
            refreshStatus()
        }
    }

    private fun onSave() {
        subnetField.error = null
        gatewayField.error = null
        when (val check = LabConfig.parse(subnetField.text.toString(), gatewayField.text.toString())) {
            is ConfigCheck.Invalid -> {
                check.subnetError?.let { subnetField.error = it }
                check.gatewayError?.let { gatewayField.error = it }
                showFormMessage(listOfNotNull(check.subnetError, check.gatewayError).joinToString("\n"), isError = true)
            }
            is ConfigCheck.Valid -> {
                val config = check.config
                store.saveLab(config)
                subnetField.setText(config.subnet.toString())
                gatewayField.setText(config.gatewayText)
                val saved = if (config.gateway == null) {
                    getString(R.string.saved, config.subnet.toString())
                } else {
                    getString(R.string.saved_with_gateway, config.subnet.toString(), config.gatewayText)
                }
                showFormMessage(saved, isError = false)
                hideKeyboard()
                if (Arming.sync(this).armed) {
                    runCheck(Trigger.SETTINGS_SAVED)
                } else {
                    store.log(getString(R.string.setting_saved_log))
                    refreshStatus()
                }
            }
        }
    }

    private fun runCheck(trigger: Trigger, manual: Boolean = false) {
        testButton.isEnabled = false
        val app = applicationContext
        Worker.execute {
            try {
                Checker(app).run(trigger, manual = manual)
            } finally {
                runOnUiThread {
                    if (!isDestroyed) {
                        testButton.isEnabled = true
                        refreshStatus()
                    }
                }
            }
        }
    }

    private fun refreshStatus() {
        val granted = AdbWifiSetting.isPermissionGranted(this)
        setStatus(R.id.status_permission, granted, R.string.status_permission_ok, R.string.status_permission_missing)
        findViewById<View>(R.id.grant_command).visibility = if (granted) View.GONE else View.VISIBLE

        val config = store.labConfig()
        val wifi = WifiNetworks.connected(getSystemService(ConnectivityManager::class.java))
        val onLab = config != null && wifi.any { LabMatcher.match(config, it) == MatchResult.MATCH }
        val labText = when {
            onLab -> R.string.status_lab_ok
            config == null -> R.string.status_lab_no_subnet
            wifi.isEmpty() -> R.string.status_lab_no_wifi
            else -> R.string.status_lab_other
        }
        setStatus(R.id.status_lab, onLab, labText, labText)

        when (AdbWifiSetting.read(contentResolver)) {
            null -> setStatus(R.id.status_adb, false, R.string.status_adb_unknown, R.string.status_adb_unknown)
            0 -> setStatus(R.id.status_adb, false, R.string.status_adb_on, R.string.status_adb_off)
            else -> setStatus(R.id.status_adb, true, R.string.status_adb_on, R.string.status_adb_off)
        }

        val unrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        setStatus(R.id.status_battery, unrestricted, R.string.status_battery_ok, R.string.status_battery_bad)

        // "Whitelisted" here means the app is exempt: "Pause app activity if unused" is off.
        val pauseOff = packageManager.isAutoRevokeWhitelisted
        setStatus(R.id.status_pause, pauseOff, R.string.status_pause_ok, R.string.status_pause_bad)

        val events = store.events().newestFirst()
        // Built on each refresh, so it follows a locale or time zone change.
        timeFormat = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())
        findViewById<TextView>(R.id.events).text =
            if (events.isEmpty()) getString(R.string.events_empty) else events.joinToString("\n", transform = ::eventLine)
    }

    private fun setStatus(viewId: Int, ok: Boolean, okText: Int, badText: Int) {
        val view = findViewById<TextView>(viewId)
        view.setText(if (ok) okText else badText)
        view.setTextColor(getColor(if (ok) R.color.status_ok else R.color.status_bad))
    }

    private fun eventLine(event: Event): String {
        val time = timeFormat.format(Instant.ofEpochMilli(event.timeMillis))
        val repeats = if (event.count > 1) " (×${event.count})" else ""
        return "$time  ${event.message}$repeats"
    }

    private fun showFormMessage(text: String, isError: Boolean) {
        formMessage.text = text
        formMessage.setTextColor(getColor(if (isError) R.color.status_bad else R.color.status_ok))
        formMessage.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        currentFocus?.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(subnetField.windowToken, 0)
    }

    /** The app's page in system settings, for Battery and "Pause app activity if unused". */
    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
}
