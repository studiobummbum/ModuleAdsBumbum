package com.example.adsmodule.core.analytics

/**
 * Strips PII and raw production secrets before events are stored or exported.
 */
public object AdsAnalyticsSanitizer {
    private val emailRegex = Regex(
        pattern = """[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""",
    )
    private val phoneRegex = Regex(
        pattern = """(?<!\d)(?:\+?\d[\d\s().-]{7,}\d)""",
    )
    private val gaidRegex = Regex(
        pattern = """(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""",
    )
    private val secretKeyRegex = Regex(
        pattern = """(?i)(api[_-]?key|secret|password|token|authorization)\s*[:=]\s*\S+""",
    )
    private val caPubRegex = Regex(
        pattern = """(?i)ca-app-pub-\d{16}/\d{10}""",
    )

    public const val REDACTED: String = "[REDACTED]"

    public fun sanitize(event: AdsAnalyticsEvent): AdsAnalyticsEvent =
        event.copy(
            error = event.error?.let(::sanitizeText),
            result = event.result?.let(::sanitizeText),
            adunitAlias = event.adunitAlias?.let(::sanitizeAdUnitAlias),
            details = event.details.mapValues { (_, value) -> sanitizeText(value) },
        )

    public fun sanitizeText(value: String): String {
        var result = value
        result = emailRegex.replace(result, REDACTED)
        result = phoneRegex.replace(result, REDACTED)
        result = gaidRegex.replace(result, REDACTED)
        result = secretKeyRegex.replace(result, REDACTED)
        result = caPubRegex.replace(result, REDACTED)
        return result
    }

    /**
     * Keeps short aliases; redacts values that look like production ad unit IDs.
     */
    public fun sanitizeAdUnitAlias(value: String): String {
        if (caPubRegex.containsMatchIn(value)) return REDACTED
        if (value.length > 64) return REDACTED
        return value
    }
}
