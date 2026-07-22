package com.example.adsmodule.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AdsCoreStatusTest {
    @Test
    fun description_identifiesCoreAndSdkContractModules() {
        assertEquals(
            "ads-core ready with ads-sdk-core",
            AdsCoreStatus.description,
        )
    }
}
