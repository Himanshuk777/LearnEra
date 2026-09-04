package com.web2apk.template

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private lateinit var offlineManager: OfflineSupport
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileUploadCallback?.onReceiveValue(results)
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        val uiConfig = loadConfig("ui_config.json")
        applyDisplayMode(uiConfig)

        offlineManager = OfflineSupport(this, webView)
        offlineManager.registerNetworkMonitoring()

        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = uiConfig.optBoolean("javascript", true)
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.addJavascriptInterface(WebAppInterface(this, webView), "AndroidBridge")
        webView.setBackgroundColor(Color.TRANSPARENT)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                v: WebView?, callback: ValueCallback<Array<Uri>>?, p: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = callback
                val intent = p?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                fileChooserLauncher.launch(intent)
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest) = assetLoader.shouldInterceptRequest(req.url)
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url.toString()
                if (u.startsWith("tel:") || u.startsWith("mailto:") || u.startsWith("sms:")) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                    return true
                }
                return false
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) offlineManager.showOfflinePage()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        val appConfig = loadConfig("app_config.json")
        webView.loadUrl(appConfig.optString("url", "https://appassets.androidplatform.net/assets/offline.html"))
    }

    private fun loadConfig(fileName: String): JSONObject {
        return try {
            val txt = assets.open(fileName).bufferedReader().use { it.readText() }
            JSONObject(txt)
        } catch (_: Exception) { JSONObject() }
    }

    private fun applyDisplayMode(uiConfig: JSONObject) {
        val edgeToEdge = uiConfig.optBoolean("edgeToEdge", true)
        val hideStatusBar = uiConfig.optBoolean("hideStatusBar", false)
        val immersive = uiConfig.optBoolean("immersiveFullscreen", false)

        WindowCompat.setDecorFitsSystemWindows(window, !edgeToEdge)
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        if (hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
        if (immersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (edgeToEdge && !immersive) {
            ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
                val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
                WindowInsetsCompat.CONSUMED
            }
        }
    }

    override fun onDestroy() {
        offlineManager.unregister()
        super.onDestroy()
    }
}