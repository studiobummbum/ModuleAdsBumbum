package com.example.adsmodule.sdk

/**
 * Immutable format → adapter map. Each [AdFormat] may be owned by at most one adapter.
 */
public class AdSdkAdapterRegistry private constructor(
    private val adaptersByFormat: Map<AdFormat, AdSdkAdapter>,
) {
    public val registeredFormats: Set<AdFormat>
        get() = adaptersByFormat.keys

    public fun adapterFor(format: AdFormat): AdSdkAdapter? = adaptersByFormat[format]

    public fun requireAdapter(format: AdFormat): AdSdkAdapter =
        requireNotNull(adapterFor(format)) {
            "No AdSdkAdapter registered for format $format"
        }

    public companion object {
        public fun create(adapters: Collection<AdSdkAdapter>): AdSdkAdapterRegistry {
            val byFormat = LinkedHashMap<AdFormat, AdSdkAdapter>()
            adapters.forEach { adapter ->
                adapter.supportedFormats.forEach { format ->
                    val existing = byFormat.put(format, adapter)
                    require(existing == null) {
                        "Duplicate AdSdkAdapter registration for format $format"
                    }
                }
            }
            return AdSdkAdapterRegistry(adaptersByFormat = byFormat.toMap())
        }
    }
}
