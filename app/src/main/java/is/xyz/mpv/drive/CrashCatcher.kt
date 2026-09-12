package `is`.xyz.mpv.drive

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Nangkep crash (uncaught exception) di mana pun dalam app, simpen full stack
 * trace-nya ke SharedPreferences (bertahan walau app force-close & dibuka lagi).
 * Nggak butuh adb/logcat/root — cukup dipasang sekali di awal.
 */
object CrashCatcher {
    private const val PREFS = "crash_prefs"
    private const val KEY = "last_crash"
    private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val appCtx = ctx.applicationContext
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY, sw.toString())
                    .apply()
            } catch (_: Exception) { /* jangan sampai crash-handler-nya sendiri ikut crash */ }
            prevHandler?.uncaughtException(thread, throwable)
        }
    }

    fun getLastCrash(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
