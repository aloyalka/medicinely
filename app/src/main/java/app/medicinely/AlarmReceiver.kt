package app.medicinely

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Engine.ACTION_FIRE -> {
                val pending = goAsync()
                val wl = ctx.getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "medicinely:chime")
                wl.acquire(8_000L)
                val ring = try { Engine.onFire(ctx) } catch (e: Exception) { false }
                if (ring) Chime.play(ctx, Prefs(ctx).chime)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (wl.isHeld) wl.release()
                    pending.finish()
                }, if (ring) 4_000L else 10L)
            }
            Engine.ACTION_TAKEN -> Engine.taken(ctx)
            Engine.ACTION_DELAY -> Engine.delay(ctx, intent.getIntExtra(Engine.EXTRA_MIN, 15))
            Engine.ACTION_AWAKE -> Engine.awake(ctx)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Engine.reschedule(ctx)
    }
}
