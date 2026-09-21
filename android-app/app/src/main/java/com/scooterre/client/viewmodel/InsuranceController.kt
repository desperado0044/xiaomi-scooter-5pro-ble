package com.scooterre.client.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import com.scooterre.client.protocol.*
import com.scooterre.client.reminder.InsuranceReminders
import com.scooterre.client.reminder.InsuranceSchedule
import com.scooterre.client.ui.*
import com.scooterre.client.update.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update

/** The optional insurance-plate reminders: switch, per-scooter tick and the test notification. */
internal class InsuranceController(
    private val shared: Shared,
    private val deviceRegistry: DeviceRegistry,
) {
    private val _state get() = shared.state
    private val prefs get() = shared.prefs
    private val s get() = shared.s
    private val app get() = shared.app
    private val scope get() = shared.scope

    fun insuranceAppliedNow(): Set<String> {
        val expiry = InsuranceSchedule.expiryFor(java.time.LocalDate.now())
        return deviceRegistry.list().filter { InsuranceReminders.isApplied(app, it.mac, expiry) }.map { it.mac }.toSet()
    }

    /** Re-reads the ticked-off scooters (the notification's button changes them while the app is closed). */
    fun refreshInsuranceState() {
        _state.update { it.copy(insuranceApplied = insuranceAppliedNow(), insuranceExpiry = InsuranceSchedule.expiryFor(java.time.LocalDate.now())) }
    }

    fun setInsuranceReminder(enabled: Boolean) {
        InsuranceReminders.setEnabled(app, enabled)
        _state.update {
            it.copy(
                insuranceReminder = enabled,
                insuranceExpiry = InsuranceSchedule.expiryFor(java.time.LocalDate.now()),
                insuranceApplied = insuranceAppliedNow(),
            )
        }
        // Switching it on inside the reminder window should not wait for tomorrow's job.
        if (enabled) scope.launch(Dispatchers.IO) { InsuranceReminders.checkAndNotify(app) }
    }

    fun setInsuranceApplied(mac: String, applied: Boolean) {
        val expiry = InsuranceSchedule.expiryFor(java.time.LocalDate.now())
        InsuranceReminders.setApplied(app, mac, expiry, applied)
        _state.update { it.copy(insuranceExpiry = expiry, insuranceApplied = insuranceAppliedNow()) }
    }

    /** True if the sample notification went out (false: notifications are blocked for the app). */
    fun sendInsuranceTest(): Boolean = InsuranceReminders.postTest(app)
}
