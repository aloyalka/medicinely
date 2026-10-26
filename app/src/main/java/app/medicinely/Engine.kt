package app.medicinely

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** All the scheduling rules live here. */
object Engine {
    const val MIN = 60_000L
    const val NUDGE_MS = 5 * MIN
    const val EDGE_MS = 5 * MIN
    const val TYPE_DOSE = 0
    const val TYPE_NUDGE = 1
    const val ACTION_FIRE = "app.medicinely.FIRE"
    const val ACTION_TAKEN = "app.medicinely.TAKEN"
    const val ACTION_DELAY = "app.medicinely.DELAY"
    const val ACTION_AWAKE = "app.medicinely.AWAKE"
    const val EXTRA_MIN = "minutes"

    class Event(val time: Long, val type: Int, val sleep: Boolean)

    // ---------- time helpers ----------

    fun dayStart(t: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = t
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun at(day: Long, minutes: Int, addDays: Int = 0): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = day
        c.add(Calendar.DAY_OF_YEAR, addDays)
        c.set(Calendar.HOUR_OF_DAY, minutes / 60); c.set(Calendar.MINUTE, minutes % 60)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Awake window (wake, sleep) for the day [offset] days from [day]. Handles sleep after midnight. */
    fun window(p: Prefs, day: Long, offset: Int): LongArray {
        val s = at(day, p.wakeMin, offset)
        var e = at(day, p.sleepMin, offset)
        if (e <= s) e = at(day, p.sleepMin, offset + 1)
        return longArrayOf(s, e)
    }

    fun fmt(t: Long): String = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(t)).lowercase()
    fun fmtMin(m: Int): String = fmt(at(dayStart(System.currentTimeMillis()), m))

    fun fmtDay(t: Long): String {
        val today = dayStart(System.currentTimeMillis())
        val d = dayStart(t)
        return when {
            d == today -> fmt(t)
            d == at(today, 0, 1) -> "Tomorrow, " + fmt(t)
            else -> SimpleDateFormat("EEE", Locale.ENGLISH).format(Date(t)) + ", " + fmt(t)
        }
    }

    fun isToday(t: Long): Boolean = dayStart(t) == dayStart(System.currentTimeMillis())

    fun duration(ms: Long): String {
        val mins = ((ms + MIN - 1) / MIN).toInt()
        val h = mins / 60
        val m = mins % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h hr"
            else -> "$h hr $m min"
        }
    }

    fun span(interval: Int): String = when (interval) {
        15 -> "15 minutes"
        60 -> "1 hour"
        180 -> "3 hours"
        else -> "$interval minutes"
    }

    // ---------- the schedule ----------

    /** Next repeating dose: one interval after [anchor], kept inside waking hours, plus the before-sleep dose. */
    fun intervalDose(p: Prefs, now: Long, interval: Int, anchor: Long): Event {
        val earliest = now + 5_000L
        var c = maxOf(anchor + interval * MIN, earliest)
        val today = dayStart(now)
        for (i in -1..3) {
            val w = window(p, today, i)
            val first = w[0] + EDGE_MS
            val last = w[1] - EDGE_MS
            if (last < earliest) continue
            val sleepDone = p.lastSleepDoseMs >= first
            if (c >= last - EDGE_MS) {
                // The regular dose would land at or after bedtime: the before-sleep dose comes first.
                if (!sleepDone) return Event(maxOf(last, earliest), TYPE_DOSE, true)
                continue
            }
            if (c < first) c = first
            return Event(c, TYPE_DOSE, false)
        }
        return Event(earliest + interval * MIN, TYPE_DOSE, false)
    }

    /** Next of the two fixed daily times. */
    fun dayNightDose(p: Prefs, now: Long, dayMin: Int, nightMin: Int): Event {
        val today = dayStart(now)
        var best = Long.MAX_VALUE
        for (d in 0..1) for (m in intArrayOf(dayMin, nightMin)) {
            val t = at(today, m, d)
            if (t > now + 1_000L && t > p.lastFireMs + 1_000L && t < best) best = t
        }
        return Event(best, TYPE_DOSE, false)
    }

    /** The next dose (ignoring nudges), or null if the course has ended. */
    fun nextDose(p: Prefs, now: Long): Event? {
        if (p.mode == Mode.NONE) return null
        if (p.delayUntilMs > 0) return Event(maxOf(p.delayUntilMs, now + 1_000L), TYPE_DOSE, p.currentIsSleep)
        val d = if (p.mode == Mode.INTERVAL) intervalDose(p, now, p.intervalMin, p.anchorMs)
                else dayNightDose(p, now, p.dayMin, p.nightMin)
        return if (p.endMs == 0L || d.time <= p.endMs) d else null
    }

    fun computeNext(p: Prefs, now: Long): Event? {
        if (p.mode == Mode.NONE) return null
        var best: Event? = null
        if (p.nudgeAtMs > now) best = Event(p.nudgeAtMs, TYPE_NUDGE, p.currentIsSleep)
        val d = nextDose(p, now)
        if (d != null) {
            val b = best
            if (b == null || d.time < b.time) best = d
        }
        return best
    }

    private fun firePending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Works out the next event and sets exactly one alarm for it. Safe to call any time. */
    fun reschedule(ctx: Context) {
        val p = Prefs(ctx)
        val now = System.currentTimeMillis()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = firePending(ctx)
        am.cancel(pi)
        val ev = computeNext(p, now)
        if (ev == null) {
            if (p.mode != Mode.NONE) {
                p.addLog(now, "Course finished")
                clear(p)
            }
            p.nextAt = 0L
            return
        }
        p.nextAt = ev.time
        p.nextType = ev.type
        p.nextSleep = ev.sleep
        val show = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(ev.time, show), pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ev.time, pi)
        }
    }

    /** Called when the alarm goes off. Returns true if a chime should play. */
    fun onFire(ctx: Context): Boolean {
        val p = Prefs(ctx)
        val now = System.currentTimeMillis()
        if (p.mode == Mode.NONE || p.nextAt == 0L || p.nextAt - now > 60_000L) {
            reschedule(ctx)
            return false
        }
        var alert = false
        var nudge = false
        if (p.nextType == TYPE_DOSE) {
            val delayed = p.delayUntilMs > 0
            if (!delayed) {
                if (p.pendingDueMs > 0) {
                    // The previous dose was never marked taken.
                    p.addLog(p.pendingDueMs, "Missed")
                    p.missedFlag = true
                    p.missedAtMs = p.pendingDueMs
                }
                p.pendingDueMs = now
                p.currentIsSleep = p.nextSleep
                p.anchorMs = now
                p.lastFireMs = p.nextAt
                if (p.nextSleep) p.lastSleepDoseMs = now
            }
            p.delayUntilMs = 0L
            p.nudgeAtMs = now + NUDGE_MS
            alert = true
        } else {
            p.nudgeAtMs = 0L
            if (p.pendingDueMs > 0) {
                alert = true
                nudge = true
            }
        }
        reschedule(ctx)
        if (!alert) return false
        val napping = p.napUntilMs > now
        Alerts.show(ctx, nudge, napping)
        return !napping
    }

    // ---------- actions ----------

    fun taken(ctx: Context) {
        val p = Prefs(ctx)
        val now = System.currentTimeMillis()
        p.anchorMs = now
        p.lastTakenMs = now
        p.pendingDueMs = 0L
        p.nudgeAtMs = 0L
        p.delayUntilMs = 0L
        p.missedFlag = false
        p.addLog(now, "Taken")
        Alerts.cancel(ctx)
        reschedule(ctx)
    }

    fun delay(ctx: Context, minutes: Int) {
        val p = Prefs(ctx)
        val now = System.currentTimeMillis()
        p.delayUntilMs = now + minutes * MIN
        p.nudgeAtMs = 0L
        p.addLog(now, "Delayed $minutes min")
        Alerts.cancel(ctx)
        reschedule(ctx)
    }

    fun start(ctx: Context, mode: Int, interval: Int) {
        val p = Prefs(ctx)
        val now = System.currentTimeMillis()
        val wasNone = p.mode == Mode.NONE
        p.mode = mode
        if (interval > 0) p.intervalMin = interval
        if (wasNone) {
            p.anchorMs = now
            p.pendingDueMs = 0L
            p.nudgeAtMs = 0L
            p.delayUntilMs = 0L
            p.missedFlag = false
            p.lastSleepDoseMs = 0L
            p.lastFireMs = 0L
            p.endMs = if (p.courseDays > 0) window(p, dayStart(now), p.courseDays - 1)[1] else 0L
            p.addLog(now, "Started")
        }
        reschedule(ctx)
    }

    private fun clear(p: Prefs) {
        p.mode = Mode.NONE
        p.pendingDueMs = 0L
        p.nudgeAtMs = 0L
        p.delayUntilMs = 0L
        p.missedFlag = false
        p.endMs = 0L
    }

    fun stop(ctx: Context) {
        val p = Prefs(ctx)
        clear(p)
        p.addLog(System.currentTimeMillis(), "Stopped")
        Alerts.cancel(ctx)
        reschedule(ctx)
    }

    fun setCourse(ctx: Context, days: Int) {
        val p = Prefs(ctx)
        p.courseDays = days
        if (p.mode != Mode.NONE) {
            p.endMs = if (days > 0) window(p, dayStart(System.currentTimeMillis()), days - 1)[1] else 0L
        }
        reschedule(ctx)
    }

    fun startNap(ctx: Context, minutes: Int) {
        Prefs(ctx).napUntilMs = System.currentTimeMillis() + minutes * MIN
    }

    fun extendNap(ctx: Context) {
        val p = Prefs(ctx)
        p.napUntilMs = maxOf(p.napUntilMs, System.currentTimeMillis()) + 30 * MIN
    }

    fun awake(ctx: Context) {
        Prefs(ctx).napUntilMs = 0L
    }
}
