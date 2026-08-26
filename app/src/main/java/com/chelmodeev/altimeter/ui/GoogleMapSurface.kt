package com.chelmodeev.altimeter.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chelmodeev.altimeter.BuildConfig
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.logic.continuousMapTrack
import com.chelmodeev.altimeter.model.TrackMapPoint
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

internal fun googleMapsAvailable(context: Context): Boolean =
    BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank() &&
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
        ConnectionResult.SUCCESS

private class GoogleMapSession(context: Context) {
    val view = MapView(context)
    var map by mutableStateOf<GoogleMap?>(null)
    var follow by mutableStateOf(true)
    var bearing by mutableDoubleStateOf(0.0)
    var zoom by mutableDoubleStateOf(4.5)
    var centerLatitude by mutableDoubleStateOf(0.0)
    var hasCentered = false
    var locationMarker: Marker? = null
    var accuracyCircle: Circle? = null
    var route: Polyline? = null

    private var started = false
    private var resumed = false
    private var destroyed = false

    init {
        view.onCreate(null)
        view.getMapAsync { ready ->
            map = ready
            ready.mapType = GoogleMap.MAP_TYPE_TERRAIN
            ready.uiSettings.isCompassEnabled = false
            ready.uiSettings.isMapToolbarEnabled = false
            ready.uiSettings.isMyLocationButtonEnabled = false
            ready.uiSettings.isZoomControlsEnabled = false
            ready.moveCamera(CameraUpdateFactory.zoomTo(4.5f))
            ready.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) follow = false
            }
            ready.setOnCameraMoveListener {
                val camera = ready.cameraPosition
                bearing = camera.bearing.toDouble()
                zoom = camera.zoom.toDouble()
                centerLatitude = camera.target.latitude
            }
        }
    }

    fun updateLifecycle(state: Lifecycle.State) {
        if (destroyed) return
        val shouldStart = state.isAtLeast(Lifecycle.State.STARTED)
        val shouldResume = state.isAtLeast(Lifecycle.State.RESUMED)
        if (shouldStart && !started) {
            view.onStart()
            started = true
        }
        if (shouldResume && !resumed) {
            if (!started) {
                view.onStart()
                started = true
            }
            view.onResume()
            resumed = true
        }
        if (!shouldResume && resumed) {
            view.onPause()
            resumed = false
        }
        if (!shouldStart && started) {
            view.onStop()
            started = false
        }
    }

    fun destroy() {
        if (destroyed) return
        updateLifecycle(Lifecycle.State.DESTROYED)
        view.onDestroy()
        destroyed = true
    }
}

@Composable
internal fun GoogleMapSurface(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    accent: Color,
    trackPoints: List<TrackMapPoint>,
    trackRecording: Boolean,
    bottomControlPadding: Dp,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val session = remember(context) { GoogleMapSession(context) }

    DisposableEffect(session, lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, _ -> session.updateLifecycle(lifecycle.currentState) }
        lifecycle.addObserver(observer)
        session.updateLifecycle(lifecycle.currentState)
        onDispose {
            lifecycle.removeObserver(observer)
            session.destroy()
        }
    }

    LaunchedEffect(session.map, latitude, longitude, accuracyMeters, accent) {
        val map = session.map ?: return@LaunchedEffect
        if (latitude == null || longitude == null) return@LaunchedEffect
        val point = LatLng(latitude, longitude)
        val color = accent.toArgb()
        val marker = session.locationMarker
        if (marker == null) {
            session.locationMarker = map.addMarker(
                MarkerOptions().position(point).anchor(0.5f, 0.5f).title(null)
            )
        } else {
            marker.position = point
        }
        val accuracyCircle = session.accuracyCircle
        if (accuracyCircle == null) {
            session.accuracyCircle = map.addCircle(
                CircleOptions()
                    .center(point)
                    .radius((accuracyMeters ?: 0f).toDouble())
                    .fillColor(Color(color).copy(alpha = 0.10f).toArgb())
                    .strokeColor(Color(color).copy(alpha = 0.55f).toArgb())
                    .strokeWidth(2f)
            )
        } else {
            accuracyCircle.center = point
            accuracyCircle.radius = (accuracyMeters ?: 0f).toDouble()
            accuracyCircle.fillColor = Color(color).copy(alpha = 0.10f).toArgb()
            accuracyCircle.strokeColor = Color(color).copy(alpha = 0.55f).toArgb()
        }
        if (!session.hasCentered) {
            session.hasCentered = true
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, GOOGLE_INITIAL_ZOOM))
        } else if (session.follow) {
            map.animateCamera(CameraUpdateFactory.newLatLng(point))
        }
    }

    LaunchedEffect(session.map, trackPoints, trackRecording, accent) {
        val map = session.map ?: return@LaunchedEffect
        val points = continuousMapTrack(trackPoints).map { LatLng(it.latitude, it.longitude) }
        session.route?.remove()
        session.route = if (points.size >= 2) {
            map.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .color(accent.toArgb())
                    .width(7f * context.resources.displayMetrics.density)
                    .zIndex(3f)
            )
        } else {
            null
        }
        if (!trackRecording && points.size >= 2) {
            session.follow = false
            val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
            session.view.post {
                runCatching {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(
                            bounds,
                            (42 * context.resources.displayMetrics.density).toInt(),
                        )
                    )
                }
            }
        }
    }

    Box(modifier) {
        AndroidView(factory = { session.view }, modifier = Modifier.fillMaxSize())

        TouristMapButton(
            icon = {
                Icon(
                    Icons.Rounded.MyLocation,
                    contentDescription = context.getString(R.string.cd_center_map),
                    tint = if (session.follow) accent else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = {
                session.follow = true
                if (latitude != null && longitude != null) {
                    session.map?.animateCamera(
                        CameraUpdateFactory.newLatLng(LatLng(latitude, longitude))
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = bottomControlPadding),
        )

        TouristMapButton(
            icon = {
                Icon(
                    Icons.Rounded.Navigation,
                    contentDescription = context.getString(R.string.cd_north_map),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp).rotate(-session.bearing.toFloat()),
                )
            },
            onClick = {
                session.map?.let { map ->
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            com.google.android.gms.maps.model.CameraPosition.Builder(map.cameraPosition)
                                .bearing(0f)
                                .tilt(0f)
                                .build()
                        )
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(if (expanded) Modifier.statusBarsPadding() else Modifier)
                .padding(top = 58.dp, end = 10.dp),
        )

        MapScaleIndicator(
            zoom = session.zoom,
            latitude = session.centerLatitude.takeIf { it != 0.0 } ?: latitude ?: 0.0,
            tileSize = 256.0,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = bottomControlPadding + 30.dp),
        )
    }
}

private const val GOOGLE_INITIAL_ZOOM = 12.5f
