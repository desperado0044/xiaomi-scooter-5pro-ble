package com.scooterre.client.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

/**
 * The "new insurance applied for" button of a reminder notification: ticks all scooters off for the
 * plate period named in the notification (so no further reminders come) and removes the notification.
 * A test notification only dismisses itself.
 */
class InsuranceActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != InsuranceReminders.ACTION_APPLIED) return
        if (!intent.getBooleanExtra(InsuranceReminders.EXTRA_TEST, false)) {
            val expiry = intent.getStringExtra(InsuranceReminders.EXTRA_EXPIRY)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
            InsuranceReminders.markAllApplied(context, expiry)
        }
        InsuranceReminders.dismiss(context)
    }
}
