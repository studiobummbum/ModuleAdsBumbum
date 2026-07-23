package com.example.adsmodule.core.config

import com.example.adsmodule.core.OriginalAdsConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

public sealed interface OriginalConfigParseResult {
    public val issues: List<ConfigValidationIssue>

    public data class Success(
        val value: OriginalConfigValue,
        val canonicalJson: String,
        override val issues: List<ConfigValidationIssue>,
    ) : OriginalConfigParseResult

    public data class Failure(
        override val issues: List<ConfigValidationIssue>,
    ) : OriginalConfigParseResult
}

public class OriginalAdsConfigParser(
    private val validator: OriginalAdsConfigValidator = OriginalAdsConfigValidator(),
) {
    private val json: Json = Json {
        isLenient = false
        ignoreUnknownKeys = true
        coerceInputValues = false
    }

    public fun parse(
        descriptor: ConfigDescriptor,
        rawJson: String,
    ): OriginalConfigParseResult {
        val root = try {
            json.decodeFromString<JsonElement>(rawJson)
        } catch (exception: SerializationException) {
            return OriginalConfigParseResult.Failure(
                issues = listOf(
                    ConfigValidationIssue(
                        severity = ConfigIssueSeverity.ERROR,
                        code = ConfigIssueCode.INVALID_JSON,
                        path = "$",
                        message = exception.message ?: "JSON cannot be parsed",
                    ),
                ),
            )
        } catch (exception: IllegalArgumentException) {
            return OriginalConfigParseResult.Failure(
                issues = listOf(
                    ConfigValidationIssue(
                        severity = ConfigIssueSeverity.ERROR,
                        code = ConfigIssueCode.INVALID_JSON,
                        path = "$",
                        message = exception.message ?: "JSON cannot be parsed",
                    ),
                ),
            )
        }

        val validationIssues = validator.validate(descriptor, root)
        if (validationIssues.hasErrors()) {
            return OriginalConfigParseResult.Failure(validationIssues)
        }

        return try {
            OriginalConfigParseResult.Success(
                value = decodeValue(descriptor.kind, root),
                canonicalJson = canonicalizeJson(root),
                issues = validationIssues,
            )
        } catch (exception: SerializationException) {
            decodeFailure(validationIssues, exception)
        } catch (exception: IllegalArgumentException) {
            decodeFailure(validationIssues, exception)
        }
    }

    private fun decodeValue(
        kind: ConfigValueKind,
        root: JsonElement,
    ): OriginalConfigValue = when (kind) {
        ConfigValueKind.ADS -> {
            val decoded = json.decodeFromJsonElement<OriginalAdsConfig>(root)
            AdsConfigValue(
                config = decoded.copy(
                    listAds = decoded.listAds.mapIndexed { index, item ->
                        item.copy(sourceListIndex = index)
                    },
                ),
            )
        }
        ConfigValueKind.FULL_SCREEN_TIMING ->
            json.decodeFromJsonElement<FullScreenTimingConfig>(root)
        ConfigValueKind.SPLASH_SCREEN ->
            json.decodeFromJsonElement<SplashScreenConfig>(root)
        ConfigValueKind.ONBOARD_ADS -> OnboardAdsConfig(
            entries = json.decodeFromJsonElement<List<OnboardAdsEntry>>(root),
        )
        ConfigValueKind.ONBOARD_SCREEN -> OnboardScreenConfig(
            entries = json.decodeFromJsonElement<List<OnboardScreenEntry>>(root),
        )
        ConfigValueKind.SPLASH_SKIP ->
            json.decodeFromJsonElement<SplashSkipConfig>(root)
        ConfigValueKind.TURNBACK ->
            json.decodeFromJsonElement<TurnbackConfig>(root)
        ConfigValueKind.BOOLEAN -> BooleanConfigValue(
            value = json.decodeFromJsonElement<Boolean>(root),
        )
    }

    private fun decodeFailure(
        validationIssues: List<ConfigValidationIssue>,
        exception: Exception,
    ): OriginalConfigParseResult.Failure = OriginalConfigParseResult.Failure(
        issues = validationIssues + ConfigValidationIssue(
            severity = ConfigIssueSeverity.ERROR,
            code = ConfigIssueCode.DECODE_FAILED,
            path = "$",
            message = exception.message ?: "Validated config could not be decoded",
        ),
    )
}

internal fun canonicalizeJson(element: JsonElement): String = when (element) {
    is JsonObject -> element.entries
        .sortedBy(Map.Entry<String, JsonElement>::key)
        .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
            "${JsonPrimitive(key)}:${canonicalizeJson(value)}"
        }
    is JsonArray -> element.joinToString(prefix = "[", postfix = "]", separator = ",") {
        canonicalizeJson(it)
    }
    else -> element.toString()
}
