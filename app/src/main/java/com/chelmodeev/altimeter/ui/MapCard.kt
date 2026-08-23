package com.chelmodeev.altimeter.ui

import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.model.TrackMapPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

private val TOPO_SOURCE = XYTileSource(
    "OpenTopoMap", 3, 17, 256, ".png",
    arrayOf(
        "https://a.tile.opentopomap.org/",
        "https://b.tile.opentopomap.org/",
        "https://c.tile.opentopomap.org/",
    ),
    "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)",
)

@Composable
fun MapCard(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    topo: Boolean,
    accent: Color,
    trackPoints: List<TrackMapPoint>,
    trackRecording: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val follow = remember { mutableStateOf(true) }
    val hadFirstFix = remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(4.5)
            setOnTouchListener { v, e ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                if (e.actionMasked == MotionEvent.ACTION_DOWN) follow.value = false
                false
            }
        }
    }
    val accuracyCircle = remember { Polygon() }
    val routeLine = remember {
        Polyline(mapView).apply {
            outlinePaint.strokeWidth = 7f * context.resources.displayMetrics.density
        }
    }
    val marker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
        }
    }

    LaunchedEffect(Unit) {
        mapView.overlays.add(accuracyCircle)
        mapView.overlays.add(routeLine)
        mapView.overlays.add(marker)
        mapView.overlays.add(CopyrightOverlay(context))
    }

    LaunchedEffect(topo) {
        mapView.setTileSource(if (topo) TOPO_SOURCE else TileSourceFactory.MAPNIK)
    }

    LaunchedEffect(accent) {
        val density = context.resources.displayMetrics.density
        marker.icon = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accent.toArgb())
            setStroke((3 * density).toInt(), android.graphics.Color.WHITE)
            setSize((18 * density).toInt(), (18 * density).toInt())
        }
        accuracyCircle.fillPaint.color = accent.copy(alpha = 0.10f).toArgb()
        accuracyCircle.outlinePaint.color = accent.copy(alpha = 0.45f).toArgb()
        accuracyCircle.outlinePaint.strokeWidth = 2f
        routeLine.outlinePaint.color = accent.toArgb()
        mapView.invalidate()
    }

    LaunchedEffect(trackPoints, trackRecording) {
        val points = trackPoints.map { GeoPoint(it.latitude, it.longitude) }
        routeLine.setPoints(points)
        if (!trackRecording && points.size >= 2) {
            follow.value = false
            val north = points.maxOf { it.latitude }
            val east = points.maxOf { it.longitude }
            val south = points.minOf { it.latitude }
            val west = points.minOf { it.longitude }
            mapView.post {
                mapView.zoomToBoundingBox(
                    org.osmdroid.util.BoundingBox(north, east, south, west),
                    true,
                    42,
                )
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(latitude, longitude, accuracyMeters, follow.value) {
        if (latitude == null || longitude == null) return@LaunchedEffect
        val point = GeoPoint(latitude, longitude)
        marker.position = point
        val acc = accuracyMeters?.toDouble() ?: 0.0
        accuracyCircle.points =
            if (acc > 5.0) Polygon.pointsAsCircle(point, acc) else arrayListOf(point, point, point)
        if (!hadFirstFix.value) {
            hadFirstFix.value = true
            if (trackRecording || trackPoints.isEmpty()) {
                mapView.controller.setZoom(15.0)
                mapView.controller.setCenter(point)
            }
        } else if (follow.value) {
            mapView.controller.animateTo(point)
        }
        mapView.invalidate()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF10192B))
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        MapRoundButton(
            icon = {
                Icon(
                    if (expanded) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                    contentDescription = stringResource(
                        if (expanded) R.string.cd_collapse_map else R.string.cd_expand_map
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            },
            onClick = onToggleExpanded,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
        )

        MapRoundButton(
            icon = {
                Icon(
                    Icons.Rounded.MyLocation,
                    contentDescription = stringResource(R.string.cd_center_map),
                    tint = if (follow.value) accent else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = {
                follow.value = true
                if (latitude != null && longitude != null) {
                    mapView.controller.animateTo(GeoPoint(latitude, longitude))
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
        )

        if (latitude == null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xCC101826),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(
                    text = stringResource(R.string.map_locating),
                    color = Color(0xFFB9C7DD),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MapRoundButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = CircleShape, color = Color(0xB3101826), modifier = modifier) {
        IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) { icon() }
    }
}
