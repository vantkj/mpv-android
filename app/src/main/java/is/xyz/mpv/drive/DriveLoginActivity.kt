package `is`.xyz.mpv.drive

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DriveLoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith(Constants.REDIRECT_URI) && !finished) {
                    finished = true
                    val code = request.url.getQueryParameter("code")
                    if (code != null) {
                        exchangeInBackground(code)
                    } else {
                        Toast.makeText(this@DriveLoginActivity, "Login dibatalkan", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                    return true
                }
                return false
            }
        }

        val authUrl = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", Constants.CLIENT_ID)
            .appendQueryParameter("redirect_uri", Constants.REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", Constants.SCOPE)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build().toString()
        webView.loadUrl(authUrl)
    }

    private fun exchangeInBackground(code: String) {
        Thread {
            try {
                val result = DriveApi.exchangeCode(code, Constants.REDIRECT_URI)
                TokenStore.saveTokens(this, result.accessToken, result.refreshToken, result.expiresIn)
                runOnUiThread {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Gagal login: ${e.message}", Toast.LENGTH_LONG).show()
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }.start()
    }
}
