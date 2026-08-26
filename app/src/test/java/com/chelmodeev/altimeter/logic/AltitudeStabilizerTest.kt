package com.chelmodeev.altimeter.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AltitudeStabilizerTest {

    @Test
    fun holdsAltitudeDuringStationaryNoise() {
        val stabilizer = AltitudeStabilizer()
        var result = stabilizer.update(100.0, 0L)!!
        for (second in 1..60) {
            val noise = if (second % 2 == 0) 1.2 else -1.2
            result = stabilizer.update(100.0 + noise, second * 1_000L)!!
        }
        assertEquals(100.0, result, 0.001)
    }

    @Test
    fun rejectsSingleLargeSpike() {
        val stabilizer = AltitudeStabilizer()
        var result = stabilizer.update(100.0, 0L)!!
        for (second in 1..20) {
            val raw = if (second == 10) 112.0 else 100.0
            result = stabilizer.update(raw, second * 1_000L)!!
        }
        assertEquals(100.0, result, 0.001)
    }

    @Test
    fun followsSustainedWalkingClimb() {
        val stabilizer = AltitudeStabilizer()
        stabilizer.update(100.0, 0L)
        var result = 100.0
        for (second in 1..60) {
            result = stabilizer.update(100.0 + second * 0.18, second * 1_000L)!!
        }
        assertTrue("slow real climb must be preserved", result > 107.0)
        assertTrue("filter should not overshoot", result <= 111.0)
    }

    @Test
    fun reactsQuicklyToAircraftOrElevatorClimb() {
        val stabilizer = AltitudeStabilizer()
        stabilizer.update(100.0, 0L)
        var result = 100.0
        for (second in 1..10) {
            result = stabilizer.update(100.0 + second * 4.0, second * 1_000L)!!
        }
        assertTrue("fast vertical movement must not be averaged away", result > 132.0)
        assertTrue("filter should not overshoot", result <= 140.0)
    }
}
