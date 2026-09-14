package com.rideminiapp.ui.web

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Base64
import com.rideminiapp.util.AppLog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebStorage
import android.webkit.ValueCallback
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.BuildConfig
import com.rideminiapp.data.local.CookieStore
import com.rideminiapp.data.repository.SessionRepository
import com.rideminiapp.domain.AdminPolicy
import com.rideminiapp.domain.Role
import com.rideminiapp.ui.common.openTelegramHandoff
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.coroutines.resume

@AndroidEntryPoint
class WebAppFragment : Fragment() {
    companion object {
        const val TAG = "WebAppFragment"
        private const val KEY_WEBVIEW_STATE = "webview_state"
        private const val ADMIN_WEB_VERSION = "20260729c"
        fun newInstance(): WebAppFragment = WebAppFragment()
    }

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var cookieStore: CookieStore
    private val stateViewModel: WebAppStateViewModel by activityViewModels()

    private var webView: WebView? = null
    private var loadingOverlay: View? = null
    private var refreshButton: ImageButton? = null
    private var telegramOpened = false
    private var tokenApplied = false
    private var pendingToken: String? = null
    private var webViewStateCleared = false
    private var currentUserResolutionAttempted = false
    private var unauthorizedRedirectTriggered = false
    private var passengerAuthRecoveryInFlight = false
    private var pendingGeolocationCallback: android.webkit.GeolocationPermissions.Callback? = null
    private var pendingGeolocationOrigin: String? = null
    private var geolocationPermissionRequestInFlight = false
    private var pendingPermissionRequest: PermissionRequest? = null
    private var cameraPermissionRequestInFlight = false
    private val isAdminApp get() = BuildConfig.IS_ADMIN_APP

    private val locationPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            geolocationPermissionRequestInFlight = false
            val granted = result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val callback = pendingGeolocationCallback
            val origin = pendingGeolocationOrigin
            pendingGeolocationCallback = null
            pendingGeolocationOrigin = null
            AppLog.d(TAG, "locationPermissionResult granted=$granted origin=$origin")
            callback?.invoke(origin, granted, granted)
            if (isAdded && !isAdminApp) {
                viewLifecycleOwner.lifecycleScope.launch {
                    startFlow()
                }
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraPermissionRequestInFlight = false
            AppLog.d(TAG, "cameraPermissionResult granted=$granted")
            if (granted) {
                val permissionRequest = pendingPermissionRequest
                pendingPermissionRequest = null
                if (permissionRequest != null) {
                    permissionRequest.grant(permissionRequest.resources)
                } else {
                    launchFileChooserWithCamera()
                }
            } else {
                pendingPermissionRequest?.deny()
                pendingPermissionRequest = null
                stateViewModel.clearFileChooserState()
            }
        }

        private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = stateViewModel.pendingFilePathCallback
            stateViewModel.pendingFilePathCallback = null
            val parsedUris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            val uris = when {
                !parsedUris.isNullOrEmpty() -> parsedUris
                result.resultCode != android.app.Activity.RESULT_OK -> null
                result.data?.clipData != null -> {
                    val clipData = result.data!!.clipData!!
                    Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
                }
                result.data?.data != null -> arrayOf(result.data!!.data!!)
                stateViewModel.pendingCameraCaptureUri != null -> arrayOf(stateViewModel.pendingCameraCaptureUri!!)
                else -> null
            }
            stateViewModel.pendingCameraCaptureUri = null
            stateViewModel.fileChooserInFlight = false
            stateViewModel.fileChooserJustReturned = true
            AppLog.d(
                TAG,
                "fileChooserResult resultCode=${result.resultCode} data=${result.data} parsed=${parsedUris?.joinToString()} resolved=${uris?.joinToString()} callbackPresent=${callback != null}",
            )
            if (callback != null) {
                callback.onReceiveValue(uris)
                return@registerForActivityResult
            }
            if (!uris.isNullOrEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val payloads = withContext(Dispatchers.IO) {
                        uris.mapNotNull { uri -> readFilePayload(uri) }
                    }
                    if (payloads.isEmpty()) {
                        AppLog.w(TAG, "fileChooserResult no payloads could be read from URIs=${uris.joinToString()}")
                        return@launch
                    }
                    stateViewModel.pendingFileUploadPayloads = payloads
                    deliverPendingNativeFileUploads()
                }
            }
        }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )

            addView(WebView(context).also { webView = it }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))

            addView(FrameLayout(context).apply {
                setBackgroundColor(0xFFFFFFFF.toInt())
                addView(ProgressBar(context), FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ))
                addView(TextView(context).apply {
                    text = "Opening RIDE..."
                    textSize = 16f
                    setPadding(0, 32, 0, 0)
                }, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL,
                ))
                this@WebAppFragment.loadingOverlay = this
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))

            val density = resources.displayMetrics.density
            addView(ImageButton(context).apply {
                this@WebAppFragment.refreshButton = this
                contentDescription = "Обновить страницу"
                setImageResource(android.R.drawable.ic_popup_sync)
                imageTintList = ColorStateList.valueOf(Color.BLACK)
                setPadding(
                    (12 * density).toInt(),
                    (12 * density).toInt(),
                    (12 * density).toInt(),
                    (12 * density).toInt(),
                )
                elevation = 8 * density
                setOnClickListener {
                    loadingOverlay?.visibility = View.VISIBLE
                    webView?.reload()
                }
            }, FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.START))

            updateRefreshButtonLayout(if (isAdminApp) "/admin" else "/")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        unauthorizedRedirectTriggered = false
        configureWebView()
        if (savedInstanceState == null) {
            clearWebViewStateIfNeeded()
        } else if (restoreWebViewState(savedInstanceState)) {
            val restoredUrl = webView?.url.orEmpty()
            if (isTelegramUrl(restoredUrl)) {
                AppLog.d(TAG, "onViewCreated dropping telegram restore url=$restoredUrl")
                webView?.loadUrl("about:blank")
                loadingOverlay?.visibility = View.VISIBLE
                startFlow()
            } else {
                AppLog.d(TAG, "onViewCreated restored webview state url=$restoredUrl")
                loadingOverlay?.visibility = View.GONE
            }
        } else {
            startFlow()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentWebView = webView
                if (currentWebView?.canGoBack() == true) {
                    currentWebView.goBack()
                    return
                }
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = Bundle()
        val currentUrl = webView?.url.orEmpty()
        if (isTelegramUrl(currentUrl)) {
            AppLog.d(TAG, "onSaveInstanceState skip telegram url=$currentUrl")
        } else {
            webView?.saveState(state)
        }
        outState.putBundle(KEY_WEBVIEW_STATE, state)
    }

    override fun onResume() {
        super.onResume()
        if (stateViewModel.fileChooserInFlight) {
            AppLog.d(TAG, "onResume skipped because file chooser is in flight")
            return
        }
        val chooserUrl = stateViewModel.webUrlBeforeFileChooser
        if (!chooserUrl.isNullOrBlank()) {
            val currentUrl = webView?.url.orEmpty()
            if (currentUrl.isBlank() || currentUrl == "about:blank") {
                AppLog.d(TAG, "onResume restoring page after file chooser chooserUrl=$chooserUrl currentUrl=$currentUrl")
                webView?.loadUrl(chooserUrl)
            } else {
                AppLog.d(TAG, "onResume keeping page after file chooser chooserUrl=$chooserUrl currentUrl=$currentUrl")
            }
            stateViewModel.webUrlBeforeFileChooser = null
            stateViewModel.fileChooserJustReturned = false
            return
        }
        if (stateViewModel.fileChooserJustReturned) {
            stateViewModel.fileChooserJustReturned = false
            stateViewModel.webUrlBeforeFileChooser = null
            AppLog.d(TAG, "onResume file chooser returned, keeping current page")
            return
        }
        val currentUrl = webView?.url.orEmpty()
        if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
            AppLog.d(TAG, "onResume kept existing web content currentUrl=$currentUrl")
            return
        }
        val fallbackUrl = stateViewModel.lastKnownWebUrl.orEmpty()
        if (fallbackUrl.isNotBlank() && fallbackUrl != "about:blank") {
            AppLog.d(TAG, "onResume restoring last known page fallbackUrl=$fallbackUrl")
            webView?.loadUrl(fallbackUrl)
            return
        }
        AppLog.d(TAG, "onResume falling back to startFlow currentUrl=$currentUrl fallbackUrl=$fallbackUrl")
        startFlow()
    }

    override fun onDestroyView() {
        pendingGeolocationCallback = null
        pendingGeolocationOrigin = null
        geolocationPermissionRequestInFlight = false
        pendingPermissionRequest?.deny()
        pendingPermissionRequest = null
        cameraPermissionRequestInFlight = false
        webView?.destroy()
        webView = null
        loadingOverlay = null
        refreshButton = null
        super.onDestroyView()
    }

    private fun updateRefreshButtonLayout(url: String?) {
        val button = refreshButton ?: return
        val density = resources.displayMetrics.density
        val normalizedUrl = url?.lowercase().orEmpty()
        val isDriverPage = !isAdminApp && normalizedUrl.contains("/driver")
        val sizeDp = if (isDriverPage) 48 else 40
        val startDp = when {
            isAdminApp -> 12
            isDriverPage -> 72
            else -> 60
        }
        val topDp = when {
            isAdminApp -> 8
            isDriverPage -> 40
            else -> 38
        }
        val paddingDp = if (isDriverPage) 12 else 10

        button.setPadding(
            (paddingDp * density).toInt(),
            (paddingDp * density).toInt(),
            (paddingDp * density).toInt(),
            (paddingDp * density).toInt(),
        )
        button.background = GradientDrawable().apply {
            if (isDriverPage) {
                cornerRadius = 16 * density
            } else {
                shape = GradientDrawable.OVAL
            }
            setColor(Color.WHITE)
            setStroke((density).toInt().coerceAtLeast(1), 0x22000000)
        }
        button.layoutParams = FrameLayout.LayoutParams(
            (sizeDp * density).toInt(),
            (sizeDp * density).toInt(),
            Gravity.TOP or Gravity.START,
        ).apply {
            marginStart = (startDp * density).toInt()
            topMargin = (topDp * density).toInt()
        }
    }

    private fun alignRefreshButtonToWebRow(view: WebView) {
        val js = if (isAdminApp) {
            """
                (function() {
                  var header = document.querySelector('.admin-dashboard-header');
                  if (!header) return null;
                  var buttons = Array.prototype.slice.call(header.querySelectorAll('button'));
                  for (var i = 0; i < buttons.length; i++) {
                    var rect = buttons[i].getBoundingClientRect();
                    if (rect.width >= 30 && rect.height >= 30) return [rect.top, rect.height];
                  }
                  var headerRect = header.getBoundingClientRect();
                  return [headerRect.top + Math.max(0, (headerRect.height - 40) / 2), 40];
                })();
            """.trimIndent()
        } else {
            """
                (function() {
                  var buttons = Array.prototype.slice.call(document.querySelectorAll('button'));
                  var matches = [];
                  for (var i = 0; i < buttons.length; i++) {
                    var rect = buttons[i].getBoundingClientRect();
                    if (rect.left >= 0 && rect.left < 100 && rect.top >= 0 && rect.top < 220 &&
                        rect.width >= 36 && rect.width <= 56 && rect.height >= 36 && rect.height <= 56) {
                      matches.push(rect);
                    }
                  }
                  matches.sort(function(a, b) { return a.left - b.left || a.top - b.top; });
                  return matches.length ? [matches[0].top, matches[0].height] : null;
                })();
            """.trimIndent()
        }

        view.evaluateJavascript(js) { value ->
            if (value.isNullOrBlank() || value == "null") return@evaluateJavascript
            runCatching {
                val row = JSONArray(value)
                val topCssPx = row.getDouble(0)
                val heightCssPx = row.getDouble(1)
                val button = refreshButton ?: return@runCatching
                val density = resources.displayMetrics.density
                val current = button.layoutParams as? FrameLayout.LayoutParams ?: return@runCatching
                current.topMargin = (topCssPx * density).toInt()
                current.height = (heightCssPx * density).toInt()
                current.width = (heightCssPx * density).toInt()
                button.layoutParams = current
                AppLog.d(TAG, "refreshButtonAligned top=$topCssPx size=$heightCssPx")
            }.onFailure { error ->
                AppLog.w(TAG, "refreshButton alignment failed value=$value", error)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val webView = webView ?: return
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setGeolocationEnabled(true)
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.mixedContentMode = if (BuildConfig.DEBUG) {
            WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        } else {
            WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        settings.userAgentString = "${settings.userAgentString} RideMiniApp/1.0"

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                AppLog.d(TAG, "onPageStarted url=$url")
            if (url != null && isTelegramUrl(url)) {
                AppLog.d(TAG, "onPageStarted blocking telegram url=$url")
                view?.stopLoading()
                if (isAdminApp) {
                    loadingOverlay?.visibility = View.GONE
                    return
                }
                loadingOverlay?.visibility = View.VISIBLE
                return
            }
                super.onPageStarted(view, url, favicon)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                AppLog.d(TAG, "shouldOverrideUrlLoading request=$url")
                return handleUrl(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val parsed = url?.let(Uri::parse) ?: return false
                AppLog.d(TAG, "shouldOverrideUrlLoading legacy=$parsed")
                return handleUrl(parsed)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?,
            ) {
                AppLog.e(TAG, "webError url=${request?.url} code=${error?.errorCode} desc=${error?.description}")
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?,
            ) {
                AppLog.e(TAG, "httpError url=${request?.url} status=${errorResponse?.statusCode} reason=${errorResponse?.reasonPhrase}")
                val requestPath = request?.url?.path.orEmpty()
                if (!isAdminApp &&
                    errorResponse?.statusCode == 401 &&
                    requestPath.startsWith("/api/admin/")
                ) {
                    AppLog.d(TAG, "httpError 401 ignored for user app admin endpoint path=$requestPath")
                    super.onReceivedHttpError(view, request, errorResponse)
                    return
                }
                if (!isAdminApp &&
                    errorResponse?.statusCode == 401 &&
                    requestPath.contains("/api/")
                ) {
                    val storedPassengerToken = sessionRepository.loadState().passengerToken
                    if (!storedPassengerToken.isNullOrBlank()) {
                        AppLog.d(TAG, "httpError 401 passenger token exists -> keep page and reapply token requestPath=$requestPath")
                        view?.post { applyPersistedPassengerTokenIfNeeded(view) }
                        super.onReceivedHttpError(view, request, errorResponse)
                        return
                    }
                    AppLog.d(TAG, "httpError 401 -> telegram handoff requestPath=$requestPath")
                    sessionRepository.logoutAll()
                    redirectToTelegramAndFinish("http-401 ${request?.url}")
                    return
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                AppLog.d(TAG, "onPageFinished url=$url pendingToken=${pendingToken != null}")
                loadingOverlay?.visibility = View.GONE
                updateRefreshButtonLayout(url)
                val pageView = view ?: return
                pageView.postDelayed({ alignRefreshButtonToWebRow(pageView) }, 250)
                pageView.postDelayed({ alignRefreshButtonToWebRow(pageView) }, 1_000)
                if (!url.isNullOrBlank() && url != "about:blank") {
                    stateViewModel.lastKnownWebUrl = url
                }
                applyPendingTokenIfNeeded(pageView)
                applyPersistedPassengerTokenIfNeeded(pageView)
                applyPassengerInsufficientPointsBanner(pageView, url)
                applyAdminMobileOverlayFix(pageView, url)
                deliverPendingNativeFileUploads()
                pageView.evaluateJavascript(
                    "(function(){try{return JSON.stringify({title:document.title,url:location.href})}catch(e){return 'n/a'}})();",
                    { value -> AppLog.d(TAG, "pageSnapshot=$value") }
                )
                pageView.evaluateJavascript(
                    """
                        (function() {
                          try {
                            var root = document.getElementById('root');
                            var body = document.body;
                            var docEl = document.documentElement;
                            var rootRect = root ? root.getBoundingClientRect() : null;
                            var bodyRect = body ? body.getBoundingClientRect() : null;
                            return JSON.stringify({
                              innerWidth: window.innerWidth,
                              innerHeight: window.innerHeight,
                              docClientWidth: docEl ? docEl.clientWidth : null,
                              docClientHeight: docEl ? docEl.clientHeight : null,
                              bodyClientWidth: body ? body.clientWidth : null,
                              bodyClientHeight: body ? body.clientHeight : null,
                              rootRect: rootRect ? {
                                x: rootRect.x,
                                y: rootRect.y,
                                width: rootRect.width,
                                height: rootRect.height,
                              } : null,
                              bodyRect: bodyRect ? {
                                x: bodyRect.x,
                                y: bodyRect.y,
                                width: bodyRect.width,
                                height: bodyRect.height,
                              } : null,
                            });
                          } catch (e) {
                            return 'n/a';
                          }
                        })();
                    """.trimIndent(),
                    { value -> AppLog.d(TAG, "pageMetrics=$value") }
                )
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                if (filePathCallback == null) return false
                AppLog.d(
                    TAG,
                    "onShowFileChooser currentUrl=${this@WebAppFragment.webView?.url} acceptTypes=${fileChooserParams?.acceptTypes?.joinToString()} mode=${fileChooserParams?.mode}",
                )
                stateViewModel.pendingFilePathCallback?.onReceiveValue(null)
                stateViewModel.pendingFilePathCallback = filePathCallback
                stateViewModel.pendingCameraCaptureUri = null
                stateViewModel.fileChooserInFlight = true
                stateViewModel.fileChooserJustReturned = false
                stateViewModel.webUrlBeforeFileChooser = this@WebAppFragment.webView?.url
                stateViewModel.lastKnownWebUrl = this@WebAppFragment.webView?.url

                val wantsCamera = shouldOfferCamera(fileChooserParams)
                if (wantsCamera && !hasCameraPermission() && !cameraPermissionRequestInFlight) {
                    cameraPermissionRequestInFlight = true
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    return true
                }

                launchFileChooserWithCamera()
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?,
            ) {
                if (isAdminApp) {
                    AppLog.d(TAG, "geolocationPrompt origin=$origin -> granted (admin app)")
                    callback?.invoke(origin, true, false)
                    return
                }

                val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (fineGranted || coarseGranted) {
                    AppLog.d(TAG, "geolocationPrompt origin=$origin -> granted (permission already granted)")
                    callback?.invoke(origin, true, true)
                    return
                }

                AppLog.d(TAG, "geolocationPrompt origin=$origin -> requesting runtime permission")
                pendingGeolocationCallback = callback
                pendingGeolocationOrigin = origin
                if (!geolocationPermissionRequestInFlight) {
                    geolocationPermissionRequestInFlight = true
                    locationPermissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                val permissionRequest = request ?: return
                val wantsCamera = permissionRequest.resources.any {
                    it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                }
                if (!wantsCamera) {
                    permissionRequest.deny()
                    return
                }
                if (isAdminApp) {
                    permissionRequest.deny()
                    return
                }
                if (hasCameraPermission()) {
                    permissionRequest.grant(permissionRequest.resources)
                    return
                }
                if (!cameraPermissionRequestInFlight) {
                    cameraPermissionRequestInFlight = true
                    pendingPermissionRequest = permissionRequest
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            }

            override fun onGeolocationPermissionsHidePrompt() {
                AppLog.d(TAG, "geolocationPrompt hidden")
                super.onGeolocationPermissionsHidePrompt()
            }

            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                AppLog.d(
                    TAG,
                    "console level=${consoleMessage?.messageLevel()} line=${consoleMessage?.lineNumber()} source=${consoleMessage?.sourceId()} message=${consoleMessage?.message()}",
                )
                return super.onConsoleMessage(consoleMessage)
            }
        }
    }

    private fun startFlow() {
        val state = sessionRepository.loadState()
        val token = state.passengerToken?.takeIf { it.isNotBlank() }
        val targetUrl = state.apiBaseUrl.ifBlank { BuildConfig.DEFAULT_API_BASE_URL }.trimEnd('/')
        val isChiefAdmin = AdminPolicy.isChiefAdminUsername(state.currentUsername)
        val isBlockedFromAdmin = AdminPolicy.isBlockedFromAdmin(state.currentUsername)
        val hasAdminAccess = state.adminCookieReady || isChiefAdmin || state.role == Role.ADMIN
        AppLog.d(
            TAG,
            "startFlow variant=${BuildConfig.APP_VARIANT} username=${state.currentUsername} role=${state.role} passengerToken=${token != null} driverCookie=${state.driverCookieReady} adminCookie=${state.adminCookieReady} chiefAdmin=$isChiefAdmin blockedFromAdmin=$isBlockedFromAdmin hasAdminAccess=$hasAdminAccess",
        )

        if (!isAdminApp && !hasLocationPermission() && !geolocationPermissionRequestInFlight) {
            AppLog.d(TAG, "branch=location permission preflight")
            loadingOverlay?.visibility = View.VISIBLE
            geolocationPermissionRequestInFlight = true
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return
        }

        if (!state.currentUsername.isNullOrBlank() || token.isNullOrBlank() || currentUserResolutionAttempted) {
            // No-op, continue below.
        } else {
            currentUserResolutionAttempted = true
            loadingOverlay?.visibility = View.VISIBLE
            viewLifecycleOwner.lifecycleScope.launch {
                val currentUser = withContext(Dispatchers.IO) {
                    sessionRepository.refreshCurrentUserFromPassengerToken()
                }
                if (currentUser == null && !isAdminApp) {
                    if (state.passengerToken.isNullOrBlank()) {
                        AppLog.d(TAG, "branch=telegram handoff because passenger token is missing")
                        sessionRepository.logoutAll()
                        redirectToTelegramAndFinish("missing-passenger-token")
                    } else {
                        AppLog.d(TAG, "branch=passenger session refresh failed but token exists, keeping session")
                        currentUserResolutionAttempted = true
                        startFlow()
                    }
                    return@launch
                }
                startFlow()
            }
            return
        }

        if (isAdminApp) {
            val adminDashboardUrl = Uri.parse(targetUrl)
                .buildUpon()
                .appendPath("admin-page.html")
                .appendQueryParameter("adminMode", "1")
                .appendQueryParameter("native", "1")
                .appendQueryParameter("v", ADMIN_WEB_VERSION)
                .build()
                .toString()
            val currentAdminUrl = webView?.url.orEmpty()
            AppLog.d(TAG, "branch=admin launchUrl=$adminDashboardUrl currentUrl=$currentAdminUrl")
            pendingToken = null
            tokenApplied = false
            loadingOverlay?.visibility = View.VISIBLE
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    if (!state.adminCookieReady) {
                        AppLog.d(TAG, "admin bootstrap -> login with built-in key")
                        val adminKey = BuildConfig.ADMIN_KEY.trim()
                        if (adminKey.isBlank()) {
                            throw IllegalStateException("ADMIN_KEY is not configured in local.properties")
                        }
                        sessionRepository.loginAdminWithKey(adminKey)
                    }
                    syncAdminCookies(targetUrl)
                    webView?.loadUrl(adminDashboardUrl)
                } catch (err: Throwable) {
                    AppLog.e(TAG, "admin bootstrap failed", err)
                    webView?.loadUrl(adminDashboardUrl)
                }
            }
            return
        }

        if (token.isNullOrBlank()) {
            AppLog.d(TAG, "branch=telegram handoff")
            loadingOverlay?.visibility = View.VISIBLE
            redirectToTelegramAndFinish("missing-token")
            return
        }

        val currentUrl = webView?.url.orEmpty()
        if (!isAdminApp && currentUrl.startsWith(targetUrl)) {
            AppLog.d(TAG, "branch=frontend preserve current route currentUrl=$currentUrl")
            loadingOverlay?.visibility = View.GONE
            return
        }

        val launchUrl = buildFrontendLaunchUrl(targetUrl, token)
        AppLog.d(TAG, "branch=frontend launchUrl=$launchUrl currentUrl=${webView?.url}")
        if (pendingToken == token && webView?.url == launchUrl) {
            return
        }

        pendingToken = token
        tokenApplied = false
        loadingOverlay?.visibility = View.VISIBLE
        webView?.loadUrl(launchUrl)
    }

    private fun buildAdminLaunchUrl(baseUrl: String): String {
        return Uri.parse(baseUrl)
            .buildUpon()
            .appendPath("admin-page.html")
            .appendQueryParameter("adminMode", "1")
            .appendQueryParameter("native", "1")
            .appendQueryParameter("v", ADMIN_WEB_VERSION)
            .build()
            .toString()
    }

    private fun clearWebViewStateIfNeeded() {
        if (webViewStateCleared) return
        webViewStateCleared = true
        webView?.clearHistory()
        webView?.clearCache(true)
    }

    private fun restoreWebViewState(savedInstanceState: Bundle): Boolean {
        val state = savedInstanceState.getBundle(KEY_WEBVIEW_STATE) ?: return false
        val restored = webView?.restoreState(state) != null
        AppLog.d(TAG, "restoreWebViewState restored=$restored url=${webView?.url}")
        return restored
    }

    private fun isTelegramUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return when (parsed.scheme?.lowercase(Locale.US)) {
            "tg" -> true
            "http", "https" -> {
                val host = parsed.host?.lowercase(Locale.US).orEmpty()
                host == "t.me" || host == "telegram.me" || host == "telegram.dog"
            }
            else -> false
        }
    }

    private fun hasLocationPermission(): Boolean {
        val context = context ?: return false
        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun hasCameraPermission(): Boolean {
        val context = context ?: return false
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun shouldOfferCamera(params: WebChromeClient.FileChooserParams?): Boolean {
        val acceptTypes = params?.acceptTypes?.filter { it.isNotBlank() }.orEmpty()
        if (acceptTypes.isEmpty()) return true
        return acceptTypes.any { acceptType ->
            val normalized = acceptType.lowercase()
            normalized == "*/*" ||
                normalized.startsWith("image/") ||
                normalized.contains("image") ||
                normalized.contains("camera")
        }
    }

    private fun launchFileChooserWithCamera() {
        val callback = stateViewModel.pendingFilePathCallback ?: return
        val context = context ?: run {
            callback.onReceiveValue(null)
            stateViewModel.pendingFilePathCallback = null
            stateViewModel.fileChooserInFlight = false
            stateViewModel.fileChooserJustReturned = true
            return
        }
        AppLog.d(TAG, "launchFileChooserWithCamera currentUrl=${webView?.url} hasCallback=${callback != null}")

        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        val initialIntents = mutableListOf<Intent>()
        val cameraIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        val cameraUri = createCameraCaptureUri(context)
        if (cameraUri != null) {
            stateViewModel.pendingCameraCaptureUri = cameraUri
            cameraIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri)
            cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            initialIntents += cameraIntent
        }

        val chooser = Intent.createChooser(pickIntent, "Select file").apply {
            if (initialIntents.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toTypedArray())
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        fileChooserLauncher.launch(chooser)
    }

    private fun createCameraCaptureUri(context: android.content.Context): Uri? {
        return runCatching {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File.createTempFile("ride_camera_", ".jpg", dir)
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        }.getOrNull()
    }

    private suspend fun readFilePayload(uri: Uri): WebAppStateViewModel.FileUploadPayload? {
        val context = context ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val fileName = resolveDisplayName(context, uri)
                val mimeType = context.contentResolver.getType(uri)
                    ?: guessMimeType(fileName)
                    ?: "application/octet-stream"
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArrayOutputStream()
                    val temp = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(temp)
                        if (read <= 0) break
                        buffer.write(temp, 0, read)
                    }
                    buffer.toByteArray()
                } ?: return@runCatching null
                WebAppStateViewModel.FileUploadPayload(
                    name = fileName,
                    mimeType = mimeType,
                    base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                )
            }.getOrNull()
        }
    }

    private fun resolveDisplayName(context: android.content.Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)
                } else {
                    null
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "ride_upload")
    }

    private fun guessMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.US)
        if (extension.isBlank()) return null
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun buildNativeFileBridgeScript(payloads: List<WebAppStateViewModel.FileUploadPayload>): String {
        val jsonPayloads = JSONArray().apply {
            payloads.forEach { payload ->
                put(
                    JSONObject().apply {
                        put("name", payload.name)
                        put("mimeType", payload.mimeType)
                        put("base64", payload.base64)
                    },
                )
            }
        }

        return """
            (function() {
              try {
                var payloads = $jsonPayloads;
                var input = document.querySelector('[data-ride-file-input="1"]');
                if (!input) {
                  console.warn('[RideNativeFileBridge] file input not found');
                  return false;
                }
                var dt = new DataTransfer();
                payloads.forEach(function(payload) {
                  var binary = atob(payload.base64);
                  var bytes = new Uint8Array(binary.length);
                  for (var i = 0; i < binary.length; i++) {
                    bytes[i] = binary.charCodeAt(i);
                  }
                  var file = new File([bytes], payload.name, { type: payload.mimeType || 'application/octet-stream' });
                  dt.items.add(file);
                });
                input.files = dt.files;
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                console.info('[RideNativeFileBridge] injected files', payloads.map(function(payload) { return payload.name; }));
                return true;
              } catch (e) {
                console.error('[RideNativeFileBridge] ' + (e && e.message ? e.message : String(e)));
                return false;
              }
            })();
        """.trimIndent()
    }

    private fun deliverPendingNativeFileUploads() {
        val payloads = stateViewModel.pendingFileUploadPayloads ?: return
        val webView = webView ?: return
        if (payloads.isEmpty()) {
            stateViewModel.pendingFileUploadPayloads = null
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeat(20) { attempt ->
                val currentPayloads = stateViewModel.pendingFileUploadPayloads ?: return@launch
                val currentWebView = webView ?: return@launch
                val script = buildNativeFileBridgeScript(currentPayloads)
                AppLog.d(
                    TAG,
                    "deliverPendingNativeFileUploads attempt=${attempt + 1} count=${currentPayloads.size} names=${currentPayloads.joinToString { it.name }}",
                )
                val success = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Boolean> { continuation ->
                        currentWebView.evaluateJavascript(script) { value ->
                            val resolved = value?.contains("true") == true
                            AppLog.d(TAG, "deliverPendingNativeFileUploads result=$value success=$resolved")
                            continuation.resume(resolved)
                        }
                    }
                }
                if (success) {
                    stateViewModel.pendingFileUploadPayloads = null
                    return@launch
                }
                delay(250)
            }
            AppLog.w(TAG, "deliverPendingNativeFileUploads exhausted retries without finding file input")
        }
    }

    private fun buildFrontendLaunchUrl(baseUrl: String, token: String): String {
        pendingToken = token
        tokenApplied = false
        return Uri.parse(baseUrl)
            .buildUpon()
            .appendQueryParameter("native", "1")
            .appendQueryParameter("v", "20260727-camera-one-tap")
            .build()
            .toString()
    }

    private fun applyPendingTokenIfNeeded(view: WebView) {
        if (isAdminApp) return
        val token = pendingToken ?: return
        if (tokenApplied) return

        tokenApplied = true
        val escapedToken = JSONObject.quote(token)
        val js = """
            (function() {
              try {
                var t = $escapedToken;
                window.localStorage.setItem('ride_access_token', t);
                window.localStorage.setItem('access_token', t);
                window.localStorage.setItem('accessToken', t);
                window.localStorage.setItem('token', t);
                window.sessionStorage.setItem('ride_access_token', t);
                window.sessionStorage.setItem('access_token', t);
                window.sessionStorage.setItem('accessToken', t);
                window.sessionStorage.setItem('token', t);
                window.__RIDE_ACCESS_TOKEN__ = t;
              } catch (e) {}
            })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
        view.post {
            loadingOverlay?.visibility = View.GONE
            if (view.url?.startsWith(sessionRepository.loadState().apiBaseUrl.ifBlank { BuildConfig.DEFAULT_API_BASE_URL }.trimEnd('/')) == true) {
                view.reload()
            }
        }
    }

    private fun applyPersistedPassengerTokenIfNeeded(view: WebView) {
        if (isAdminApp) return
        val persistedToken = sessionRepository.loadState().passengerToken?.takeIf { it.isNotBlank() } ?: return
        val currentUrl = view.url.orEmpty()
        val baseUrl = sessionRepository.loadState().apiBaseUrl.ifBlank { BuildConfig.DEFAULT_API_BASE_URL }.trimEnd('/')
        if (!currentUrl.startsWith(baseUrl)) return

        val escapedToken = JSONObject.quote(persistedToken)
        val js = """
            (function() {
              try {
                var t = $escapedToken;
                window.localStorage.setItem('ride_access_token', t);
                window.localStorage.setItem('access_token', t);
                window.localStorage.setItem('accessToken', t);
                window.localStorage.setItem('token', t);
                window.sessionStorage.setItem('ride_access_token', t);
                window.sessionStorage.setItem('access_token', t);
                window.sessionStorage.setItem('accessToken', t);
                window.sessionStorage.setItem('token', t);
                window.__RIDE_ACCESS_TOKEN__ = t;
              } catch (e) {}
            })();
        """.trimIndent()

        AppLog.d(TAG, "applyPersistedPassengerTokenIfNeeded url=$currentUrl tokenSet=true")
        view.evaluateJavascript(js, null)
    }

    private fun applyPassengerInsufficientPointsBanner(view: WebView, url: String?) {
        if (isAdminApp) return
        val normalizedUrl = url?.lowercase().orEmpty()
        if (normalizedUrl.contains("/driver")) return

        val js = """
            (function() {
              try {
                if (window.__rideInsufficientPointsBannerInstalled) return;
                window.__rideInsufficientPointsBannerInstalled = true;

                var state = { balance: null, ridePoints: null };
                var bannerId = 'ride-insufficient-points-banner';

                function findBuyPointsCard() {
                  var nodes = document.querySelectorAll('p, span, div');
                  for (var i = 0; i < nodes.length; i++) {
                    var text = (nodes[i].textContent || '').trim().toLowerCase();
                    if (text === 'купите поинты' || text === 'buy points') {
                      return nodes[i].closest('.mx-3.mb-2') || nodes[i].closest('[class*="shadow-card"]');
                    }
                  }
                  return null;
                }

                function findBottomSheet() {
                  var nodes = document.querySelectorAll('div');
                  for (var i = 0; i < nodes.length; i++) {
                    var style = window.getComputedStyle(nodes[i]);
                    if (style.position === 'absolute' && style.bottom === '0px' &&
                        nodes[i].className && String(nodes[i].className).indexOf('flex-col') >= 0) {
                      return nodes[i];
                    }
                  }
                  return null;
                }

                function render() {
                  var existing = document.getElementById(bannerId);
                  var shouldShow = Number.isFinite(state.balance) && Number.isFinite(state.ridePoints) &&
                    state.balance < state.ridePoints;
                  if (!shouldShow) {
                    if (existing) existing.remove();
                    return;
                  }
                  if (existing && existing.isConnected) return;

                  var banner = document.createElement('div');
                  banner.id = bannerId;
                  banner.setAttribute('role', 'alert');
                  banner.style.cssText = 'margin:0 12px 8px;display:flex;align-items:center;gap:10px;background:#fff;border:1px solid #e6e6e6;border-radius:12px;box-shadow:0 4px 16px rgba(0,0,0,.10);padding:10px 12px;color:#000;font:600 12px/1.35 Inter,Arial,sans-serif;';
                  banner.innerHTML = '<span style="color:#ef4444;font-size:16px;line-height:1" aria-hidden="true">!</span><span>у вас не достаточно поинтов, сгенерируете кюар код для водителя в профиле</span>';

                  var buyCard = findBuyPointsCard();
                  if (buyCard && buyCard.parentNode) {
                    buyCard.parentNode.insertBefore(banner, buyCard);
                    return;
                  }
                  var sheet = findBottomSheet();
                  if (sheet) sheet.insertBefore(banner, sheet.firstChild);
                }

                var originalFetch = window.fetch.bind(window);
                window.fetch = async function(input, init) {
                  var response = await originalFetch(input, init);
                  try {
                    var requestUrl = typeof input === 'string' ? input : (input && input.url) || '';
                    if (requestUrl.indexOf('/api/ride-quote') >= 0 && response.ok) {
                      response.clone().json().then(function(data) {
                        state.ridePoints = Number(data && data.points);
                        render();
                      }).catch(function() {});
                    }
                    if (requestUrl.indexOf('/api/users/me/cabinet') >= 0 && response.ok) {
                      response.clone().json().then(function(data) {
                        state.balance = Number(data && data.pointsBalance);
                        render();
                      }).catch(function() {});
                    }
                  } catch (e) {}
                  return response;
                };

                function refreshBalance() {
                  var token = localStorage.getItem('ride_access_token') || localStorage.getItem('access_token') || '';
                  var headers = token ? { Authorization: 'Bearer ' + token } : {};
                  originalFetch('/api/users/me/cabinet?limit=1&offset=0', { headers: headers })
                    .then(function(response) { return response.ok ? response.json() : null; })
                    .then(function(data) {
                      if (!data) return;
                      state.balance = Number(data.pointsBalance);
                      render();
                    }).catch(function() {});
                }

                var observer = new MutationObserver(function() { render(); });
                observer.observe(document.documentElement, { childList: true, subtree: true });
                refreshBalance();
                window.setInterval(refreshBalance, 10000);
              } catch (e) {}
            })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }

    private fun applyAdminMobileOverlayFix(view: WebView, url: String?) {
        if (!isAdminApp) return
        val normalizedUrl = url?.lowercase().orEmpty()
        if (!normalizedUrl.contains("/admin")) return

        val js = """
            (function() {
              try {
                var styleId = 'ride-admin-mobile-overlay-fix';
                var existing = document.getElementById(styleId);
                if (!existing) {
                  var style = document.createElement('style');
                  style.id = styleId;
                  style.textContent = '@media (max-width: 768px) { .admin-drawer-scrim { display: none !important; pointer-events: none !important; } .admin-dashboard-header > div:first-child { margin-left: 44px !important; } }';
                  (document.head || document.documentElement).appendChild(style);
                }
                var scrims = document.querySelectorAll('.admin-drawer-scrim');
                for (var i = 0; i < scrims.length; i++) {
                  scrims[i].style.display = 'none';
                  scrims[i].style.pointerEvents = 'none';
                }
              } catch (e) {}
            })();
        """.trimIndent()

        AppLog.d(TAG, "applyAdminMobileOverlayFix url=$normalizedUrl")
        view.evaluateJavascript(js, null)
    }

    private fun applyAdminZoneDrawingControls(view: WebView, url: String?) {
        if (!isAdminApp) return
        val normalizedUrl = url?.lowercase().orEmpty()
        if (!normalizedUrl.contains("/admin")) return

        val js = """
            (function() {
              try {
                if (window.__rideAdminZoneControlsInstalled) return;
                window.__rideAdminZoneControlsInstalled = true;

                var controls = document.createElement('div');
                controls.id = 'ride-admin-zone-controls';
                controls.style.cssText = 'display:none;position:fixed;z-index:2147483000;gap:8px;align-items:center;transform:translateX(-50%);';

                var undo = document.createElement('button');
                undo.type = 'button';
                undo.textContent = 'Отменить';
                undo.style.cssText = 'height:44px;padding:0 18px;border:1px solid #e5e5e5;border-radius:14px;background:#fff;color:#000;font:700 13px Inter,Arial,sans-serif;box-shadow:0 4px 16px rgba(0,0,0,.16);';

                var save = document.createElement('button');
                save.type = 'button';
                save.textContent = 'Сохранить';
                save.style.cssText = 'height:44px;padding:0 18px;border:0;border-radius:14px;background:#000;color:#fff;font:700 13px Inter,Arial,sans-serif;box-shadow:0 4px 16px rgba(0,0,0,.16);';

                controls.appendChild(undo);
                controls.appendChild(save);
                (document.body || document.documentElement).appendChild(controls);

                function allButtons() {
                  return Array.prototype.slice.call(document.querySelectorAll('button'));
                }

                function findDrawingHint() {
                  var nodes = document.querySelectorAll('div, p, span');
                  var best = null;
                  var bestArea = Number.POSITIVE_INFINITY;
                  for (var i = 0; i < nodes.length; i++) {
                    var text = (nodes[i].textContent || '').trim().toLowerCase();
                    if ((text.indexOf('вершины зоны (') >= 0 ||
                         text.indexOf('zone vertices (') >= 0 ||
                         text.indexOf('wierzcholki strefy (') >= 0 ||
                         text.indexOf('zonos virsunes (') >= 0) &&
                        nodes[i].children.length <= 1) {
                      var rect = nodes[i].getBoundingClientRect();
                      if (rect.width > 0 && rect.height > 0 && rect.height <= 80) {
                        var area = rect.width * rect.height;
                        if (area < bestArea) {
                          best = nodes[i];
                          bestArea = area;
                        }
                      }
                    }
                  }
                  return best;
                }

                function findOriginalUndo() {
                  var buttons = allButtons();
                  for (var i = 0; i < buttons.length; i++) {
                    if (buttons[i] === undo || buttons[i] === save) continue;
                    var text = (buttons[i].textContent || '').trim().toLowerCase();
                    var title = (buttons[i].getAttribute('title') || '').trim().toLowerCase();
                    if (text === 'отменить точку' || title === 'отменить точку' ||
                        text === 'undo point' || title === 'undo point' ||
                        text === 'cofnij punkt' || title === 'cofnij punkt' ||
                        text === 'anuliuoti taska' || title === 'anuliuoti taska') return buttons[i];
                  }
                  return null;
                }

                function findOriginalSave() {
                  var buttons = allButtons();
                  for (var i = 0; i < buttons.length; i++) {
                    if (buttons[i] === undo || buttons[i] === save) continue;
                    var text = (buttons[i].textContent || '').trim().toLowerCase();
                    if (text === 'сохранить зону' || text === 'save zone' ||
                        text === 'zapisz strefe' || text === 'issaugoti zona') return buttons[i];
                  }
                  return null;
                }

                undo.addEventListener('click', function() {
                  var original = findOriginalUndo();
                  if (original) original.click();
                });
                save.addEventListener('click', function() {
                  var original = findOriginalSave();
                  if (original && !original.disabled) original.click();
                });

                function update() {
                  var hint = findDrawingHint();
                  if (!hint) {
                    controls.style.display = 'none';
                    return;
                  }
                  var hintText = (hint.textContent || '');
                  var match = hintText.match(/\((\d+)\)/);
                  var count = match ? Number(match[1]) : 0;
                  var rect = hint.getBoundingClientRect();
                  controls.style.display = 'flex';
                  controls.style.left = Math.max(130, Math.min(window.innerWidth - 130, rect.left + rect.width / 2)) + 'px';
                  var targetTop = rect.bottom + 48;
                  if (!Number.isFinite(targetTop) || targetTop < 90 || targetTop > window.innerHeight - 120) {
                    targetTop = 220;
                  }
                  controls.style.top = Math.min(window.innerHeight - 120, targetTop) + 'px';

                  var originalUndo = findOriginalUndo();
                  if (originalUndo) originalUndo.style.display = 'none';
                  undo.disabled = count < 1 || !originalUndo;
                  undo.style.opacity = undo.disabled ? '.45' : '1';

                  var originalSave = findOriginalSave();
                  save.disabled = count < 3 || !originalSave || originalSave.disabled;
                  save.style.opacity = save.disabled ? '.45' : '1';
                }

                update();
                window.setInterval(update, 300);
              } catch (e) {}
            })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }

    private suspend fun syncAdminCookies(baseUrl: String) {
        val requestUrl = baseUrl.toHttpUrl()
        val webUrl = requestUrl.newBuilder().encodedPath("/").build().toString()
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieStore.loadForRequest(requestUrl)
        AppLog.d(TAG, "syncAdminCookies baseUrl=$baseUrl cookieCount=${cookies.size} webUrl=$webUrl")
        cookies.forEach { cookie ->
            val cookieValue = buildString {
                append(cookie.name)
                append('=')
                append(cookie.value)
                append("; Path=")
                append(cookie.path)
                if (!cookie.hostOnly) {
                    append("; Domain=")
                    append(cookie.domain)
                }
                if (cookie.secure) append("; Secure")
                if (cookie.httpOnly) append("; HttpOnly")
            }
            AppLog.d(TAG, "syncAdminCookies setCookie name=${cookie.name} path=${cookie.path} domain=${cookie.domain} secure=${cookie.secure} httpOnly=${cookie.httpOnly}")
            setCookieAwait(cookieManager, webUrl, cookieValue)
            setCookieAwait(cookieManager, requestUrl.newBuilder().encodedPath("/admin").build().toString(), cookieValue)
        }
        cookieManager.flush()
        AppLog.d(TAG, "syncAdminCookies flushed cookies=${cookieManager.getCookie(webUrl)}")
    }

    private suspend fun setCookieAwait(cookieManager: CookieManager, url: String, cookieValue: String) {
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                cookieManager.setCookie(url, cookieValue) { success ->
                    AppLog.d(TAG, "setCookie callback url=$url success=$success")
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        } catch (err: Throwable) {
            AppLog.e(TAG, "setCookie failed url=$url", err)
        }
    }

    private fun handleUrl(uri: Uri): Boolean {
        if (isTelegramUrl(uri.toString())) {
            if (isAdminApp) {
                AppLog.d(TAG, "handleUrl ignoring telegram url in admin app uri=$uri")
                return true
            }
            if (sessionRepository.hasTelegramHandoffAttempted()) {
                AppLog.d(TAG, "handleUrl blocking repeated telegram handoff uri=$uri")
                return true
            }
            AppLog.d(TAG, "handleUrl telegram handoff uri=$uri")
            sessionRepository.markTelegramHandoffAttempted()
            telegramOpened = true
            openTelegram()
            return true
        }
        if (isAdminApp) {
            val targetBase = sessionRepository.loadState().apiBaseUrl.ifBlank { BuildConfig.DEFAULT_API_BASE_URL }.trimEnd('/')
            val normalized = uri.toString()
            val isAdminRoute = normalized.startsWith(targetBase) && normalized.contains("/admin")
            if (normalized.startsWith(targetBase) && !isAdminRoute) {
                AppLog.d(TAG, "handleUrl forcing admin route uri=$uri")
                webView?.post {
                    webView?.loadUrl(buildAdminLaunchUrl(targetBase))
                }
                return true
            }
        }
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> false
            else -> {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }.recoverCatching {
                    if (uri.scheme == "ride") {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.DEFAULT_HANDOFF_URL)))
                    } else {
                        throw it
                    }
                }
                true
            }
        }
    }

    private fun openTelegram() {
        val handoffUrl = sessionRepository.loadState().handoffUrl.ifBlank { BuildConfig.DEFAULT_HANDOFF_URL }
        openTelegramHandoff(handoffUrl)
    }

    private fun redirectToTelegramAndFinish(reason: String) {
        if (unauthorizedRedirectTriggered) return
        unauthorizedRedirectTriggered = true
        AppLog.d(TAG, "redirectToTelegramAndFinish reason=$reason")
        if (!isAdminApp) {
            loadingOverlay?.visibility = View.VISIBLE
        }
        if (!telegramOpened) {
            sessionRepository.markTelegramHandoffAttempted()
            telegramOpened = true
            openTelegram()
        }
    }

}
