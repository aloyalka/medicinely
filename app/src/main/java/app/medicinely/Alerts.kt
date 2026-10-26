package app.medicinely

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

/** Notifications and the full-screen reminder. */
object Alerts {
    private const val CH_REMIND = "reminders"
    private const val CH_SILENT = "silent"
    private const val ID = 1

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val r = NotificationChannel(CH_REMIND, "Medicine reminders", NotificationManager.IMPORTANCE_HIGH)
        r.setSound(null, null) // the chime is played separately on the alarm channel
        r.enableVibration(true)
        r.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(r)
        val s = NotificationChannel(CH_SILENT, "Reminders during a nap", NotificationManager.IMPORTANCE_LOW)
        s.setSound(null, null)
        s.enableVibration(false)
        nm.createNotificationChannel(s)
    }

    private fun broadcast(ctx: Context, code: Int, action: String, minutes: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, AlarmReceiver::class.java).setAction(action).putExtra(Engine.EXTRA_MIN, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun show(ctx: Context, nudge: Boolean, silent: Boolean) {
        channels(ctx)
        val p = Prefs(ctx)
        val text = when {
            p.currentIsSleep -> "Before-sleep dose"
            p.missedFlag -> "You may have missed a dose"
            nudge -> "Your dose isn't marked taken yet"
            silent -> "Dose due during your nap"
            else -> "Tap Taken once you've had it"
        }
        val open = PendingIntent.getActivity(
            ctx, 2,
            Intent(ctx, ReminderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = Icon.createWithResource(ctx, R.drawable.ic_stat)
        val b = Notification.Builder(ctx, if (silent) CH_SILENT else CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Medicine Time")
            .setContentText(text)
            .setColor(if (p.currentIsSleep) C.NIGHT else C.TEAL)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setAutoCancel(false)
            .addAction(Notification.Action.Builder(icon, "Taken", broadcast(ctx, 3, Engine.ACTION_TAKEN)).build())
            .addAction(Notification.Action.Builder(icon, "In 15 min", broadcast(ctx, 4, Engine.ACTION_DELAY, 15)).build())
        if (silent) {
            b.addAction(Notification.Action.Builder(icon, "I'm awake", broadcast(ctx, 5, Engine.ACTION_AWAKE)).build())
        } else {
            b.setFullScreenIntent(open, true)
        }
        ctx.getSystemService(NotificationManager::class.java).notify(ID, b.build())
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
