package com.chelmodeev.altimeter.ui

import android.graphics.Color as AndroidColor
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.chelmodeev.altimeter.maps.offlineMapStyle
import com.chelmodeev.altimeter.model.TrackMapPoint
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File

/**
 * Векторная карта MapLibre. Онлайн используется только как фон; региональные
 * PMTiles подключаются тем же движком и полностью работают с локального файла.
 */
@Composable
fun MapCard(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    topo: Boolean,
    accent: Color,
    trackPoints: List<TrackMapPoint>,
    trackRecording: Boolean,
    offlineMapPath: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRevision by remember { mutableIntStateOf(0) }
    var follow by remember { mutableStateOf(true) }
    var hadFirstFix by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            setOnTouchListener { view, event ->
                view.parent?.requestDisallowInterceptTouchEvent(true)
                if (event.actionMasked == MotionEvent.ACTION_DOWN) follow = false
                false
            }
            getMapAsync { readyMap ->
                map = readyMap
                readyMap.uiSettings.isRotateGesturesEnabled = true
                readyMap.uiSettings.isTiltGesturesEnabled = true
                readyMap.uiSettings.isCompassEnabled = true
                readyMap.cameraPosition = CameraPosition.Builder().zoom(4.5).build()
            }
        }
    }

    LaunchedEffect(map, offlineMapPath, topo) {
        val readyMap = map ?: return@LaunchedEffect
        val localPath = offlineMapPath?.takeIf { File(it).isFile }
        val styleBuilder = if (localPath != null) {
            Style.Builder().fromJson(offlineMapStyle(localPath))
        } else {
            Style.Builder().fromUri(ONLINE_OUTDOOR_STYLE)
        }
        readyMap.setStyle(styleBuilder) { style ->
            installOverlayLayers(style)
            styleRevision++
        }
    }

    LaunchedEffect(map, styleRevision, latitude, longitude, accuracyMeters, accent) {
        val readyMap = map ?: return@LaunchedEffect
        val style = readyMap.style ?: return@LaunchedEffect
        updateLocation(style, latitude, longitude, accent.toArgb())
        if (latitude != null && longitude != null) {
            val target = LatLng(latitude, longitude)
            if (!hadFirstFix) {
                hadFirstFix = true
                if (trackRecording || trackPoints.isEmpty()) {
                    readyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 15.0))
                }
            } else if (follow) {
                readyMap.animateCamera(CameraUpdateFactory.newLatLng(target))
            }
        }
    }

    LaunchedEffect(map, styleRevision, trackPoints, trackRecording, accent) {
        val readyMap = map ?: return@LaunchedEffect
        val style = readyMap.style ?: return@LaunchedEffect
        updateRoute(style, trackPoints, accent.toArgb())
        if (!trackRecording && trackPoints.size >= 2) {
            follow = false
            val points = trackPoints.map { LatLng(it.latitude, it.longitude) }
            mapView.post {
                runCatching {
                    readyMap.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(
                            LatLngBounds.fromLatLngs(points),
                            (42 * context.resources.displayMetrics.density).toInt(),
                        )
                    )
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF10192B))
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xCC101826),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
        ) {
            Text(
                text = stringResource(
                    if (offlineMapPath != null) R.string.map_offline_beta
                    else R.string.map_tourist_beta
                ),
                color = Color(0xFFB9F6CA),
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }

        TouristMapButton(
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
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
        )

        TouristMapButton(
            icon = {
                Icon(
                    Icons.Rounded.MyLocation,
                    contentDescription = stringResource(R.string.cd_center_map),
                    tint = if (follow) accent else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = {
                follow = true
                if (latitude != null && longitude != null) {
                    map?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 15.0)
                    )
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
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

private fun installOverlayLayers(style: Style) {
    if (style.getSource(ROUTE_SOURCE) == null) {
        style.addSource(
            GeoJsonSource(ROUTE_SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>()))
        )
        style.addLayer(
            LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                lineColor(AndroidColor.CYAN),
                lineWidth(5f),
                lineOpacity(0.96f),
            )
        )
    }
    if (style.getSource(LOCATION_SOURCE) == null) {
        style.addSource(
            GeoJsonSource(LOCATION_SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>()))
        )
        style.addLayer(
            CircleLayer(LOCATION_OUTER_LAYER, LOCATION_SOURCE).withProperties(
                circleRadius(9f),
                circleColor(AndroidColor.WHITE),
                circleStrokeColor(AndroidColor.argb(100, 0, 0, 0)),
                circleStrokeWidth(1.5f),
            )
        )
        style.addLayer(
            CircleLayer(LOCATION_INNER_LAYER, LOCATION_SOURCE).withProperties(
                circleRadius(5.5f),
                circleColor(AndroidColor.CYAN),
            )
        )
    }
}

private fun updateLocation(style: Style, latitude: Double?, longitude: Double?, color: Int) {
    val source = style.getSourceAs<GeoJsonSource>(LOCATION_SOURCE) ?: return
    val features = if (latitude != null && longitude != null) {
        arrayOf(Feature.fromGeometry(Point.fromLngLat(longitude, latitude)))
    } else {
        emptyArray<Feature>()
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
    style.getLayerAs<CircleLayer>(LOCATION_INNER_LAYER)?.setProperties(circleColor(color))
}

private fun updateRoute(style: Style, track: List<TrackMapPoint>, color: Int) {
    val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE) ?: return
    val features = if (track.size >= 2) {
        val line = LineString.fromLngLats(track.map { Point.fromLngLat(it.longitude, it.latitude) })
        arrayOf(Feature.fromGeometry(line))
    } else {
        emptyArray<Feature>()
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
    style.getLayerAs<LineLayer>(ROUTE_LAYER)?.setProperties(lineColor(color))
}

@Composable
private fun TouristMapButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = CircleShape, color = Color(0xCC101826), modifier = modifier) {
        IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) { icon() }
    }
}

private const val ONLINE_OUTDOOR_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val ROUTE_SOURCE = "errarium-route-source"
private const val ROUTE_LAYER = "errarium-route-layer"
private const val LOCATION_SOURCE = "errarium-location-source"
private const val LOCATION_OUTER_LAYER = "errarium-location-outer"
private const val LOCATION_INNER_LAYER = "errarium-location-inner"
