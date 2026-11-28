package com.devrayan.pengu.bugreport

import java.security.MessageDigest

object BugReportLimits {
    const val TITLE_MIN = 5
    const val TITLE_MAX = 140
    const val DESCRIPTION_MIN = 15
    const val DESCRIPTION_MAX = 4000
    const val STEPS_MAX = 2500
    const val EXPECTED_MAX = 1500
    const val ACTUAL_MAX = 1500
    const val MAX_ATTACHMENTS = 3
    const val MAX_ATTACHMENT_BYTES = 3 * 1024 * 1024
}

data class BugReportValidationResult(
    val isValid: Boolean,
    val titleError: String? = null,
    val descriptionError: String? = null,
    val stepsError: String? = null,
    val expectedError: String? = null,
    val actualError: String? = null,
    val attachmentsError: String? = null,
)

fun validateBugReportForm(
    title: String,
    description: String,
    stepsToReproduce: String = "",
    expectedBehavior: String = "",
    actualBehavior: String = "",
    attachmentCount: Int = 0,
): BugReportValidationResult {
    val cleanTitle = title.trim()
    val cleanDesc = description.trim()

    val titleError =
        when {
            cleanTitle.length < BugReportLimits.TITLE_MIN ->
                "Title must be at least ${BugReportLimits.TITLE_MIN} characters"
            cleanTitle.length > BugReportLimits.TITLE_MAX ->
                "Title cannot exceed ${BugReportLimits.TITLE_MAX} characters"
            else -> null
        }

    val descError =
        when {
            cleanDesc.length < BugReportLimits.DESCRIPTION_MIN ->
                "Description must be at least ${BugReportLimits.DESCRIPTION_MIN} characters"
            cleanDesc.length > BugReportLimits.DESCRIPTION_MAX ->
                "Description cannot exceed ${BugReportLimits.DESCRIPTION_MAX} characters"
            else -> null
        }

    val stepsError =
        if (stepsToReproduce.length > BugReportLimits.STEPS_MAX)
            "Steps cannot exceed ${BugReportLimits.STEPS_MAX} characters"
        else null

    val expectedError =
        if (expectedBehavior.length > BugReportLimits.EXPECTED_MAX)
            "Expected behavior cannot exceed ${BugReportLimits.EXPECTED_MAX} characters"
        else null

    val actualError =
        if (actualBehavior.length > BugReportLimits.ACTUAL_MAX)
            "Actual behavior cannot exceed ${BugReportLimits.ACTUAL_MAX} characters"
        else null

    val attachmentsError =
        if (attachmentCount > BugReportLimits.MAX_ATTACHMENTS)
            "Maximum ${BugReportLimits.MAX_ATTACHMENTS} screenshot attachments allowed"
        else null

    val valid =
        titleError == null &&
            descError == null &&
            stepsError == null &&
            expectedError == null &&
            actualError == null &&
            attachmentsError == null

    return BugReportValidationResult(
        isValid = valid,
        titleError = titleError,
        descriptionError = descError,
        stepsError = stepsError,
        expectedError = expectedError,
        actualError = actualError,
        attachmentsError = attachmentsError,
    )
}

fun computeBugReportSignature(
    category: String,
    title: String,
    description: String,
    actualBehavior: String,
): String {
    val raw =
        "${category.trim().lowercase()}|${title.trim().lowercase()}|${description.trim().lowercase()}|${actualBehavior.trim().lowercase()}"
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

/**
 * Scrubs sensitive credentials, session cookies, tokens, URL query secrets,
 * email addresses, and OS user paths prior to local log display or remote submission.
 */
object DiagnosticRedactor {
    private val headerOrParamSecret =
        Regex(
            """(?i)\b(authorization|cookie|set-cookie|x-tracking-token|password|passwd|pwd|secret|token|apikey|api_key|sessionid|jsessionid)\s*[:=]\s*[^\s,;'"&]+"""
        )
    private val bearerRegex = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
    private val querySecretRegex =
        Regex("""(?i)([?&](?:token|key|password|pass|auth|sig|cookie|session|access_token)=)[^&\s]+""")
    private val emailRegex = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
    private val cookiesFileRegex = Regex("""(?i)cookies?\.txt""")
    private val userIdPathRegex = Regex("""/data/user/\d+/""")

    fun redact(input: String): String {
        if (input.isBlank()) return input
        var out = input
        out = bearerRegex.replace(out, "Bearer [REDACTED]")
        out = headerOrParamSecret.replace(out) { mr -> "${mr.groupValues[1]}=[REDACTED]" }
        out = querySecretRegex.replace(out) { mr -> "${mr.groupValues[1]}[REDACTED]" }
        out = emailRegex.replace(out, "[REDACTED_EMAIL]")
        out = cookiesFileRegex.replace(out, "[REDACTED_COOKIE_FILE]")
        out = userIdPathRegex.replace(out, "/data/user/[USER]/")
        return out
    }
}
