package com.scooterre.client.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scooterre.client.MainActivity
import com.scooterre.client.R
import com.scooterre.client.protocol.DeviceRegistry
import com.scooterre.client.ui.Lang
import com.scooterre.client.ui.modelDisplayName
import com.scooterre.client.ui.resolveLang
import com.scooterre.client.ui.strings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Insurance-plate reminders as normal notifications (no foreground service, no connection to the
 * scooter needed): a daily background job checks the fixed schedule of [InsuranceSchedule] and
 * notifies once per stage, for every scooter that is not yet ticked off as "new insurance applied
 * for". The tick is stored together with the plate period it was given for, so the reminders start
 * again by themselves the next year.
 */
object InsuranceReminders {
    private const val PREFS = "scooter_prefs"
    const val KEY_ENABLED = "insurance_reminder"
    private const val KEY_NOTIFIED = "insurance_notified" // "<expiry date>:<stage ordinal>"
    private const val KEY_APPLIED_PREFIX = "insurance_applied_" // + MAC -> expiry date the box was ticked for
    private const val KEY_LANG = "lang"
    private const val CHANNEL_ID = "insurance"
    private const val NOTIFICATION_ID = 4711
    private const val WORK_NAME = "insurance-reminder"
    const val EXTRA_OPEN = "open"
    const val EXTRA_MAC = "mac"
    const val OPEN_DOCUMENTS = "documents"
    const val ACTION_APPLIED = "com.scooterre.client.INSURANCE_APPLIED"
    const val EXTRA_EXPIRY = "expiry"
    const val EXTRA_TEST = "test"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) schedule(context, replace = true) else cancel(context)
    }

    fun isApplied(context: Context, mac: String, expiry: LocalDate) =
        prefs(context).getString(KEY_APPLIED_PREFIX + mac, null) == expiry.toString()

    fun setApplied(context: Context, mac: String, expiry: LocalDate, applied: Boolean) {
        prefs(context).edit().apply {
            if (applied) putString(KEY_APPLIED_PREFIX + mac, expiry.toString()) else remove(KEY_APPLIED_PREFIX + mac)
        }.apply()
    }

    /** Ticks every known scooter off for the plate period ending on [expiry] (the notification's button). */
    fun markAllApplied(context: Context, expiry: LocalDate) {
        DeviceRegistry(context).list().forEach { setApplied(context, it.mac, expiry, true) }
    }

    fun dismiss(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    /** Runs every day at about 9:00 (the phone may shift it a little to save battery). */
    fun schedule(context: Context, replace: Boolean) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(9, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<InsuranceWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun lastNotified(context: Context, expiry: LocalDate): InsuranceSchedule.Stage {
        val parts = prefs(context).getString(KEY_NOTIFIED, null)?.split(":") ?: return InsuranceSchedule.Stage.NONE
        if (parts.size != 2 || parts[0] != expiry.toString()) return InsuranceSchedule.Stage.NONE
        return InsuranceSchedule.Stage.values().getOrNull(parts[1].toIntOrNull() ?: 0) ?: InsuranceSchedule.Stage.NONE
    }

    /** Notifies if a new stage is due for scooters that are not ticked off yet. Returns true if a notification went out. */
    fun checkAndNotify(context: Context, today: LocalDate = LocalDate.now()): Boolean {
        if (!isEnabled(context)) return false
        val expiry = InsuranceSchedule.expiryFor(today)
        val pending = DeviceRegistry(context).list().filter { !isApplied(context, it.mac, expiry) }
        if (pending.isEmpty()) return false
        val stage = InsuranceSchedule.stageToNotify(today, lastNotified(context, expiry)) ?: return false
        if (!canNotify(context)) return false // not recorded, so it is tried again tomorrow
        val lang = resolveLang(prefs(context).getString(KEY_LANG, null))
        val names = pending.joinToString(", ") { it.name ?: modelDisplayName(it.model, lang) }
        post(context, stage, expiry, names, pending.first().mac, lang, test = false)
        prefs(context).edit().putString(KEY_NOTIFIED, "$expiry:${stage.ordinal}").apply()
        return true
    }

    /** A sample notification so the user can see that notifications work on this phone. */
    fun postTest(context: Context): Boolean {
        if (!canNotify(context)) return false
        val lang = resolveLang(prefs(context).getString(KEY_LANG, null))
        val registry = DeviceRegistry(context).list()
        val names = registry.joinToString(", ") { it.name ?: modelDisplayName(it.model, lang) }.ifEmpty { "-" }
        post(context, InsuranceSchedule.Stage.WEEK, InsuranceSchedule.expiryFor(LocalDate.now()), names, registry.firstOrNull()?.mac, lang, test = true)
        return true
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun post(context: Context, stage: InsuranceSchedule.Stage, expiry: LocalDate, names: String, mac: String?, lang: Lang, test: Boolean) {
        val s = strings(lang)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, s.insuranceChannelName, NotificationManager.IMPORTANCE_DEFAULT))
        val locale = if (lang == Lang.DE) Locale.GERMANY else Locale.US
        val date = expiry.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_DOCUMENTS)
            .apply { if (mac != null) putExtra(EXTRA_MAC, mac) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val applied = Intent(context, InsuranceActionReceiver::class.java)
            .setAction(ACTION_APPLIED)
            .putExtra(EXTRA_EXPIRY, expiry.toString())
            .putExtra(EXTRA_TEST, test)
        val appliedPending = PendingIntent.getBroadcast(context, 1, applied, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = (if (test) s.insuranceTestPrefix else "") + s.insuranceNotifTitle(stage)
        val text = s.insuranceNotifText(date, names)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).build(),
            )
            .addAction(0, s.insuranceActionApplied, appliedPending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // permission revoked between the check and the call - nothing to do
        }
    }
}

class InsuranceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        InsuranceReminders.checkAndNotify(applicationContext)
        return Result.success()
    }
}
