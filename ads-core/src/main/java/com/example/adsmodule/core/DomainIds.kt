package com.example.adsmodule.core

@JvmInline
public value class ConfigKey(public val value: String) {
    init {
        require(value.isNotBlank()) { "ConfigKey must not be blank" }
    }
}

@JvmInline
public value class ScreenInstanceId(public val value: String) {
    init {
        require(value.isNotBlank()) { "ScreenInstanceId must not be blank" }
    }
}

@JvmInline
public value class LoadCycleId(public val value: String) {
    init {
        require(value.isNotBlank()) { "LoadCycleId must not be blank" }
    }
}

@JvmInline
public value class LoadRequestId(public val value: String) {
    init {
        require(value.isNotBlank()) { "LoadRequestId must not be blank" }
    }
}

@JvmInline
public value class ObjectId(public val value: String) {
    init {
        require(value.isNotBlank()) { "ObjectId must not be blank" }
    }
}

@JvmInline
public value class ReservationId(public val value: String) {
    init {
        require(value.isNotBlank()) { "ReservationId must not be blank" }
    }
}

@JvmInline
public value class ShowRequestId(public val value: String) {
    init {
        require(value.isNotBlank()) { "ShowRequestId must not be blank" }
    }
}

@JvmInline
public value class SessionId(public val value: String) {
    init {
        require(value.isNotBlank()) { "SessionId must not be blank" }
    }
}

@JvmInline
public value class FullSessionId(public val value: String) {
    init {
        require(value.isNotBlank()) { "FullSessionId must not be blank" }
    }
}

@JvmInline
public value class SplashSessionId(public val value: String) {
    init {
        require(value.isNotBlank()) { "SplashSessionId must not be blank" }
    }
}

@JvmInline
public value class LanguageSessionId(public val value: String) {
    init {
        require(value.isNotBlank()) { "LanguageSessionId must not be blank" }
    }
}

@JvmInline
public value class AdClickTokenId(public val value: String) {
    init {
        require(value.isNotBlank()) { "AdClickTokenId must not be blank" }
    }
}
