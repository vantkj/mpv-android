package `is`.xyz.mpv.drive

import android.content.Context
import android.content.SharedPreferences

/**
 * Padanan localStorage di app.js. Disimpan sebagai SharedPreferences biasa.
 * Kalau mau lebih aman, ganti ke EncryptedSharedPreferences (androidx.security-crypto).
 */
object TokenStore {
    private const val PREFS = "drive_prefs"
    private const val K_ACCESS = "access_token"
    private const val K_EXPIRY = "token_expiry"
    private const val K_REFRESH = "refresh_token"
    private const val K_FAVORITES = "favorites"
    private const val K_WATCH = "watch_status"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveTokens(ctx: Context, accessToken: String, refreshToken: String?, expiresInSec: Long) {
        prefs(ctx).edit().apply {
            putString(K_ACCESS, accessToken)
            putLong(K_EXPIRY, System.currentTimeMillis() + expiresInSec * 1000 - 60_000)
            if (refreshToken != null) putString(K_REFRESH, refreshToken)
        }.apply()
    }

    fun saveAccessTokenOnly(ctx: Context, accessToken: String, expiresInSec: Long) {
        prefs(ctx).edit()
            .putString(K_ACCESS, accessToken)
            .putLong(K_EXPIRY, System.currentTimeMillis() + expiresInSec * 1000 - 60_000)
            .apply()
    }

    /** Balikin access_token yang masih valid, atau null kalau nggak ada/sudah expired. */
    fun getValidAccessToken(ctx: Context): String? {
        val p = prefs(ctx)
        val token = p.getString(K_ACCESS, null) ?: return null
        val expiry = p.getLong(K_EXPIRY, 0)
        return if (System.currentTimeMillis() < expiry) token else null
    }

    fun getRefreshToken(ctx: Context): String? = prefs(ctx).getString(K_REFRESH, null)

    fun clearAccessToken(ctx: Context) {
        prefs(ctx).edit().remove(K_ACCESS).remove(K_EXPIRY).apply()
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    // ---------------- Favorit (folder) ----------------
    fun getFavorites(ctx: Context): MutableList<DriveFile> =
        DriveJson.parseFileList(prefs(ctx).getString(K_FAVORITES, "[]")!!)

    fun isFavorite(ctx: Context, id: String): Boolean = getFavorites(ctx).any { it.id == id }

    fun toggleFavorite(ctx: Context, file: DriveFile) {
        val list = getFavorites(ctx)
        val idx = list.indexOfFirst { it.id == file.id }
        if (idx >= 0) list.removeAt(idx) else list.add(file)
        prefs(ctx).edit().putString(K_FAVORITES, DriveJson.toJsonFileList(list)).apply()
    }

    // ---------------- Status tontonan (file) ----------------
    // "none" | "watching" | "finished"
    fun getWatchStatus(ctx: Context, id: String): String {
        val map = DriveJson.parseStatusMap(prefs(ctx).getString(K_WATCH, "{}")!!)
        return map[id] ?: "none"
    }

    fun setWatchStatus(ctx: Context, id: String, status: String) {
        val map = DriveJson.parseStatusMap(prefs(ctx).getString(K_WATCH, "{}")!!)
        if (status == "none") map.remove(id) else map[id] = status
        prefs(ctx).edit().putString(K_WATCH, DriveJson.toJsonStatusMap(map)).apply()
    }
}
