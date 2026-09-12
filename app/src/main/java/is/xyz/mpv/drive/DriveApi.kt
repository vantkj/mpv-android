package `is`.xyz.mpv.drive

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Semua panggilan jaringan di sini bersifat BLOCKING (pakai .execute(), bukan
 * enqueue). Selalu panggil dari background thread (lihat contoh pemakaian di
 * DriveLoginActivity / DriveBrowserActivity yang jalanin ini di Thread terpisah).
 */
object DriveApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun exchangeCode(code: String, redirectUri: String): TokenResult {
        val body = JSONObject().apply {
            put("code", code)
            put("redirect_uri", redirectUri)
        }.toString().toRequestBody(JSON)

        val req = Request.Builder()
            .url("${Constants.PROXY_BASE}/oauth/exchange")
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: "{}"
            val json = JSONObject(text)
            if (!resp.isSuccessful || json.has("error")) {
                throw Exception(json.optString("error", "exchange_failed (${resp.code})"))
            }
            return TokenResult(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token", null),
                expiresIn = json.optLong("expires_in", 3600)
            )
        }
    }

    fun refreshAccessToken(refreshToken: String): TokenResult {
        val body = JSONObject().apply { put("refresh_token", refreshToken) }
            .toString().toRequestBody(JSON)

        val req = Request.Builder()
            .url("${Constants.PROXY_BASE}/oauth/refresh")
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: "{}"
            val json = JSONObject(text)
            if (!resp.isSuccessful || json.has("error")) {
                throw Exception(json.optString("error", "refresh_failed (${resp.code})"))
            }
            return TokenResult(
                accessToken = json.getString("access_token"),
                refreshToken = null,
                expiresIn = json.optLong("expires_in", 3600)
            )
        }
    }

    /** Query Drive API v3 files.list dengan pagination penuh. Lempar AuthExpiredException kalau 401. */
    fun listFiles(accessToken: String, query: String): List<DriveFile> {
        val out = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val urlBuilder = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "nextPageToken, files(id,name,size,mimeType)")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("orderBy", "folder,name_natural")
                .addQueryParameter("spaces", "drive")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
            if (pageToken != null) urlBuilder.addQueryParameter("pageToken", pageToken)

            val req = Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer $accessToken")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.code == 401) throw AuthExpiredException()
                if (!resp.isSuccessful) throw Exception("Drive API error ${resp.code}")

                val json = JSONObject(resp.body?.string() ?: "{}")
                val files = json.optJSONArray("files")
                if (files != null) {
                    for (i in 0 until files.length()) {
                        val f = files.getJSONObject(i)
                        out.add(
                            DriveFile(
                                id = f.getString("id"),
                                name = f.getString("name"),
                                mimeType = f.optString("mimeType", ""),
                                size = f.optLong("size", 0)
                            )
                        )
                    }
                }
                pageToken = if (json.has("nextPageToken")) json.getString("nextPageToken") else null
            }
        } while (pageToken != null)
        return out
    }

    /** URL streaming buat dikasih ke MPVActivity — sama persis logikanya dengan app.js. */
    fun buildStreamUrl(fileId: String): String =
        "${Constants.PROXY_BASE}?id=$fileId&key=${Constants.STREAM_KEY}"
}
