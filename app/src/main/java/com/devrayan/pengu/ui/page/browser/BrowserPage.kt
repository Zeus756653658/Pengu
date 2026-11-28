package com.devrayan.pengu.ui.page.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.util.COOKIES
import com.devrayan.pengu.util.DownloadUtil
import com.devrayan.pengu.util.FileUtil
import com.devrayan.pengu.util.FileUtil.getCookiesFile
import com.devrayan.pengu.util.PreferenceUtil.updateBoolean
import com.devrayan.pengu.util.PreferenceUtil.updateString
import com.devrayan.pengu.util.USER_AGENT_STRING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val BrowserQuickBookmarks =
    listOf(
        "YouTube" to "https://m.youtube.com",
        "Instagram" to "https://www.instagram.com",
        "TikTok" to "https://www.tiktok.com",
        "Facebook" to "https://m.facebook.com",
        "Twitter / X" to "https://x.com",
        "SoundCloud" to "https://m.soundcloud.com",
    )

/**
 * In-app WebView Browser Downloader with quick bookmarks, automatic WebView cookies sync,
 * and floating download trigger for grabbing reels/videos while logged in.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserPage(
    initialUrl: String = "https://m.youtube.com",
    onNavigateBack: () -> Unit,
    onDownloadUrl: (String) -> Unit,
) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val startUrl =
        remember(initialUrl) {
            initialUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: "https://m.youtube.com"
        }

    var addressInput by remember { mutableStateOf(startUrl) }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun navigateToInput(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val target =
            when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
                else -> "https://www.google.com/search?q=${android.net.Uri.encode(trimmed)}"
            }
        focusManager.clearFocus()
        webViewRef?.loadUrl(target)
    }

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            val gradient = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFD946EF)))
            ExtendedFloatingActionButton(
                onClick = {
                    view.slightHapticFeedback()
                    val urlToDownload = webViewRef?.url?.takeIf { it.startsWith("http") } ?: currentUrl
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            CookieManager.getInstance().flush()
                            DownloadUtil.getCookiesContentFromDatabase().getOrNull()?.let { content ->
                                if (content.lines().size > 4) {
                                    FileUtil.writeContentToFile(content, context.getCookiesFile())
                                    COOKIES.updateBoolean(true)
                                }
                            }
                        }
                    }
                    onDownloadUrl(urlToDownload)
                },
                containerColor = Color.Transparent,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                modifier =
                    Modifier.clip(RoundedCornerShape(18.dp))
                        .background(gradient)
                        .padding(horizontal = 2.dp),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Download Page", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Address bar + controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        view.slightHapticFeedback()
                        onNavigateBack()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Browser", modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = { webViewRef?.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = { webViewRef?.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        modifier = Modifier.size(18.dp),
                    )
                }

                Row(
                    modifier =
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color(0xFF1E1E24) else Color(0xFFF3F4F6))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        tint = Color(0xFF7C3AED),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        singleLine = true,
                        textStyle =
                            MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                            ),
                        cursorBrush = SolidColor(Color(0xFF7C3AED)),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                        keyboardActions = KeyboardActions(onGo = { navigateToInput(addressInput) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                IconButton(
                    onClick = { webViewRef?.reload() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                }
            }

            // Quick bookmarks strip
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrowserQuickBookmarks.forEach { (title, url) ->
                    val isSelected = currentUrl.contains(url.removePrefix("https://").removePrefix("m.").removePrefix("www."), ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color =
                            if (isSelected) Color(0xFF7C3AED).copy(alpha = 0.16f)
                            else if (isDark) Color(0xFF1E1E24) else Color(0xFFF3F4F6),
                        modifier = Modifier.clickable { webViewRef?.loadUrl(url) },
                    ) {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            if (loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    color = Color(0xFF7C3AED),
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().height(2.5.dp),
                )
            }

            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        USER_AGENT_STRING.updateString(settings.userAgentString)
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val scheme = request?.url?.scheme ?: return false
                                    return scheme != "http" && scheme != "https"
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    url?.let {
                                        currentUrl = it
                                        addressInput = it
                                    }
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    url?.let {
                                        currentUrl = it
                                        addressInput = it
                                    }
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    CookieManager.getInstance().flush()
                                }
                            }

                        webChromeClient =
                            object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }
                            }

                        webViewRef = this
                        loadUrl(startUrl)
                    }
                },
            )
        }
    }
}
