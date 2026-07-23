package com.example.adsdemo.language

import com.example.adsmodule.core.language.DemoLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageSelectionPersistenceTest {
    @Test
    fun demoLanguages_containsExpectedTags() {
        val tags = DemoLanguages.all.map { it.tag }.toSet()
        assertTrue(tags.contains("en"))
        assertTrue(tags.contains("vi"))
        assertTrue(tags.contains("fr"))
    }

    @Test
    fun find_isCaseInsensitive() {
        val found = DemoLanguages.find("VI")
        assertNotNull(found)
        assertEquals("vi", found!!.tag)
        assertEquals("Tiếng Việt", found.displayName)
    }

    @Test
    fun selectedLanguage_roundTripTag() {
        val selected = DemoLanguages.find("es")!!
        val restored = DemoLanguages.find(selected.tag)
        assertEquals(selected, restored)
    }
}
