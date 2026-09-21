package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecProfilesTest {
    @Test
    fun testedAndSameGroupModelsAreFullyUsable() {
        for (model in listOf("xiaomi.scooter.5pro", "xiaomi.scooter.5max", "xiaomi.scooter.t2336", "xiaomi.scooter.5")) {
            assertEquals(model, ModelSupport.FULL, SpecProfiles.supportOf(model))
            assertFalse(SpecProfiles.forModel(model).readOnly)
        }
    }

    @Test
    fun anUnknownModelIsCheckedByTheFirstReadingsInstead() {
        assertEquals(ModelSupport.FULL, SpecProfiles.supportOf(null))
        assertEquals(ModelSupport.FULL, SpecProfiles.supportOf(""))
    }

    @Test
    fun theSixMaxIsReadOnly() {
        assertEquals(ModelSupport.READ_ONLY, SpecProfiles.supportOf("xiaomi.scooter.6max"))
        assertTrue(SpecProfiles.forModel("xiaomi.scooter.6max").readOnly)
    }

    @Test
    fun modelsWithDifferentlyNumberedValuesAreNotSupported() {
        for (model in listOf("xiaomi.scooter.elite", "xiaomi.scooter.5plus", "xiaomi.scooter.6", "xiaomi.scooter.6lite", "xiaomi.scooter.6pro", "xiaomi.scooter.cross", "xiaomi.scooter.6esstl", "xiaomi.scooter.7", "ninebot.scooter.v13")) {
            assertEquals(model, ModelSupport.UNSUPPORTED, SpecProfiles.supportOf(model))
        }
    }
}
