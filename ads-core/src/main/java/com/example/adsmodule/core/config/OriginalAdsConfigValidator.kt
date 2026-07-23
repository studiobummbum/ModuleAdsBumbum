package com.example.adsmodule.core.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

public class OriginalAdsConfigValidator {
    public fun validate(
        descriptor: ConfigDescriptor,
        root: JsonElement,
    ): List<ConfigValidationIssue> = buildList {
        validateForbiddenFields(root, "$", this)
        when (descriptor.kind) {
            ConfigValueKind.ADS -> validateAdsConfig(root, this)
            ConfigValueKind.FULL_SCREEN_TIMING -> validateFullScreenTiming(root, this)
            ConfigValueKind.SPLASH_SCREEN -> validateSplashScreen(root, this)
            ConfigValueKind.ONBOARD_ADS -> validateOnboardArray(
                root = root,
                fieldPrefix = "ads_onboard",
                issues = this,
            )
            ConfigValueKind.ONBOARD_SCREEN -> validateOnboardArray(
                root = root,
                fieldPrefix = "screen_onboard",
                issues = this,
            )
            ConfigValueKind.SPLASH_SKIP -> validateSplashSkip(root, this)
            ConfigValueKind.TURNBACK -> validateTurnback(root, this)
            ConfigValueKind.BOOLEAN -> validateBooleanRoot(root, this)
        }
    }

    private fun validateAdsConfig(
        root: JsonElement,
        issues: MutableList<ConfigValidationIssue>,
    ) {
        val objectRoot = requireObject(root, "$", issues) ?: return
        requireBoolean(objectRoot, "enable", "$", issues)
        optionalBoolean(objectRoot, "isOrganic", "$", issues)
        optionalString(objectRoot, "type_layout", "$", issues)
        optionalString(objectRoot, "collapsible", "$", issues)
        optionalString(objectRoot, "refresh_time", "$", issues)
        optionalInteger(objectRoot, "timeout_total", "$", nonNegative = false, issues)
        optionalInteger(objectRoot, "interval", "$", nonNegative = false, issues)

        if ("weight" in objectRoot) {
            issues.error(
                code = ConfigIssueCode.FORBIDDEN_FIELD,
                path = "$.weight",
                message = "weight must be declared on each list_ads item",
            )
        }

        val listElement = objectRoot["list_ads"]
        if (listElement == null) {
            issues.error(
                code = ConfigIssueCode.REQUIRED_FIELD,
                path = "$.list_ads",
                message = "Ad config must contain list_ads",
            )
            return
        }
        val items = listElement as? JsonArray
        if (items == null) {
            issues.error(
                code = ConfigIssueCode.INVALID_FIELD_TYPE,
                path = "$.list_ads",
                message = "list_ads must be an Array",
            )
            return
        }

        val validWeights = mutableListOf<Int>()
        items.forEachIndexed { index, element ->
            val itemPath = "$.list_ads[$index]"
            val item = element as? JsonObject
            if (item == null) {
                issues.error(
                    code = ConfigIssueCode.INVALID_FIELD_TYPE,
                    path = itemPath,
                    message = "list_ads item must be an Object",
                    sourceListIndex = index,
                )
                return@forEachIndexed
            }

            requireBoolean(item, "enable_ad", itemPath, issues, index)
            val weight = requireInteger(
                objectValue = item,
                field = "weight",
                parentPath = itemPath,
                nonNegative = true,
                intRange = true,
                issues = issues,
                sourceListIndex = index,
            )
            if (weight != null) {
                validWeights += weight.toInt()
            }

            val adunit = requireString(item, "adunit", itemPath, issues, index)
            if (adunit != null && isPlaceholderAdunit(adunit)) {
                issues.warning(
                    code = ConfigIssueCode.PLACEHOLDER_ADUNIT,
                    path = "$itemPath.adunit",
                    message = "adunit is blank, incomplete, or a placeholder",
                    sourceListIndex = index,
                )
            }

            item["type"]?.let { typeElement ->
                val type = typeElement.stringValue()
                if (type == null) {
                    issues.error(
                        code = ConfigIssueCode.INVALID_FIELD_TYPE,
                        path = "$itemPath.type",
                        message = "type must be a String when present",
                        sourceListIndex = index,
                    )
                } else if (type !in ALLOWED_AD_TYPES) {
                    issues.error(
                        code = ConfigIssueCode.INVALID_AD_TYPE,
                        path = "$itemPath.type",
                        message = "type must be one of inter, appopen, native",
                        sourceListIndex = index,
                    )
                }
            }
            optionalInteger(
                objectValue = item,
                field = "timeout",
                parentPath = itemPath,
                nonNegative = true,
                issues = issues,
                sourceListIndex = index,
            )
        }

        if (validWeights.size != validWeights.toSet().size) {
            issues.warning(
                code = ConfigIssueCode.DUPLICATE_WEIGHT,
                path = "$.list_ads",
                message = "Duplicate weights use original list index as tie-break",
            )
        }
    }

    private fun validateFullScreenTiming(
        root: JsonElement,
        issues: MutableList<ConfigValidationIssue>,
    ) {
        val objectRoot = requireObject(root, "$", issues) ?: return
        requireInteger(
            objectValue = objectRoot,
            field = "time_delay_X_button",
            parentPath = "$",
            nonNegative = true,
            intRange = false,
            issues = issues,
        )
        requireInteger(
            objectValue = objectRoot,
            field = "auto_skip",
            parentPath = "$",
            nonNegative = true,
            intRange = false,
            issues = issues,
        )
    }

    private fun validateSplashScreen(
        root: JsonElement,
        issues: MutableList<ConfigValidationIssue>,
    ) {
        val objectRoot = requireObject(root, "$", issues) ?: return
        requireBoolean(objectRoot, "show_LFO", "$", issues)
        requireString(objectRoot, "show_position", "$", issues)
        requireBoolean(objectRoot, "skipped", "$", issues)
        requireInteger(
            objectValue = objectRoot,
            field = "timeout_screen",
            parentPath = "$",
            nonNegative = false,
            intRange = false,
            issues = issues,
        )
    }

    private fun validateOnboardArray(
        root: JsonElement,
        fieldPrefix: String,
        issues: MutableList<ConfigValidationIssue>,
    ) {
        val array = root as? JsonArray
        if (array == null) {
            issues.error(
                code = ConfigIssueCode.INVALID_ROOT_TYPE,
                path = "$",
                message = "Onboarding config must be an Array",
            )
            return
        }
        if (array.size != ONBOARDING_PAGE_COUNT) {
            issues.error(
                code = ConfigIssueCode.INVALID_ARRAY_SIZE,
                path = "$",
                message = "Onboarding config must contain exactly $ONBOARDING_PAGE_COUNT entries",
            )
        }
        array.forEachIndexed { index, element ->
            val itemPath = "$[$index]"
            val item = element as? JsonObject
            if (item == null) {
                issues.error(
                    code = ConfigIssueCode.INVALID_FIELD_TYPE,
                    path = itemPath,
                    message = "Onboarding entry must be an Object",
                )
                return@forEachIndexed
            }
            requireBoolean(item, "$fieldPrefix${index + 1}", itemPath, issues)
            requireBoolean(item, "isOrganic", itemPath, issues)
        }
    }

    private fun validateSplashSkip(
        root: JsonElement,
        issues: MutableList<ConfigValidationIssue>,
    ) {
        val objectRoot = requireObject(root, "$", issues) ?: return
        requireBoolean(objectRoot, "enable", "$", issues)
        requireBoolean(objectRoot, "isOrganic", "$", issues)
        requireInteger(
            objectValue = objectRoot,
            field = "time_skip",
            parentPath = "$",
            nonNegative = true,
            intRange = false,
            issues = issues,
        )
    }

    private fun validateTurnback(
        root: JsonElement,
        issues: MutableList<ConfigValidationIssue>,
    ) {
        val objectRoot = requireObject(root, "$", issues) ?: return
        requireBoolean(objectRoot, "enable", "$", issues)
        requireBoolean(objectRoot, "isOrganic", "$", issues)
        requireInteger(objectRoot, "time_turnback", "$", false, false, issues)
        requireInteger(objectRoot, "time_delay", "$", false, false, issues)
    }

    private fun validateBooleanRoot(
        root: JsonElement,
        issues: MutableList<ConfigValidationIssue>,
    ) {
        if (root.booleanValue() == null) {
            issues.error(
                code = ConfigIssueCode.INVALID_ROOT_TYPE,
                path = "$",
                message = "Boolean config must be a Boolean primitive",
            )
        }
    }

    private fun validateForbiddenFields(
        element: JsonElement,
        path: String,
        issues: MutableList<ConfigValidationIssue>,
    ) {
        when (element) {
            is JsonObject -> element.forEach { (field, value) ->
                val childPath = "$path.$field"
                if (field in FORBIDDEN_FIELDS) {
                    issues.error(
                        code = ConfigIssueCode.FORBIDDEN_FIELD,
                        path = childPath,
                        message = "$field is not part of the original Remote Config schema",
                    )
                }
                validateForbiddenFields(value, childPath, issues)
            }
            is JsonArray -> element.forEachIndexed { index, value ->
                validateForbiddenFields(value, "$path[$index]", issues)
            }
            else -> Unit
        }
    }

    private fun requireObject(
        element: JsonElement,
        path: String,
        issues: MutableList<ConfigValidationIssue>,
    ): JsonObject? {
        val value = element as? JsonObject
        if (value == null) {
            issues.error(
                code = ConfigIssueCode.INVALID_ROOT_TYPE,
                path = path,
                message = "Config root must be an Object",
            )
        }
        return value
    }

    private fun requireBoolean(
        objectValue: JsonObject,
        field: String,
        parentPath: String,
        issues: MutableList<ConfigValidationIssue>,
        sourceListIndex: Int? = null,
    ): Boolean? {
        val element = objectValue[field]
        if (element == null) {
            issues.error(
                code = ConfigIssueCode.REQUIRED_FIELD,
                path = "$parentPath.$field",
                message = "$field is required",
                sourceListIndex = sourceListIndex,
            )
            return null
        }
        val value = element.booleanValue()
        if (value == null) {
            issues.error(
                code = ConfigIssueCode.INVALID_FIELD_TYPE,
                path = "$parentPath.$field",
                message = "$field must be a Boolean",
                sourceListIndex = sourceListIndex,
            )
        }
        return value
    }

    private fun optionalBoolean(
        objectValue: JsonObject,
        field: String,
        parentPath: String,
        issues: MutableList<ConfigValidationIssue>,
    ) {
        val element = objectValue[field] ?: return
        if (element.booleanValue() == null) {
            issues.error(
                code = ConfigIssueCode.INVALID_FIELD_TYPE,
                path = "$parentPath.$field",
                message = "$field must be a Boolean when present",
            )
        }
    }

    private fun requireString(
        objectValue: JsonObject,
        field: String,
        parentPath: String,
        issues: MutableList<ConfigValidationIssue>,
        sourceListIndex: Int? = null,
    ): String? {
        val element = objectValue[field]
        if (element == null) {
            issues.error(
                code = ConfigIssueCode.REQUIRED_FIELD,
                path = "$parentPath.$field",
                message = "$field is required",
                sourceListIndex = sourceListIndex,
            )
            return null
        }
        val value = element.stringValue()
        if (value == null) {
            issues.error(
                code = ConfigIssueCode.INVALID_FIELD_TYPE,
                path = "$parentPath.$field",
                message = "$field must be a String",
                sourceListIndex = sourceListIndex,
            )
        }
        return value
    }

    private fun optionalString(
        objectValue: JsonObject,
        field: String,
        parentPath: String,
        issues: MutableList<ConfigValidationIssue>,
    ) {
        val element = objectValue[field] ?: return
        if (element.stringValue() == null) {
            issues.error(
                code = ConfigIssueCode.INVALID_FIELD_TYPE,
                path = "$parentPath.$field",
                message = "$field must be a String when present",
            )
        }
    }

    private fun optionalInteger(
        objectValue: JsonObject,
        field: String,
        parentPath: String,
        nonNegative: Boolean,
        issues: MutableList<ConfigValidationIssue>,
        sourceListIndex: Int? = null,
    ) {
        if (field !in objectValue) return
        requireInteger(
            objectValue = objectValue,
            field = field,
            parentPath = parentPath,
            nonNegative = nonNegative,
            intRange = false,
            issues = issues,
            sourceListIndex = sourceListIndex,
        )
    }

    private fun requireInteger(
        objectValue: JsonObject,
        field: String,
        parentPath: String,
        nonNegative: Boolean,
        intRange: Boolean,
        issues: MutableList<ConfigValidationIssue>,
        sourceListIndex: Int? = null,
    ): Long? {
        val path = "$parentPath.$field"
        val element = objectValue[field]
        if (element == null) {
            issues.error(
                code = ConfigIssueCode.REQUIRED_FIELD,
                path = path,
                message = "$field is required",
                sourceListIndex = sourceListIndex,
            )
            return null
        }
        val primitive = element as? JsonPrimitive
        val content = primitive?.takeUnless { it.isString }?.content
        if (content == null || !INTEGER_PATTERN.matches(content)) {
            issues.error(
                code = ConfigIssueCode.INVALID_FIELD_TYPE,
                path = path,
                message = "$field must be an integer Number",
                sourceListIndex = sourceListIndex,
            )
            return null
        }
        val value = content.toLongOrNull()
        if (
            value == null ||
            (intRange && (value < Int.MIN_VALUE.toLong() || value > Int.MAX_VALUE.toLong()))
        ) {
            issues.error(
                code = ConfigIssueCode.INVALID_RANGE,
                path = path,
                message = "$field is outside the supported ${if (intRange) "Int" else "Long"} range",
                sourceListIndex = sourceListIndex,
            )
            return null
        }
        if (nonNegative && value < 0) {
            issues.error(
                code = ConfigIssueCode.INVALID_RANGE,
                path = path,
                message = "$field must be >= 0",
                sourceListIndex = sourceListIndex,
            )
            return null
        }
        return value
    }

    private fun JsonElement.stringValue(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement.booleanValue(): Boolean? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return when (primitive.content) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun isPlaceholderAdunit(value: String): Boolean =
        value.isBlank() ||
            "{{" in value ||
            value.trim() == "ca-app-pub-"

    private fun MutableList<ConfigValidationIssue>.error(
        code: ConfigIssueCode,
        path: String,
        message: String,
        sourceListIndex: Int? = null,
    ) {
        add(
            ConfigValidationIssue(
                severity = ConfigIssueSeverity.ERROR,
                code = code,
                path = path,
                message = message,
                sourceListIndex = sourceListIndex,
            ),
        )
    }

    private fun MutableList<ConfigValidationIssue>.warning(
        code: ConfigIssueCode,
        path: String,
        message: String,
        sourceListIndex: Int? = null,
    ) {
        add(
            ConfigValidationIssue(
                severity = ConfigIssueSeverity.WARNING,
                code = code,
                path = path,
                message = message,
                sourceListIndex = sourceListIndex,
            ),
        )
    }

    private companion object {
        private val ALLOWED_AD_TYPES: Set<String> = setOf("inter", "appopen", "native")
        private val FORBIDDEN_FIELDS: Set<String> =
            setOf("schema_version", "candidates", "candidate_sets", "ad_unit_id")
        private val INTEGER_PATTERN: Regex = Regex("-?(0|[1-9][0-9]*)")
        private const val ONBOARDING_PAGE_COUNT: Int = 4
    }
}
