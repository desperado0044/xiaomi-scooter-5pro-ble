package com.scooterre.client.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveLangTest {
    @Test
    fun savedChoiceWinsOverTheSystemLanguage() {
        assertEquals(Lang.EN, resolveLang("EN", systemLanguage = "de"))
        assertEquals(Lang.DE, resolveLang("DE", systemLanguage = "en"))
    }

    @Test
    fun withoutSavedChoiceGermanDevicesGetGermanAndEverythingElseEnglish() {
        assertEquals(Lang.DE, resolveLang(null, systemLanguage = "de"))
        assertEquals(Lang.EN, resolveLang(null, systemLanguage = "en"))
        assertEquals(Lang.EN, resolveLang(null, systemLanguage = "fr"))
        assertEquals(Lang.EN, resolveLang(null, systemLanguage = ""))
    }

    @Test
    fun unknownSavedValueFallsBackToTheSystemLanguage() {
        assertEquals(Lang.DE, resolveLang("garbage", systemLanguage = "de"))
        assertEquals(Lang.EN, resolveLang("garbage", systemLanguage = "es"))
    }
}
