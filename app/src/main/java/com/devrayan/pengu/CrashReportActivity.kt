package com.devrayan.pengu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devrayan.pengu.bugreport.BugCategory
import com.devrayan.pengu.bugreport.BugReportApiException
import com.devrayan.pengu.bugreport.BugReportRepository
import com.devrayan.pengu.bugreport.DiagnosticRedactor
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.ui.common.SettingsProvider
import com.devrayan.pengu.ui.theme.SealTheme
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent

class CrashReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val rawErrorMessage: String = intent.getStringExtra("error_report").orEmpty()
        val redactedCrashLog = DiagnosticRedactor.redact(rawErrorMessage)
        val repository =
            runCatching { KoinJavaComponent.get<BugReportRepository>(BugReportRepository::class.java) }
                .getOrElse { BugReportRepository(applicationContext) }

        setContent {
            SettingsProvider(WindowWidthSizeClass.Compact) {
                SealTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                ) {
                    val clipboardManager = LocalClipboardManager.current
                    val scope = rememberCoroutineScope()
                    var isSending by remember { mutableStateOf(false) }
                    var statusBanner by rememberSaveable { mutableStateOf<String?>(null) }
                    var isErrorBanner by rememberSaveable { mutableStateOf(false) }
                    var reportSentId by rememberSaveable { mutableStateOf<String?>(null) }

                    CrashReportPage(
                        errorMessage = redactedCrashLog,
                        isSending = isSending,
                        statusBanner = statusBanner,
                        isErrorBanner = isErrorBanner,
                        reportSentId = reportSentId,
                        onSendReport = { consented ->
                            if (!consented || isSending || reportSentId != null) return@CrashReportPage
                            isSending = true
                            statusBanner = null
                            isErrorBanner = false
                            scope.launch {
                                val firstLine =
                                    redactedCrashLog
                                        .lineSequence()
                                        .firstOrNull {
                                            it.contains("Exception") || it.contains("Error")
                                        }
                                        ?.take(95)
                                        ?: "Unexpected Crash"
                                val result =
                                    repository.submitBugReport(
                                        title = "Crash: $firstLine",
                                        category = BugCategory.APPLICATION_CRASH,
                                        description =
                                            "User-consented crash report submitted after uncaught application exception.",
                                        stepsToReproduce = "Occurred during normal app usage.",
                                        expectedBehavior = "Application continues running smoothly.",
                                        actualBehavior = firstLine,
                                        includeDiagnostics = true,
                                        rawCrashTrace = redactedCrashLog,
                                    )
                                isSending = false
                                result.fold(
                                    onSuccess = { saved ->
                                        reportSentId = saved.remoteId
                                        isErrorBanner = false
                                        statusBanner =
                                            "Crash report sent (${saved.remoteId}). You can inspect live status in Settings -> Troubleshooting -> My Bug Reports."
                                    },
                                    onFailure = { err ->
                                        isErrorBanner = true
                                        val msg =
                                            (err as? BugReportApiException)?.userFriendlyMessage
                                                ?: err.message
                                                ?: "Network unavailable"
                                        statusBanner =
                                            "Saved locally for retry: $msg"
                                    },
                                )
                            }
                        },
                        onCopyCrashLog = {
                            clipboardManager.setText(AnnotatedString(redactedCrashLog))
                            isErrorBanner = false
                            statusBanner = getString(R.string.error_copied)
                        },
                        onExit = { finishAffinity() },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) finishAffinity()
    }
}

@Composable
@Preview
fun CrashReportPage(
    errorMessage: String = "ERROR_EXAMPLE",
    isSending: Boolean = false,
    statusBanner: String? = null,
    isErrorBanner: Boolean = false,
    reportSentId: String? = null,
    onSendReport: (consented: Boolean) -> Unit = {},
    onCopyCrashLog: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    // Explicit user consent required — never defaults to uploading silently
    var hasUserConsent by rememberSaveable { mutableStateOf(false) }

    val violet = Color(0xFF6D28D9)
    val emerald = Color(0xFF10B981)
    val coral = Color(0xFFEF4444)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
            ) {
                HorizontalDivider()
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onSendReport(hasUserConsent) },
                        enabled = hasUserConsent && !isSending && reportSentId == null,
                        shape = RoundedCornerShape(14.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = violet,
                                contentColor = Color.White,
                            ),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Sending Redacted Report...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text =
                                    if (reportSentId != null) "Sent ($reportSentId)"
                                    else "Send Bug Report",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onCopyCrashLog,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Crash Log", fontWeight = FontWeight.SemiBold)
                        }

                        TextButton(
                            onClick = onExit,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(46.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Exit", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Rounded.BugReport,
                    contentDescription = null,
                    tint = coral,
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    text = stringResource(R.string.unknown_error_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            if (statusBanner != null) {
                val tone = if (isErrorBanner) coral else emerald
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = tone.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, tone.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            if (isErrorBanner) Icons.Rounded.ErrorOutline
                            else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = tone,
                        )
                        Text(statusBanner, fontSize = 12.5.sp, color = tone)
                    }
                }
            }

            // Consent Gate Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = violet.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, violet.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().clickable { hasUserConsent = !hasUserConsent },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = hasUserConsent,
                        onCheckedChange = { hasUserConsent = it },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Security,
                                contentDescription = null,
                                tint = violet,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "Explicit Privacy Consent",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                            )
                        }
                        Text(
                            text =
                                "Check to share redacted diagnostics (Pengu version, Android API, device model, CPU ABI, yt-dlp version, and scrubbed stack trace). Passwords, cookies, and tokens are never uploaded.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = "Redacted Crash Trace Preview",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                )
            }
        }
    }
}
