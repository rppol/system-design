package io.github.rppol.sddaily

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

    private companion object {
        const val START_URL = "https://appassets.androidplatform.net/www/game/index.html"
        const val INTERNAL_HOST = "appassets.androidplatform.net"
        const val KEY_LAST_ROUTE = "lastRoute"
    }

    private lateinit var webView: WebView

    // Last in-app hash route (e.g. "/reader/hld%2Fcaching%2FREADME.md"), tracked
    // as a plain field so onSaveInstanceState never has to touch a WebView that
    // may already be dead. Restored ONLY from savedInstanceState, which arrives
    // exactly in the two cases where the route is lost against the user's will:
    // a renderer crash (recreate below) and system process death. A launch from
    // the launcher carries no bundle, so Home stays the front door.
    private var lastRoute: String? = null

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
            if (cb == null) {
                // Process death while the picker was open: the launcher survived
                // recreation but the page's file-input state did not — the picked
                // URI has nowhere to go. Say so instead of failing silently.
                if (uri != null) toast("Import interrupted — please try again")
            } else {
                cb.onReceiveValue(if (uri == null) emptyArray() else arrayOf(uri))
            }
        }

    // Enabled only when in-app back has somewhere meaningful to go (see
    // doUpdateVisitedHistory below). When disabled, back falls through to the
    // system, restoring the Android 15 predictive back-to-home animation and
    // making Home a true exit point instead of forcing the user to unwind the
    // whole session's hash history one press at a time.
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (webView.canGoBack()) webView.goBack() else finish()
        }
    }

    // Immersive-sticky fullscreen: bars stay hidden; an edge swipe shows them
    // transiently and they auto-hide again.
    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, webView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    // Dialogs, the import file picker, and app switches can bring the bars
    // back permanently — re-assert fullscreen whenever focus returns.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::webView.isInitialized) hideSystemBars()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        // WebView's own surface defaults to WHITE until the page's first paint —
        // a visible flash on every cold start of a dark app, and any inset margin
        // around it would show as white bands. Black on both layers closes it.
        webView.setBackgroundColor(Color.BLACK)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(root)

        // TRUE FULLSCREEN (owner request): hide the status + navigation bars
        // entirely, immersive-sticky style — a swipe from the edge peeks them
        // transiently and they re-hide on their own. This removes the whole
        // status-bar-overlap failure class (padding around the bars proved
        // unreliable across devices) and lets the reader's immersive mode be
        // genuinely full screen.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        // Let content extend into a camera cutout in both orientations instead
        // of showing letterbox bands beside the notch.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Reassign (not mutate in place) so the change survives a future move
            // to after window-attach, where in-place mutation is a silent no-op.
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Insets are applied as MARGINS on the WebView inside the black root, not
        // as WebView padding: WebView (an AbsoluteLayout descendant managing its
        // own rendering surface) has a long history of painting straight across
        // its padded region — the root cause of the earlier failed inset fix.
        // Margins change the view's bounds, which the layout system enforces
        // unconditionally. One maxOf listener covers three states at once:
        //  - immersive fullscreen: bars report 0, only the cutout matters
        //  - split-screen/freeform: immersive is ignored there, bars report real
        //    values again — without this the status-bar overlap bug returns
        //  - keyboard open: the ime inset lifts inputs above the keyboard (the
        //    window never resizes itself once decorFitsSystemWindows is false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            (webView.layoutParams as FrameLayout.LayoutParams).setMargins(
                maxOf(cutout.left, bars.left),
                maxOf(cutout.top, bars.top),
                maxOf(cutout.right, bars.right),
                maxOf(cutout.bottom, bars.bottom, ime.bottom)
            )
            webView.requestLayout()
            WindowInsetsCompat.CONSUMED
        }

        // Serve assets/www at the root of the reserved virtual host. A request to
        // https://appassets.androidplatform.net/www/game/index.html maps to the
        // packaged asset assets/www/game/index.html.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // WebView initializes textZoom to systemFontScale x 100, silently
            // multiplying every CSS px on top of the reader's own persisted
            // A-/A+ font control — two compounding scaling systems, one of them
            // invisible to the app ("fullscreen readjusts text"). Pin to 100 so
            // the APK renders identically to Pages; reading comfort belongs to
            // the in-app control.
            textZoom = 100
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
                    // Nothing resolves ACTION_VIEW: a silent no-op reads as
                    // "the app is broken" — say what happened.
                    toast("No app can open this link")
                    true
                }
            }

            // Keeps the in-app back walk (reader close, quiz pause-guard) but
            // treats Home as the root: at #/home the callback disables itself,
            // back falls through to the system, and predictive back-to-home
            // works. A live quiz is never at #/home, so the guard is intact.
            override fun doUpdateVisitedHistory(
                view: WebView,
                url: String?,
                isReload: Boolean
            ) {
                val frag = url?.substringAfter('#', "") ?: ""
                backCallback.isEnabled =
                    view.canGoBack() && frag.isNotEmpty() && frag != "/home"
                if (frag.startsWith("/") && Uri.parse(url ?: "").host == INTERNAL_HOST) {
                    lastRoute = frag
                }
            }

            // Without this, a crashed WebView renderer (OOM on a huge doc + heavy
            // Mermaid SVG is a realistic trigger) terminates the WHOLE app process
            // by default on API 26+. Detach and destroy the dead WebView, then
            // rebuild the activity; localStorage state survives untouched.
            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                recreate()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Debug builds only: without this the page's console is invisible,
            // so a JS error in the field leaves nothing in a bug report but
            // "the screen was blank". Release keeps chromium's default handling
            // (return false) rather than logging user content.
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return false
                Log.d(
                    "SDWeb",
                    "[${message.messageLevel()}] ${message.message()} " +
                        "(${message.sourceId()}:${message.lineNumber()})"
                )
                return true
            }

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
                    // Import expects a single progress-backup JSON, but file
                    // managers mistag exported backups as generic/binary/text —
                    // accept those, and keep "*/*" as the escape hatch for SAF
                    // providers with odd types (application/x-json) so the file
                    // is never greyed out; the page validates the payload anyway.
                    fileChooserLauncher.launch(
                        arrayOf(
                            "application/json", "application/octet-stream",
                            "text/plain", "*/*"
                        )
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
                // applicationContext, not the Activity: if the popup never
                // navigates (window.open() with no URL, about:blank), the engine
                // retains this WebView as a pending popup indefinitely -- it must
                // not pin the Activity when that happens.
                val tempWebView = WebView(applicationContext)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        childView: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val url = request.url
                        // Internal target="_blank" links stay in the main WebView;
                        // the system browser cannot resolve the virtual host.
                        if (url.host == "appassets.androidplatform.net") {
                            webView.loadUrl(url.toString())
                        } else {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, url))
                            } catch (e: Exception) {
                                toast("No app can open this link")
                            }
                        }
                        // Defer destruction: destroying a WebView synchronously
                        // inside its own client callback is re-entrant and has
                        // crashed on some WebView versions.
                        childView.post { childView.destroy() }
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

        // Hardware/gesture back: walk WebView history while away from Home
        // (enable/disable is driven by doUpdateVisitedHistory above).
        onBackPressedDispatcher.addCallback(this, backCallback)

        // chrome://inspect remote debugging, debug builds only. (Runtime flag
        // check: AGP 8 doesn't generate BuildConfig unless opted in.)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // A renderer crash or a process kill used to dump the reader mid-chapter
        // back at Home. Reopen where the user was; the page restores its own
        // scroll position from sd_reader_scroll once the route loads.
        val restored = savedInstanceState?.getString(KEY_LAST_ROUTE)
        webView.loadUrl(
            if (restored.isNullOrEmpty() || restored == "/home" || !restored.startsWith("/")) START_URL
            else "$START_URL#$restored"
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastRoute?.let { outState.putString(KEY_LAST_ROUTE, it) }
    }

    // Pause the page's timers/rAF when backgrounded (the blitz runs a countdown
    // loop) so the app doesn't burn battery behind the lock screen.
    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    /**
     * Exposed to the page as `window.SDAndroid`. Writes a progress-backup JSON to
     * the shared Downloads collection via MediaStore, which needs no runtime
     * permission on API 29+ (minSdk is 29), then confirms with a Toast.
     */
    private inner class SDAndroid {
        // Returns success synchronously so the page only records/announces a
        // completed export when the file actually exists — otherwise the 30-day
        // backup nudge gets suppressed by a backup that was never written.
        @JavascriptInterface
        fun saveBackup(name: String, json: String): Boolean {
            return try {
                val fileName = if (name.endsWith(".json")) name else "$name.json"
                // The page names backups by date, so exporting twice in one day
                // collides. MediaStore's default answer is to invent
                // "…backup-2026-07-20 (1).json", leaving the user to guess which
                // of several same-day files is current. Overwrite our own row
                // instead. A query here can only ever see rows this app owns
                // (scoped storage, minSdk 29), so a same-named file belonging to
                // anything else is invisible and can never be clobbered.
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                var existing: Uri? = null
                try {
                    // Guarded separately: overwriting is a convenience, and an
                    // OEM MediaStore that throws here must not take down the
                    // export itself — fall through to the plain insert.
                    // IS_PENDING = 0 skips a row orphaned by an export that
                    // died mid-write; reusing one would write a file that
                    // never becomes visible in Files.
                    contentResolver.query(
                        collection,
                        arrayOf(MediaStore.Downloads._ID),
                        "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.IS_PENDING} = 0",
                        arrayOf(fileName),
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) existing = ContentUris.withAppendedId(collection, c.getLong(0))
                    }
                } catch (e: Exception) {
                    existing = null
                }
                existing?.let { uri ->
                    try {
                        // "wt" truncates. Plain "w" would leave the tail of a
                        // longer previous backup behind, producing trailing
                        // garbage after valid JSON.
                        contentResolver.openOutputStream(uri, "wt")?.use { out: OutputStream ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("no output stream")
                        toast("Saved to Downloads/$fileName")
                        return true
                    } catch (e: Exception) {
                        // Row is orphaned (file deleted in Files app) or not
                        // writable — fall through and insert a fresh one.
                    }
                }
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
                    return false
                }
                resolver.openOutputStream(uri)?.use { out: OutputStream ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                // Publish (clear IS_PENDING) so it's visible in Files/Downloads.
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                toast("Saved to Downloads/$fileName")
                true
            } catch (e: Exception) {
                toast("Backup failed: ${e.message}")
                false
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
