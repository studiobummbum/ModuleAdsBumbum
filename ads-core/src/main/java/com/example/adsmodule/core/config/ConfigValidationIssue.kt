package com.example.adsmodule.core.config

public enum class ConfigIssueSeverity {
    ERROR,
    WARNING,
}

public enum class ConfigIssueCode {
    INVALID_JSON,
    INVALID_ROOT_TYPE,
    FORBIDDEN_FIELD,
    REQUIRED_FIELD,
    INVALID_FIELD_TYPE,
    INVALID_RANGE,
    INVALID_AD_TYPE,
    PLACEHOLDER_ADUNIT,
    DUPLICATE_WEIGHT,
    INVALID_ARRAY_SIZE,
    DECODE_FAILED,
}

public data class ConfigValidationIssue(
    val severity: ConfigIssueSeverity,
    val code: ConfigIssueCode,
    val path: String,
    val message: String,
    val sourceListIndex: Int? = null,
)

internal fun List<ConfigValidationIssue>.hasErrors(): Boolean =
    any { it.severity == ConfigIssueSeverity.ERROR }
