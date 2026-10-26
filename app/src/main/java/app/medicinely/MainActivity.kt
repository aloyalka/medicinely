package app.medicinely

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Toast
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private var screen = "home"
    private var setupStep = 1
    private var updateUrl: String? = null
    private var scroller: ScrollView? = null

    private val tick = object : Runnable {
        override fun run() {
            if (screen == "home") render(keepScroll = true)
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        Alerts.channels(this)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.BG
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        screen = if (p.setupDone) "home" else "setup"
        Updater.check(this) { url ->
            updateUrl = url
            if (screen == "home") render(keepScroll = true)
        }
    }

    override fun onResume() {
        super.onResume()
        Engine.reschedule(this)
        render(keepScroll = true)
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 30_000L)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            screen == "settings" -> { screen = "home"; render() }
            screen == "setup" && setupStep > 1 -> { setupStep -= 1; render() }
            else -> super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render(keepScroll = true)
    }

    private fun render(keepScroll: Boolean = false) {
        val y = if (keepScroll) (scroller?.scrollY ?: 0) else 0
        val content = when (screen) {
            "setup" -> setupView()
            "settings" -> settingsView()
            else -> homeView()
        }
        val sv = ScrollView(this)
        sv.setBackgroundColor(C.BG)
        sv.isFillViewport = true
        sv.addView(content)
        setContentView(sv)
        scroller = sv
        if (y > 0) sv.post { sv.scrollTo(0, y) }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // ---------------- Home ----------------

    private fun homeView(): View {
        val now = System.currentTimeMillis()
        val col = column(24)

        val head = row()
        head.add(label("Medicinely", 22f, C.TEAL, true))
        head.add(bigButton("Settings", C.WHITE, C.INK, C.BORDER, sp = 17f) { screen = "settings"; render() }, 0, 48, 0f)
        col.add(head)

        updateUrl?.let { url ->
            val c = card(C.TEAL_TINT)
            c.add(label("A new version of Medicinely is ready", 19f, C.TEAL_DARK, true))
            c.add(label("It downloads in your browser. Open the file to install it.", 17f, C.TEAL_DARK), 4)
            c.add(bigButton("Update now", C.TEAL, C.WHITE, bold = true) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }, 12, 56)
            col.add(c, 20)
        }

        if (p.napUntilMs > now) {
            val c = card(C.NIGHT_TINT, padDp = 24)
            c.add(label("Napping until", 18f, C.NIGHT_INK))
            c.add(label(Engine.fmt(p.napUntilMs), 48f, C.NIGHT_INK, true))
            c.add(label("No chimes until then. Doses still show as silent notifications.", 18f, C.NIGHT_INK), 6)
            c.add(bigButton("I'm awake", C.NIGHT_INK, C.WHITE, bold = true) { Engine.awake(this); render() }, 16, 64)
            c.add(bigButton("Extend nap by 30 min", C.WHITE, C.NIGHT_INK, C.BORDER, sp = 19f) {
                Engine.extendNap(this); render()
            }, 10, 56)
            col.add(c, 20)
        }

        if (p.mode == Mode.NONE) {
            val c = card(C.WHITE, C.BORDER, 24)
            c.add(label("No reminders running", 26f, C.INK, true))
            c.add(label("Choose how often below to start.", 19f, C.MUTED), 6)
            col.add(c, 20)
        } else {
            val next = Engine.nextDose(p, now)
            val c = card(C.WHITE, C.BORDER, 24)
            if (next == null) {
                c.add(label("Course ends today", 22f, C.INK, true))
            } else {
                val kicker = when {
                    next.sleep -> "Next dose · before sleep"
                    !Engine.isToday(next.time) -> "Next dose · tomorrow"
                    else -> "Next dose"
                }
                c.add(label(kicker, 18f, if (next.sleep) C.NIGHT_INK else C.MUTED, next.sleep))
                c.add(label(Engine.fmt(next.time), 56f, C.INK, true))
                c.add(label("in " + Engine.duration(next.time - now), 20f, C.TEAL))
            }
            if (p.lastTakenMs > 0) c.add(label("Last taken at " + Engine.fmtDay(p.lastTakenMs), 17f, C.MUTED), 12)
            if (p.courseDays > 0 && p.endMs > 0) c.add(label("Course ends " + Engine.fmtDay(p.endMs), 17f, C.MUTED), 4)
            col.add(c, 20)

            if (p.pendingDueMs > 0) {
                val m = card(C.AMBER_BG, C.AMBER_LINE)
                m.add(label("The " + Engine.fmt(p.pendingDueMs) + " dose isn't marked taken", 19f, C.AMBER, true))
                m.add(bigButton("Taken now", C.TEAL, C.WHITE, bold = true) { Engine.taken(this); render() }, 12, 64)
                col.add(m, 16)
            }
        }

        col.add(label("How often", 18f, C.MUTED), 24)
        val opts = listOf(
            Triple("Every 15 min", Mode.INTERVAL, 15),
            Triple("Every hour", Mode.INTERVAL, 60),
            Triple("Every 3 hours", Mode.INTERVAL, 180),
            Triple("Day and night", Mode.DAYNIGHT, 0)
        )
        for (r in 0..1) {
            val line = row()
            for (k in 0..1) {
                val (name, mode, iv) = opts[r * 2 + k]
                val active = p.mode == mode && (mode == Mode.DAYNIGHT || p.intervalMin == iv)
                val btn = if (active) {
                    bigButton(name, C.TEAL, C.WHITE, bold = true) { if (mode == Mode.DAYNIGHT) askDayNight() }
                } else {
                    bigButton(name, C.WHITE, C.INK, C.BORDER) { choose(mode, iv, name) }
                }
                line.add(btn, if (k == 0) 0 else 10, 68)
            }
            col.add(line, 10)
        }

        if (p.mode != Mode.NONE && p.napUntilMs <= now) {
            col.add(bigButton("Take a nap", C.WHITE, C.NIGHT_INK, C.BORDER) { askNap() }, 16, 64)
        }

        val log = p.logSince(Engine.dayStart(now)).takeLast(8)
        if (log.isNotEmpty()) {
            col.add(label("Today", 18f, C.MUTED), 24)
            for ((t, s) in log) {
                val line = row()
                line.add(label(Engine.fmt(t), 18f, C.INK))
                val color = when {
                    s == "Taken" -> C.TEAL
                    s == "Missed" -> C.AMBER
                    else -> C.MUTED
                }
                val st = label(s, 18f, color, s == "Missed")
                st.gravity = Gravity.END
                line.add(st)
                col.add(line, 8)
            }
        }

        if (p.mode != Mode.NONE) {
            col.add(bigButton("Stop reminders", C.BG, C.RED, sp = 18f) { askStop() }, 28, 52)
        }
        return col
    }

    private fun choose(mode: Int, iv: Int, name: String) {
        if (mode == Mode.DAYNIGHT) { askDayNight(); return }
        val now = System.currentTimeMillis()
        val starting = p.mode == Mode.NONE
        val anchor = if (starting) now else p.anchorMs
        val ev = Engine.intervalDose(p, now, iv, anchor)
        val expected = anchor + iv * Engine.MIN
        val why = when {
            ev.sleep -> "This is the before-sleep reminder"
            abs(ev.time - expected) < 60_000L ->
                if (starting) "${Engine.span(iv)} from now"
                else if (anchor == p.lastTakenMs) "${Engine.span(iv)} after your last dose at ${Engine.fmt(anchor)}"
                else "${Engine.span(iv)} after the last reminder at ${Engine.fmt(anchor)}"
            expected < now -> "Your next dose is already due"
            else -> "The first chime after you wake up"
        }
        val lower = name.lowercase()
        confirm(
            if (starting) "Start $lower?" else "Switch to $lower?",
            "Next chime", Engine.fmtDay(ev.time), why,
            if (starting) "Start $lower" else "Switch to $lower",
            if (starting) "Cancel" else "Keep current"
        ) {
            Engine.start(this, mode, iv)
            render()
        }
    }

    private fun askDayNight() {
        pickTime("Daytime dose", p.dayMin) { day ->
            pickTime("Night dose", p.nightMin) { night ->
                if (day == night) {
                    toast("The two times need to be different")
                } else {
                    val ev = Engine.dayNightDose(p, System.currentTimeMillis(), day, night)
                    val starting = p.mode == Mode.NONE
                    confirm(
                        if (starting) "Start day and night?" else "Use these times?",
                        "Next chime", Engine.fmtDay(ev.time),
                        "Every day at ${Engine.fmtMin(day)} and ${Engine.fmtMin(night)}",
                        if (starting) "Start" else "Save times",
                        "Cancel"
                    ) {
                        p.dayMin = day
                        p.nightMin = night
                        Engine.start(this, Mode.DAYNIGHT, 0)
                        render()
                    }
                }
            }
        }
    }

    private fun askNap() {
        val v = column(24)
        v.add(label("Nap for how long?", 26f, C.INK, true))
        v.add(label("No chimes during the nap. Doses still appear as silent notifications.", 17f, C.MUTED), 8)
        val d = AlertDialog.Builder(this).setView(v).create()
        for ((name, m) in listOf("30 min" to 30, "1 hour" to 60, "1.5 hours" to 90, "2 hours" to 120)) {
            v.add(bigButton(name, C.WHITE, C.NIGHT_INK, C.BORDER) {
                d.dismiss(); Engine.startNap(this, m); render()
            }, 10, 60)
        }
        v.add(bigButton("Other length", C.WHITE, C.INK, C.BORDER) {
            d.dismiss()
            askNumber("Nap length in minutes", 45) { n -> Engine.startNap(this, n); render() }
        }, 10, 60)
        d.show()
    }

    private fun askStop() {
        val v = column(24)
        v.add(label("Stop all reminders?", 26f, C.INK, true))
        v.add(label("No more chimes until you choose a frequency again.", 18f, C.MUTED), 8)
        val d = AlertDialog.Builder(this).setView(v).create()
        v.add(bigButton("Stop reminders", C.RED, C.WHITE, bold = true) { d.dismiss(); Engine.stop(this); render() }, 20, 64)
        v.add(bigButton("Keep them", C.WHITE, C.INK, C.BORDER) { d.dismiss() }, 10, 64)
        d.show()
    }

    // ---------------- Dialog helpers ----------------

    private fun confirm(
        title: String, kicker: String, big: String, why: String,
        yes: String, no: String, onYes: () -> Unit
    ) {
        val v = column(24)
        v.add(label(title, 26f, C.INK, true))
        val box = card(C.TEAL_TINT)
        box.add(label(kicker, 17f, C.TEAL_DARK))
        box.add(label(big, 36f, C.TEAL_DARK, true))
        box.add(label(why, 17f, C.TEAL_DARK))
        v.add(box, 16)
        val d = AlertDialog.Builder(this).setView(v).create()
        v.add(bigButton(yes, C.TEAL, C.WHITE, bold = true) { d.dismiss(); onYes() }, 20, 64)
        v.add(bigButton(no, C.WHITE, C.INK, C.BORDER) { d.dismiss() }, 10, 64)
        d.show()
    }

    private fun pickTime(title: String, initial: Int, onPicked: (Int) -> Unit) {
        val d = TimePickerDialog(this, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, false)
        d.setTitle(title)
        d.show()
    }

    private fun askNumber(title: String, initial: Int, onNumber: (Int) -> Unit) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.setText(initial.toString())
        input.textSize = 24f
        val wrap = column(24)
        wrap.add(input)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("OK") { _, _ ->
                val n = input.text.toString().toIntOrNull() ?: 0
                if (n in 1..999) onNumber(n) else toast("Enter a number between 1 and 999")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------- Setup ----------------

    private fun setupView(): View {
        val col = column(24)
        col.add(label("Step $setupStep of 3", 18f, C.MUTED), 24)
        when (setupStep) {
            1 -> {
                col.add(label("When do you wake up and go to sleep?", 30f, C.INK, true), 12)
                col.add(label("Chimes only ring while you're awake. The first one comes 5 minutes after you wake up, the last 5 minutes before you sleep.", 19f, C.MUTED), 12)
                col.add(timeCard(), 24)
                col.add(label("You can change these later in Settings.", 17f, C.MUTED), 12)
                col.add(bigButton("Continue", C.TEAL, C.WHITE, sp = 21f, bold = true) { setupStep = 2; render() }, 32, 64)
            }
            2 -> {
                col.add(label("Let Medicinely ring reliably", 30f, C.INK, true), 12)
                col.add(label("Allow each of these so the phone never blocks a chime.", 19f, C.MUTED), 12)
                col.add(permissionsBlock(), 20)
                col.add(bigButton("Continue", C.TEAL, C.WHITE, sp = 21f, bold = true) { setupStep = 3; render() }, 32, 64)
            }
            else -> {
                col.add(label("Play a test chime", 30f, C.INK, true), 12)
                col.add(label("Try it with your phone on silent. You should still hear it.", 19f, C.MUTED), 12)
                col.add(bigButton("Play test chime", C.WHITE, C.TEAL, C.TEAL, sp = 21f, bold = true) {
                    Chime.play(this, p.chime, ignoreNap = true)
                }, 24, 64)
                col.add(bigButton("I heard it, finish setup", C.TEAL, C.WHITE, sp = 21f, bold = true) {
                    p.setupDone = true; screen = "home"; render()
                }, 12, 64)
                col.add(bigButton("I didn't hear it", C.WHITE, C.INK, C.BORDER) { setupStep = 2; render() }, 12, 60)
                col.add(label("If you didn't hear it, check the permissions on the previous step and make sure Do Not Disturb allows alarms.", 17f, C.MUTED), 12)
            }
        }
        return col
    }

    private fun settingRow(parent: LinearLayout, key: String, value: String, onClick: () -> Unit) {
        val r = row()
        r.minimumHeight = dp(64)
        r.add(label(key, 19f, C.INK))
        val v = label(value, 19f, C.TEAL, true)
        v.gravity = Gravity.END
        r.add(v)
        r.isClickable = true
        r.setOnClickListener { onClick() }
        parent.add(r)
    }

    private fun timeCard(): View {
        val c = card(C.WHITE, C.BORDER, 16)
        settingRow(c, "Wake up", Engine.fmtMin(p.wakeMin)) {
            pickTime("Wake up", p.wakeMin) { m ->
                if (m == p.sleepMin) toast("Wake-up and sleep times need to be different")
                else { p.wakeMin = m; Engine.reschedule(this); render(keepScroll = true) }
            }
        }
        settingRow(c, "Sleep", Engine.fmtMin(p.sleepMin)) {
            pickTime("Sleep", p.sleepMin) { m ->
                if (m == p.wakeMin) toast("Wake-up and sleep times need to be different")
                else { p.sleepMin = m; Engine.reschedule(this); render(keepScroll = true) }
            }
        }
        return c
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun permissionsBlock(): View {
        val col = column(0)
        val nm = getSystemService(NotificationManager::class.java)
        val pm = getSystemService(PowerManager::class.java)

        col.add(permRow("Notifications", "So reminders can appear", nm.areNotificationsEnabled()) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            else openAppSettings()
        })
        if (Build.VERSION.SDK_INT >= 34) {
            col.add(permRow("Reminder screen", "Shows Medicine Time over the lock screen", nm.canUseFullScreenIntent()) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
                } catch (e: Exception) { openAppSettings() }
            }, 12)
        }
        col.add(permRow("No battery limits", "Stops the phone from blocking chimes to save battery", pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) { openAppSettings() }
        }, 12)
        val brand = Build.MANUFACTURER.lowercase()
        val strict = listOf("xiaomi", "redmi", "poco", "oppo", "vivo", "realme", "oneplus", "huawei", "honor", "iqoo")
        if (strict.any { brand.contains(it) }) {
            col.add(permRow(
                "Autostart",
                "This phone needs one more step: in App info, turn on Autostart and set Battery to No restrictions.",
                false, "Open app info"
            ) { openAppSettings() }, 12)
        }
        return col
    }

    private fun permRow(title: String, desc: String, ok: Boolean, button: String = "Allow", onClick: () -> Unit): View {
        val c = card(C.WHITE, C.BORDER, 18)
        c.add(label(title, 20f, C.INK, true))
        c.add(label(desc, 17f, C.MUTED), 4)
        if (ok) c.add(label("Done", 18f, C.TEAL, true), 10)
        else c.add(bigButton(button, C.TEAL, C.WHITE, bold = true) { onClick() }, 12, 56)
        return c
    }

    // ---------------- Settings ----------------

    private fun settingsView(): View {
        val col = column(24)
        val head = row()
        head.add(label("Settings", 30f, C.INK, true))
        head.add(bigButton("Back", C.WHITE, C.INK, C.BORDER, sp = 17f) { screen = "home"; render() }, 0, 48, 0f)
        col.add(head)

        col.add(timeCard(), 20)

        val c2 = card(C.WHITE, C.BORDER, 16)
        settingRow(c2, "Reminders run", if (p.courseDays > 0) "${p.courseDays} days" else "Until stopped") { askCourse() }
        if (p.mode == Mode.DAYNIGHT) {
            settingRow(c2, "Day and night times", "${Engine.fmtMin(p.dayMin)}, ${Engine.fmtMin(p.nightMin)}") { askDayNight() }
        }
        col.add(c2, 12)

        col.add(label("Chime", 18f, C.MUTED), 24)
        for (r in 0..3) {
            val line = row()
            for (k in 0..1) {
                val i = r * 2 + k
                val name = Chime.names[i]
                val b = if (p.chime == i) bigButton(name, C.TEAL_TINT, C.TEAL_DARK, C.TEAL, sp = 18f, bold = true) { Chime.play(this, i, true) }
                        else bigButton(name, C.WHITE, C.INK, C.BORDER, sp = 18f) {
                            p.chime = i; Chime.play(this, i, true); render(keepScroll = true)
                        }
                line.add(b, if (k == 0) 0 else 8, 56)
            }
            col.add(line, 8)
        }

        col.add(label("Chime volume", 18f, C.MUTED), 24)
        val sb = SeekBar(this)
        sb.max = 90
        sb.progress = p.volume - 10
        sb.progressTintList = ColorStateList.valueOf(C.TEAL)
        sb.thumbTintList = ColorStateList.valueOf(C.TEAL)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, value: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {
                p.volume = s.progress + 10
                Chime.play(this@MainActivity, p.chime, true)
            }
        })
        col.add(sb, 8, 48)

        col.add(bigButton("Play test chime", C.WHITE, C.TEAL, C.TEAL, bold = true) {
            Chime.play(this, p.chime, true)
        }, 20, 60)

        col.add(label("Permissions", 18f, C.MUTED), 28)
        col.add(permissionsBlock(), 8)

        col.add(label("Version ${BuildConfig.VERSION_NAME}", 15f, C.MUTED), 24)
        return col
    }

    private fun askCourse() {
        val v = column(24)
        v.add(label("How long should reminders run?", 24f, C.INK, true))
        val d = AlertDialog.Builder(this).setView(v).create()
        v.add(bigButton("Until I stop them", C.WHITE, C.INK, C.BORDER) {
            d.dismiss(); Engine.setCourse(this, 0); render(keepScroll = true)
        }, 16, 60)
        v.add(bigButton("A set number of days", C.WHITE, C.INK, C.BORDER) {
            d.dismiss()
            askNumber("Number of days, counting today", if (p.courseDays > 0) p.courseDays else 7) { n ->
                Engine.setCourse(this, n); render(keepScroll = true)
            }
        }, 10, 60)
        d.show()
    }
}
