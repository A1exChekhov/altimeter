package com.chelmodeev.altimeter.logic

import com.chelmodeev.altimeter.model.TrackMapPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class MapTrackGeometryTest {

    @Test
    fun segmentBoundariesDoNotHideLiveRoute() {
        val route = listOf(
            TrackMapPoint(27.0, 85.0, startsNewSegment = true),
            TrackMapPoint(27.0001, 85.0001),
            TrackMapPoint(27.0010, 85.0010, startsNewSegment = true),
            TrackMapPoint(27.0011, 85.0011),
        )

        assertEquals(route.map { it.latitude to it.longitude }, continuousMapTrack(route)
            .map { it.latitude to it.longitude })
    }

    @Test
    fun invalidAndDuplicateCoordinatesAreRemoved() {
        val route = listOf(
            TrackMapPoint(27.0, 85.0),
            TrackMapPoint(27.0, 85.0),
            TrackMapPoint(Double.NaN, 85.1),
            TrackMapPoint(91.0, 85.2),
            TrackMapPoint(27.1, 85.1),
        )

        assertEquals(2, continuousMapTrack(route).size)
    }
}
