package `is`.xyz.mpv.drive

import org.json.JSONArray
import org.json.JSONObject

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String = "application/vnd.google-apps.folder",
    val size: Long = 0,
    val shared: Boolean = false
) {
    val isFolder: Boolean get() = mimeType == "application/vnd.google-apps.folder"
}

class AuthExpiredException : Exception("auth_expired")
data class TokenResult(val accessToken: String, val refreshToken: String?, val expiresIn: Long)

/** Helper JSON kecil-kecilan, biar nggak perlu nambah dependency Gson/Moshi. */
object DriveJson {
    fun parseFileList(raw: String): MutableList<DriveFile> {
        val arr = JSONArray(raw)
        val out = mutableListOf<DriveFile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(DriveFile(o.getString("id"), o.getString("name")))
        }
        return out
    }

    fun toJsonFileList(list: List<DriveFile>): String {
        val arr = JSONArray()
        for (f in list) {
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
            })
        }
        return arr.toString()
    }

    fun parseStatusMap(raw: String): MutableMap<String, String> {
        val o = JSONObject(raw)
        val map = mutableMapOf<String, String>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = o.getString(k)
        }
        return map
    }

    fun toJsonStatusMap(map: Map<String, String>): String {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        return o.toString()
    }
}

/** Human-readable file size, sama seperti humanSize() di app.js */
fun humanSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var b = bytes.toDouble()
    var i = 0
    while (b >= 1024 && i < units.size - 1) { b /= 1024; i++ }
    return if (b < 10 && i > 0) String.format("%.1f %s", b, units[i]) else String.format("%.0f %s", b, units[i])
}
