package app.medicinely

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** The full-screen "Medicine Time" card, shown over the lock screen. */
class ReminderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        build()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        build()
    }

    private fun build() {
        val p = Prefs(this)
        if (p.pendingDueMs == 0L) { finish(); return }
        val night = p.currentIsSleep
        val bg = if (night) C.NIGHT else C.TEAL_TINT
        val fg = if (night) C.NIGHT_TEXT else C.TEAL_DARK
        val soft = if (night) C.NIGHT_SOFT else C.TEAL_DARK
        window.statusBarColor = bg
        window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            if (night) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = column(24)
        root.setBackgroundColor(bg)
        root.gravity = Gravity.CENTER_HORIZONTAL

        val time = label(Engine.fmt(System.currentTimeMillis()), 22f, soft)
        time.gravity = Gravity.CENTER
        root.add(time, 64)
        val title = label("Medicine Time", 46f, fg, true)
        title.gravity = Gravity.CENTER
        root.add(title, 8)

        if (night) {
            val pill = card(C.NIGHT_BTN, padDp = 14)
            val t = label("Before-sleep dose", 20f, C.NIGHT_TEXT, true)
            t.gravity = Gravity.CENTER
            pill.add(t)
            root.add(pill, 16)
            val s = label("Last reminder until " + Engine.fmtMin(p.wakeMin + 5), 18f, C.NIGHT_SOFT)
            s.gravity = Gravity.CENTER
            root.add(s, 10)
        } else if (p.missedFlag) {
            val m = card(C.AMBER_BG, C.AMBER_LINE, 16)
            val a = label("You may have missed a dose", 20f, C.AMBER, true)
            a.gravity = Gravity.CENTER
            m.add(a)
            if (p.missedAtMs > 0) {
                val b = label("The " + Engine.fmt(p.missedAtMs) + " dose wasn't marked taken", 18f, C.AMBER)
                b.gravity = Gravity.CENTER
                m.add(b, 4)
            }
            root.add(m, 16)
        }

        val spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        val takenLabel = if (p.missedFlag && !night) "Taken now" else "Taken"
        val taken = if (night) bigButton(takenLabel, C.NIGHT_TEXT, C.NIGHT, sp = 28f, bold = true) { taken() }
                    else bigButton(takenLabel, C.TEAL, C.WHITE, sp = 28f, bold = true) { taken() }
        root.add(taken, 0, 88)

        val nn = label("Not now, remind me in", 19f, soft)
        nn.gravity = Gravity.CENTER
        root.add(nn, 20)
        val r = row()
        val choices = if (night) listOf(15, 30) else listOf(15, 30, 60)
        choices.forEachIndexed { i, m ->
            val name = if (m == 60) "1 hour" else "$m min"
            val b = if (night) bigButton(name, C.NIGHT_BTN, C.NIGHT_TEXT, C.NIGHT_LINE) { later(m) }
                    else bigButton(name, C.WHITE, C.TEAL_DARK, C.TEAL_LINE) { later(m) }
            r.add(b, if (i == 0) 0 else 10, 64)
        }
        root.add(r, 10)
        setContentView(root)
    }

    private fun taken() {
        Engine.taken(this)
        finish()
    }

    private fun later(minutes: Int) {
        Engine.delay(this, minutes)
        finish()
    }
}
