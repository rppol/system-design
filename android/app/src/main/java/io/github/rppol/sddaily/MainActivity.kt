package io.github.rppol.sddaily

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import java.io.OutputStream

/**
 * Single-activity host. Loads the fully offline study game (Markdown + SPA) that
 * `scripts/build_android_assets.sh` mirrors into assets/www, served through a
 * virtual https origin by [WebViewAssetLoader] so that fetch()/localStorage all
 * behave as on the deployed Pages site. No network, no permissions.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    // Holds the in-flight WebChromeClient file-chooser callback while the system
    // document picker is open; null when no chooser is pending. The WebView
    // contract requires exactly one onReceiveValue call per request.
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // Registered at construction (required before the activity is STARTED). Opens
    // the system document picker and routes the chosen URI — or an empty array on
    // cancel — back into the page's <input type="file"> that triggered it.
    private val fileChooserLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            val cb = filePathCallback
            filePathCallback = null
            cb?.onReceiveValue(if (uri == null) emptyArray() else arrayOf(uri))
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // Serve assets/www at the root of the reserved virtual host. A request to
        // https://appassets.androidplatform.net/www/game/index.html maps to the
        // packaged asset assets/www/game/index.html.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // The bundle is self-contained; deny file:// and content:// reach-out.
            allowFileAccess = false
            allowContentAccess = false
            // Required for onCreateWindow (below) to fire at all: without this,
            // target="_blank" anchors are dropped silently before
            // shouldOverrideUrlLoading ever sees them. Must be set before the
            // initial loadUrl() call.
            setSupportMultipleWindows(true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                // Non-matching URLs return null -> WebView handles them normally.
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                // Keep navigation within our virtual host inside the WebView; hand
                // any genuinely external link off to the system browser.
                if (url.host == "appassets.androidplatform.net") {
                    return false
                }
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                } catch (e: Exception) {
                    // No app can handle it -> just swallow the navigation.
                    true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Wired so the import <input type="file"> works, and so JS
            // alert()/confirm() render as native dialogs (default WebChromeClient
            // behaviour, active once a chrome client is set).
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                // Abandon any previous pending request per the one-callback contract.
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    // Import expects a single progress-backup JSON, but some file
                    // managers mistag the exported backup as a generic/binary or
                    // text type -> accept those too so the picker doesn't grey
                    // the file out.
                    fileChooserLauncher.launch(
                        arrayOf("application/json", "application/octet-stream", "text/plain")
                    )
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }

            // The reader renders external links with target="_blank" (e.g. GitHub
            // source links, external references), which never reach
            // shouldOverrideUrlLoading -- WebView instead asks for a new window
            // via this callback. With setSupportMultipleWindows(true) above, we
            // must handle it or the request is dropped and the tap does nothing.
            //
            // Per the WebChromeClient.onCreateWindow contract, returning true
            // obligates us to supply a WebView through the WebViewTransport and
            // call resultMsg.sendToTarget() -- skipping that reply (or returning
            // false without ever populating the transport) leaves the page's
            // window-open request unresolved and can hang the calling JS. So we
            // satisfy the contract with a throwaway, never-attached WebView: its
            // WebViewClient captures the first navigation (the actual target
            // URL, which onCreateWindow itself is never given directly), hands
            // it to the same ACTION_VIEW path used above, then destroys itself
            // without ever rendering a second browsing surface.
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val tempWebView = WebView(view.context)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        childView: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val url = request.url
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, url))
                        } catch (e: Exception) {
                            // No app can handle it -> swallow the navigation.
                        }
                        tempWebView.destroy()
                        return true
                    }
                }
                transport.webView = tempWebView
                resultMsg.sendToTarget()
                return true
            }
        }

        // window.SDAndroid bridge for the JSON backup export path.
        webView.addJavascriptInterface(SDAndroid(), "SDAndroid")

        // Hardware/gesture back: walk WebView history first, then leave the app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        webView.loadUrl("https://appassets.androidplatform.net/www/game/index.html")
    }

    /**
     * Exposed to the page as `window.SDAndroid`. Writes a progress-backup JSON to
     * the shared Downloads collection via MediaStore, which needs no runtime
     * permission on API 29+ (minSdk is 29), then confirms with a Toast.
     */
    private inner class SDAndroid {
        @JavascriptInterface
        fun saveBackup(name: String, json: String) {
            try {
                val fileName = if (name.endsWith(".json")) name else "$name.json"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri: Uri? = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                )
                if (uri == null) {
                    toast("Backup failed: could not create file")
                    return
                }
                resolver.openOutputStream(uri)?.use { out: OutputStream ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                // Publish (clear IS_PENDING) so it's visible in Files/Downloads.
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                toast("Saved to Downloads/$fileName")
            } catch (e: Exception) {
                toast("Backup failed: ${e.message}")
            }
        }
    }

    private fun toast(msg: String) {
        // @JavascriptInterface calls arrive on a binder thread; hop to UI first.
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }
}
