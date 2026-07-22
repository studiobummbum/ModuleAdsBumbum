package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdsSdkModule

/**
 * Build-time marker for the fake SDK adapter module.
 */
public object FakeAdsSdkModule {
    public val status: String = "${AdsSdkModule.NAME} fake adapter ready"
}
