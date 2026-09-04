package com.web2apk.template
import android.content.Context
import android.net.*
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONObject

class OfflineSupport(private val ctx: Context, private val webView: WebView) {
    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isOffline = false

    private val cb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (isOffline) {
                isOffline = false
                mainHandler.post {
                    try {
                        val txt = ctx.assets.open("app_config.json").bufferedReader().use { it.readText() }
                        webView.loadUrl(JSONObject(txt).getString("url"))
                    } catch (_: Exception) {}
                }
            }
        }
        override fun onLost(network: Network) { isOffline = true }
    }

    fun registerNetworkMonitoring() {
        cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb)
    }

    fun showOfflinePage() {
        isOffline = true
        mainHandler.post { webView.loadUrl("https://appassets.androidplatform.net/assets/offline.html") }
    }

    fun unregister() { try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) {} }
}