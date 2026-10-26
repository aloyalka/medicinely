package app.medicinely

import android.app.Activity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub for a newer release. */
object Updater {
    val downloadUrl: String
        get() = "https://github.com/${BuildConfig.REPO}/releases/latest/download/medicinely.apk"

    fun check(activity: Activity, onAvailable: (String) -> Unit) {
        if (BuildConfig.REPO.isEmpty()) return
        Thread {
            try {
                val c = URL("https://api.github.com/repos/${BuildConfig.REPO}/releases/latest")
                    .openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.connectTimeout = 8000
                c.readTimeout = 8000
                if (c.responseCode == 200) {
                    val json = JSONObject(c.inputStream.bufferedReader().readText())
                    val latest = json.optString("tag_name").removePrefix("v").toIntOrNull() ?: 0
                    if (latest > BuildConfig.VERSION_CODE) {
                        activity.runOnUiThread { onAvailable(downloadUrl) }
                    }
                }
                c.disconnect()
            } catch (e: Exception) {
                // No internet or GitHub unreachable: try again next time.
            }
        }.start()
    }
}
