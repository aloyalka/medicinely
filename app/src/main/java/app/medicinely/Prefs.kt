package app.medicinely

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Mode {
    const val NONE = 0
    const val INTERVAL = 1
    const val DAYNIGHT = 2
}

/** Everything Medicinely remembers, stored on the phone. */
class Prefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("medicinely", Context.MODE_PRIVATE)

    private fun gi(k: String, d: Int): Int = p.getInt(k, d)
    private fun si(k: String, v: Int) { p.edit().putInt(k, v).commit() }
    private fun gl(k: String): Long = p.getLong(k, 0L)
    private fun sl(k: String, v: Long) { p.edit().putLong(k, v).commit() }
    private fun gb(k: String): Boolean = p.getBoolean(k, false)
    private fun sb(k: String, v: Boolean) { p.edit().putBoolean(k, v).commit() }

    var setupDone: Boolean
        get() = gb("setupDone")
        set(v) = sb("setupDone", v)
    var wakeMin: Int
        get() = gi("wakeMin", 600)
        set(v) = si("wakeMin", v)
    var sleepMin: Int
        get() = gi("sleepMin", 1380)
        set(v) = si("sleepMin", v)
    var mode: Int
        get() = gi("mode", Mode.NONE)
        set(v) = si("mode", v)
    var intervalMin: Int
        get() = gi("intervalMin", 60)
        set(v) = si("intervalMin", v)
    var dayMin: Int
        get() = gi("dayMin", 605)
        set(v) = si("dayMin", v)
    var nightMin: Int
        get() = gi("nightMin", 1375)
        set(v) = si("nightMin", v)
    var courseDays: Int
        get() = gi("courseDays", 0)
        set(v) = si("courseDays", v)
    var chime: Int
        get() = gi("chime", 0)
        set(v) = si("chime", v)
    var volume: Int
        get() = gi("volume", 70)
        set(v) = si("volume", v)

    /** The clock for the next dose counts from here. */
    var anchorMs: Long
        get() = gl("anchorMs")
        set(v) = sl("anchorMs", v)
    var lastTakenMs: Long
        get() = gl("lastTakenMs")
        set(v) = sl("lastTakenMs", v)
    var endMs: Long
        get() = gl("endMs")
        set(v) = sl("endMs", v)
    var napUntilMs: Long
        get() = gl("napUntilMs")
        set(v) = sl("napUntilMs", v)
    var delayUntilMs: Long
        get() = gl("delayUntilMs")
        set(v) = sl("delayUntilMs", v)
    /** A dose that chimed and hasn't been marked taken yet. */
    var pendingDueMs: Long
        get() = gl("pendingDueMs")
        set(v) = sl("pendingDueMs", v)
    var nudgeAtMs: Long
        get() = gl("nudgeAtMs")
        set(v) = sl("nudgeAtMs", v)
    var missedFlag: Boolean
        get() = gb("missedFlag")
        set(v) = sb("missedFlag", v)
    var missedAtMs: Long
        get() = gl("missedAtMs")
        set(v) = sl("missedAtMs", v)
    var currentIsSleep: Boolean
        get() = gb("currentIsSleep")
        set(v) = sb("currentIsSleep", v)
    var lastSleepDoseMs: Long
        get() = gl("lastSleepDoseMs")
        set(v) = sl("lastSleepDoseMs", v)
    var lastFireMs: Long
        get() = gl("lastFireMs")
        set(v) = sl("lastFireMs", v)

    /** The one alarm currently set with the phone. */
    var nextAt: Long
        get() = gl("nextAt")
        set(v) = sl("nextAt", v)
    var nextType: Int
        get() = gi("nextType", 0)
        set(v) = si("nextType", v)
    var nextSleep: Boolean
        get() = gb("nextSleep")
        set(v) = sb("nextSleep", v)

    fun addLog(t: Long, status: String) {
        val arr = JSONArray(p.getString("log", "[]"))
        arr.put(JSONObject().put("t", t).put("s", status))
        val trimmed = JSONArray()
        val start = maxOf(0, arr.length() - 60)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        p.edit().putString("log", trimmed.toString()).commit()
    }

    fun logSince(from: Long): List<Pair<Long, String>> {
        val arr = JSONArray(p.getString("log", "[]"))
        val out = ArrayList<Pair<Long, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = o.getLong("t")
            if (t >= from) out.add(t to o.getString("s"))
        }
        return out.sortedBy { it.first }
    }
}
