package com.web2apk.template
import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

class WebAppInterface(private val activity: Activity, private val webView: WebView) {
    @JavascriptInterface
    fun retryConnection() {
        activity.runOnUiThread {
            try {
                val cfg = activity.assets.open("app_config.json").bufferedReader().use { it.readText() }
                webView.loadUrl(JSONObject(cfg).getString("url"))
            } catch (_: Exception) {}
        }
    }
}