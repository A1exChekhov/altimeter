package com.chelmodeev.altimeter.logic

import com.chelmodeev.altimeter.model.TrackMapPoint

/**
 * Geometry shown on the live map must stay continuous even when the GPX file
 * starts a new segment after a temporary loss of GPS. A segment boundary is
 * still preserved in the exported GPX, but hiding the bridge on the live map
 * made a correctly recorded sparse background track look empty.
 */
fun continuousMapTrack(track: List<TrackMapPoint>): List<TrackMapPoint> = buildList {
    for (point in track) {
        val valid = point.latitude.isFinite() && point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
        if (!valid) continue
        val previous = lastOrNull()
        if (previous?.latitude == point.latitude && previous.longitude == point.longitude) continue
        add(point)
    }
}
