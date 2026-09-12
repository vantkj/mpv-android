package `is`.xyz.mpv.drive

/**
 * Samain semua nilai ini dengan yang ada di webapp (app.js) dan Worker
 * (stream-proxy-worker.js) — TIGA-TIGANYA harus identik.
 */
object Constants {
    const val CLIENT_ID = "973297230234-fugktib6mvnsqekp1ah9r63g7jsf53d7.apps.googleusercontent.com"
    const val SCOPE = "https://www.googleapis.com/auth/drive.readonly"
    const val PROXY_BASE = "https://ikiworker.panitkj.workers.dev"
    const val STREAM_KEY = "K0kNtxk27AAz3AC8X0TeMXNFLo2F37iaoE_vEhHm6pQ"

    // Redirect URI ini HARUS sama dengan yang terdaftar di Google Cloud Console
    // (Authorized redirect URIs) — kita pakai domain webapp yang sudah terdaftar,
    // WebView di app ini yang "mencegat" navigasi ke URL ini sebelum benar-benar dimuat.
    const val REDIRECT_URI = "https://masvan.panitkj.workers.dev"
}
