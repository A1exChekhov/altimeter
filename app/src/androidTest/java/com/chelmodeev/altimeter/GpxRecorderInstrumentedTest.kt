package com.chelmodeev.altimeter

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chelmodeev.altimeter.core.AltimeterCore
import com.chelmodeev.altimeter.model.TrackSamplingMode
import com.chelmodeev.altimeter.track.GpxRecorder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpxRecorderInstrumentedTest {

    @Test
    fun oneSecondDefaultKeepsEveryPointAndEveryTurn() {
        val recorder = GpxRecorder().apply { begin() }
        val started = 1_800_000_000_000L
        val fixes = buildList {
            repeat(11) { i -> add(fix(55.0 + i * 0.00001, 37.0, started + i * 1_000L)) }
            repeat(10) { i -> add(fix(55.00010, 37.0 + (i + 1) * 0.00002, started + (i + 11) * 1_000L)) }
            repeat(10) { i -> add(fix(55.00010 - (i + 1) * 0.00001, 37.00020, started + (i + 21) * 1_000L)) }
        }

        fixes.forEach { assertTrue(recorder.offer(it)) }

        assertEquals(fixes.size, recorder.pointCount)
        val route = recorder.mapPoints()
        assertEquals(55.00010, route[10].latitude, 0.0000001)
        assertEquals(37.00020, route[20].longitude, 0.0000001)
    }

    @Test
    fun fourSecondModeStillWritesSharpTurnImmediately() {
        val recorder = GpxRecorder().apply {
            begin()
            setSamplingMode(TrackSamplingMode.EVERY_4S)
        }
        val started = 1_800_000_100_000L
        assertTrue(recorder.offer(fix(55.0, 37.0, started)))
        assertFalse(recorder.offer(fix(55.00001, 37.0, started + 1_000L)))
        assertFalse(recorder.offer(fix(55.00002, 37.0, started + 2_000L)))
        assertFalse(recorder.offer(fix(55.00003, 37.0, started + 3_000L)))
        assertTrue(recorder.offer(fix(55.00004, 37.0, started + 4_000L)))
        // Поворот на восток через секунду принимается вне интервала 4 с.
        assertTrue(recorder.offer(fix(55.00004, 37.00002, started + 5_000L)))
        assertEquals(3, recorder.pointCount)
    }

    @Test
    fun gpsGapKeepsRouteContinuousAndRestoreKeepsAllPoints() {
        val recorder = GpxRecorder().apply { begin() }
        val started = 1_800_000_200_000L
        assertTrue(recorder.offer(fix(55.0, 37.0, started)))
        assertTrue(recorder.offer(fix(55.00001, 37.0, started + 1_000L)))
        assertTrue(recorder.offer(fix(55.001, 37.001, started + 41_000L)))
        assertFalse(recorder.mapPoints().last().startsNewSegment)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "precise-route-test.gpx")
        recorder.saveTo(file)
        val xml = file.readText()
        assertEquals(3, Regex("<trkpt ").findAll(xml).count())
        assertEquals(1, Regex("<trkseg>").findAll(xml).count())

        val restored = GpxRecorder()
        assertTrue(restored.restoreFrom(file))
        assertEquals(3, restored.pointCount)
        assertFalse(restored.mapPoints().last().startsNewSegment)
        file.delete()
    }

    @Test
    fun mountainFixWithModerateAccuracyIsNotDiscarded() {
        val recorder = GpxRecorder().apply { begin() }
        val started = 1_800_000_300_000L

        assertTrue(recorder.offer(fix(27.0, 85.0, started, accuracy = 55f)))
        assertTrue(recorder.offer(fix(27.0001, 85.0001, started + 15_000L, accuracy = 55f)))
        assertEquals(2, recorder.pointCount)
    }

    private fun fix(
        lat: Double,
        lon: Double,
        timeMs: Long,
        accuracy: Float = 3f,
    ) = AltimeterCore.CoreState(
        latitude = lat,
        longitude = lon,
        altitude = 300.0,
        gpsAccuracy = accuracy,
        hasFix = true,
        gpsFixTimeMs = timeMs,
        timestampMs = timeMs,
    )
}
