package com.devrayan.pengu.bugreport

import com.devrayan.pengu.BuildConfig
import com.devrayan.pengu.util.UpdateUtil
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed class BugReportApiException(message: String, val userFriendlyMessage: String) :
    IOException(message) {
    class EndpointNotConfigured :
        BugReportApiException(
            "No HTTPS bug report endpoint configured",
            "Server URL is not configured yet. Report saved locally so you can retry once an HTTPS server URL is saved.",
        )

    class InsecureEndpoint :
        BugReportApiException(
            "Only HTTPS URLs are permitted",
            "Security error: Pengu requires an https:// server URL.",
        )

    class Offline(cause: Throwable) :
        BugReportApiException(
            cause.message ?: "Offline",
            "No connection to server. Your bug report is saved locally and ready to retry.",
        )

    class Timeout(cause: Throwable) :
        BugReportApiException(
            cause.message ?: "Request timed out",
            "Connection timed out. Your bug report is preserved locally for retry.",
        )

    class Duplicate(message: String) :
        BugReportApiException(
            message,
            "An identical bug report was already submitted recently.",
        )

    class RateLimited(message: String) :
        BugReportApiException(
            message,
            "Too many requests right now. Please wait a moment and retry.",
        )

    class ValidationOrServer(val httpCode: Int, message: String) :
        BugReportApiException(message, message)
}

@Serializable
private data class ApiErrorEnvelope(
    val error: String = "",
    val code: String = "",
)

interface BugReportApiClient {
    suspend fun submitBugReport(
        baseUrl: String,
        request: BugReportSubmissionRequest,
    ): BugReportSubmissionResponse

    suspend fun fetchReportStatus(
        baseUrl: String,
        reportId: String,
        trackingToken: String,
    ): BugReportPublicStatusResponse
}

class OkHttpBugReportApi(
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build(),
) : BugReportApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun requireHttpsBase(rawBaseUrl: String): String {
        val cleaned = rawBaseUrl.trim().trimEnd('/')
        if (cleaned.isEmpty()) throw BugReportApiException.EndpointNotConfigured()
        if (!UpdateUtil.isHttpsUrl(cleaned)) throw BugReportApiException.InsecureEndpoint()
        return cleaned
    }

    override suspend fun submitBugReport(
        baseUrl: String,
        request: BugReportSubmissionRequest,
    ): BugReportSubmissionResponse {
        val cleanBase = requireHttpsBase(baseUrl)
        val payload = json.encodeToString(request)
        val httpReq =
            Request.Builder()
                .url("$cleanBase/api/bug-reports")
                .header("User-Agent", "Pengu-Android/${BuildConfig.VERSION_NAME}")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        return executeAndParse(httpReq)
    }

    override suspend fun fetchReportStatus(
        baseUrl: String,
        reportId: String,
        trackingToken: String,
    ): BugReportPublicStatusResponse {
        val cleanBase = requireHttpsBase(baseUrl)
        val httpReq =
            Request.Builder()
                .url("$cleanBase/api/bug-reports/$reportId/status")
                .header("User-Agent", "Pengu-Android/${BuildConfig.VERSION_NAME}")
                .header("X-Tracking-Token", trackingToken)
                .get()
                .build()
        return executeAndParse(httpReq)
    }

    private inline fun <reified T> executeAndParse(httpReq: Request): T {
        try {
            client.newCall(httpReq).execute().use { resp ->
                val raw = resp.body.string()
                if (!resp.isSuccessful) {
                    val parsedErr = runCatching { json.decodeFromString<ApiErrorEnvelope>(raw) }.getOrNull()
                    val msg = parsedErr?.error?.ifBlank { null } ?: "Server returned HTTP ${resp.code}"
                    when (resp.code) {
                        409 -> throw BugReportApiException.Duplicate(msg)
                        429 -> throw BugReportApiException.RateLimited(msg)
                        else -> throw BugReportApiException.ValidationOrServer(resp.code, msg)
                    }
                }
                return json.decodeFromString(raw)
            }
        } catch (e: BugReportApiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw BugReportApiException.Timeout(e)
        } catch (e: UnknownHostException) {
            throw BugReportApiException.Offline(e)
        } catch (e: ConnectException) {
            throw BugReportApiException.Offline(e)
        } catch (e: IOException) {
            throw BugReportApiException.Offline(e)
        }
    }
}
