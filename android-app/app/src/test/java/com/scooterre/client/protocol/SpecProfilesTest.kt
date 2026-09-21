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
    fun unknownModelsAreNotSupported() {
        for (model in listOf("xiaomi.scooter.7", "xiaomi.scooter.4lpde", "ninebot.scooter.v13")) {
            assertEquals(model, ModelSupport.UNSUPPORTED, SpecProfiles.supportOf(model))
        }
    }

    @Test
    fun modelsWithTheirOwnTableAreReadOnly() {
        for (model in listOf("xiaomi.scooter.elite", "xiaomi.scooter.5plus", "xiaomi.scooter.6", "xiaomi.scooter.6lite", "xiaomi.scooter.6pro", "xiaomi.scooter.cross", "xiaomi.scooter.6esstl")) {
            assertEquals(model, ModelSupport.READ_ONLY, SpecProfiles.supportOf(model))
            val profile = SpecProfiles.forModel(model)
            assertTrue(model, profile.readOnly)
            assertTrue(model, profile.settable.isEmpty() && profile.writeOnly.isEmpty())
            assertTrue(model, profile !== SpecProfiles.SCOOTER_5_PRO)
        }
    }

    @Test
    fun theTablesAreSafeAndConsistent() {
        val shared = setOf("BATTERY_LEVEL", "RIDING_MODE", "IS_LOCKED", "TAIL_LIGHT_IS_ON")
        val forbidden = listOf("OOB", "UNBIND", "BIND", "PASSWORD", "RESET", "ANTI_ACTIVATION", "FACTORY")
        for ((model, profile) in FamilyAProfiles.BY_MODEL) {
            val names = profile.all.map { it.name }
            assertEquals("$model: unique names", names.size, names.toSet().size)
            assertEquals("$model: unique ids", profile.all.size, profile.all.map { it.siid to it.piid }.toSet().size)
            assertTrue("$model: only services 2-5", profile.all.all { it.siid in 2..5 })
            assertTrue("$model: canonical names only the shared ones", names.filter { !it.startsWith("A_") }.all { it in shared })
            assertTrue("$model: basic values present", names.contains("BATTERY_LEVEL") && names.contains("RIDING_MODE"))
            for (word in forbidden) assertFalse("$model: no $word property", names.any { it.contains(word) && !it.contains("RESET_CAPACITY") && !it.contains("PRESSURE_REST") })
            val tabbed = listOf(profile.tabRide, profile.tabBattery, profile.tabSettings, profile.tabVehicleStatus, profile.tabIdentification).flatten()
            assertEquals("$model: every property is on exactly one tab", names.sorted(), tabbed.sorted())
        }
    }
}
