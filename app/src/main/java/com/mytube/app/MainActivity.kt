package com.mytube.app

import android.Manifest
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mytube.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView

    // For the <input type="file"> upload flow (thumbnails, avatars, etc.)
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback
            filePathCallback = null
            if (callback == null) return@registerForActivityResult
            val data = result.data
            val results: Array<Uri>? = when {
                result.resultCode != RESULT_OK -> null
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
            callback.onReceiveValue(results)
        }

    // Camera/mic permission prompts triggered by getUserMedia() on the page
    // (only relevant if MyTube ever adds in-browser recording).
    private val permissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { }

    // Full-screen <video> support (the fullscreen button in the Shorts/watch player)
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = binding.webView
        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()
        setupRetry()

        loadHome()
    }

    private fun loadHome() {
        if (isOnline()) {
            binding.offlineView.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadUrl(getString(R.string.base_url))
        } else {
            showOffline()
        }
    }

    private fun showOffline() {
        binding.offlineView.visibility = View.VISIBLE
        webView.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun setupRetry() {
        binding.retryButton.setOnClickListener { loadHome() }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            if (isOnline()) {
                webView.reload()
            } else {
                showOffline()
            }
        }
        binding.swipeRefresh.setColorSchemeResources(R.color.mytube_red)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false // let muted Shorts autoplay
        settings.allowFileAccess = true
        settings.setSupportZoom(false)
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.userAgentString = settings.userAgentString + " MyTubeAndroidApp/1.0"

        // Persist login cookies/session across app restarts.
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                val scheme = url.scheme
                val host = url.host ?: ""
                val baseHost = getString(R.string.base_url).toUri().host ?: ""

                return when {
                    // Keep normal navigation on our own site inside the WebView.
                    host == baseHost -> false
                    scheme == "http" || scheme == "https" -> {
                        // External link (e.g. a help/docs page) — open in the
                        // system browser instead of inside the app.
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                        true
                    }
                    scheme == "mailto" || scheme == "tel" -> {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                        true
                    }
                    else -> false
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                binding.swipeRefresh.isRefreshing = false
                binding.progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) showOffline()
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                // Do NOT proceed on SSL errors — fail closed for safety.
                handler.cancel()
                showOffline()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                binding.progressBar.progress = newProgress
            }

            // --- <input type="file"> support: thumbnail changes, avatar, etc. ---
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val intent = params.createIntent()
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }

            // --- getUserMedia() permission bridge (camera/mic), if ever used ---
            override fun onPermissionRequest(request: PermissionRequest) {
                val neededAndroidPermissions = request.resources.mapNotNull {
                    when (it) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                        else -> null
                    }
                }
                val missing = neededAndroidPermissions.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
                request.grant(request.resources)
            }

            // --- True full-screen for the HTML5 video fullscreen button ---
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                binding.fullscreenContainer.visibility = View.VISIBLE
                binding.fullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                webView.visibility = View.GONE
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            }

            override fun onHideCustomView() {
                binding.fullscreenContainer.visibility = View.GONE
                binding.fullscreenContainer.removeAllViews()
                webView.visibility = View.VISIBLE
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }

        // --- Downloads: the Shorts "Save" button and the Watch page's
        // download link both trigger a normal browser-style file download. ---
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                val fileName = URLUtilGuessFileName(url, contentDisposition, mimeType)
                request.setMimeType(mimeType)
                // Forward the site's session cookie so the download endpoint
                // recognizes the signed-in user, same as the WebView does.
                val cookie = CookieManager.getInstance().getCookie(url)
                if (cookie != null) request.addRequestHeader("cookie", cookie)
                request.addRequestHeader("User-Agent", userAgent)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
            } catch (e: Exception) {
                // Fall back to letting the browser handle it if DownloadManager fails.
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    private fun URLUtilGuessFileName(url: String, contentDisposition: String?, mimeType: String?): String =
        android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> webView.webChromeClient?.onHideCustomView()
                    webView.canGoBack() -> webView.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        binding.swipeRefresh.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }
}
