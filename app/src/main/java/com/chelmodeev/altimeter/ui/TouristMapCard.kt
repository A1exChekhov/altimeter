package com.chelmodeev.altimeter.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
import kotlin.math.roundToInt

class TouristMapSession(context: android.content.Context) {
    val mapView = MapView(context)
    var map by mutableStateOf<MapLibreMap?>(null)
    var styleRevision by mutableIntStateOf(0)
    var foregroundRevision by mutableIntStateOf(0)
    var follow by mutableStateOf(true)
    var hadFirstFix by mutableStateOf(false)
    var requestedStyleKey: String? = null
    var latestTrackPoints: List<TrackMapPoint> = emptyList()
    var latestTrackColor: Int = AndroidColor.CYAN
    var latestLatitude: Double? = null
    var latestLongitude: Double? = null

    private var attached = false
    private var hostStarted = false
    private var hostResumed = false
    private var viewStarted = false
    private var viewResumed = false
    private var destroyed = false

    init {
        mapView.onCreate(null)
        mapView.setOnTouchListener { view, event ->
            view.parent?.requestDisallowInterceptTouchEvent(true)
            if (event.actionMasked == MotionEvent.ACTION_DOWN) follow = false
            false
        }
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.isRotateGesturesEnabled = true
            readyMap.uiSettings.isTiltGesturesEnabled = true
            readyMap.uiSettings.isCompassEnabled = true
            readyMap.cameraPosition = CameraPosition.Builder().zoom(4.5).build()
        }
    }

    fun updateHostState(state: Lifecycle.State) {
        val wasResumed = hostResumed
        hostStarted = state.isAtLeast(Lifecycle.State.STARTED)
        hostResumed = state.isAtLeast(Lifecycle.State.RESUMED)
        syncLifecycle()
        if (!wasResumed && hostResumed) foregroundRevision++
    }

    fun attach() {
        attached = true
        syncLifecycle()
    }

    fun detach() {
        attached = false
        syncLifecycle()
    }

    fun destroy() {
        if (destroyed) return
        attached = false
        hostResumed = false
        hostStarted = false
        syncLifecycle()
        mapView.onDestroy()
        destroyed = true
    }

    private fun syncLifecycle() {
        if (destroyed) return
        val shouldStart = attached && hostStarted
        val shouldResume = attached && hostResumed
        if (shouldStart && !viewStarted) {
            mapView.onStart()
            viewStarted = true
        }
        if (shouldResume && !viewResumed) {
            if (!viewStarted) {
                mapView.onStart()
                viewStarted = true
            }
            mapView.onResume()
            viewResumed = true
        }
        if (!shouldResume && viewResumed) {
            mapView.onPause()
            viewResumed = false
        }
        if (!shouldStart && viewStarted) {
            if (viewResumed) {
                mapView.onPause()
                viewResumed = false
            }
            mapView.onStop()
            viewStarted = false
        }
    }
}

@Composable
fun rememberTouristMapSession(): TouristMapSession {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val session = remember(context) { TouristMapSession(context) }

    DisposableEffect(session, lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, _ ->
            session.updateHostState(lifecycle.currentState)
        }
        lifecycle.addObserver(observer)
        session.updateHostState(lifecycle.currentState)
        onDispose {
            lifecycle.removeObserver(observer)
            session.destroy()
        }
    }
    return session
}

/**
 * Векторная карта MapLibre. Онлайн используется только как фон; региональные
 * PMTiles подключаются тем же движком и полностью работают с локального файла.
 */
@Composable
fun MapCard(
    session: TouristMapSession,
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    topo: Boolean,
    accent: Color,
    trackPoints: List<TrackMapPoint>,
    trackRecording: Boolean,
    trackPointCount: Int,
    hasPreciseFix: Boolean,
    fineLocationGranted: Boolean,
    offlineMapPath: String?,
    expanded: Boolean,
    showExpandControl: Boolean = true,
    bottomControlPadding: Dp = 10.dp,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val networkAvailable = rememberNetworkAvailable()
    val map = session.map
    val styleRevision = session.styleRevision
    val foregroundRevision = session.foregroundRevision
    val mapView = session.mapView
    val trackStatus = when {
        !trackRecording -> null
        !fineLocationGranted || !hasPreciseFix -> stringResource(R.string.track_waiting_for_gps)
        accuracyMeters != null && accuracyMeters > MAX_TRACK_ACCURACY_METERS ->
            stringResource(R.string.track_waiting_for_accuracy, accuracyMeters.roundToInt())
        trackPointCount < 2 -> stringResource(R.string.track_collecting_points, trackPointCount)
        else -> stringResource(R.string.track_points_on_map, trackPointCount)
    }

    // Keep overlays independently of the network-backed basemap. If an online
    // style finishes loading later, its callback restores the current local GPX
    // immediately instead of briefly replacing it with an empty style.
    SideEffect {
        session.latestTrackPoints = trackPoints
        session.latestTrackColor = accent.toArgb()
        session.latestLatitude = latitude
        session.latestLongitude = longitude
    }

    DisposableEffect(session) {
        session.attach()
        onDispose { session.detach() }
    }

    LaunchedEffect(map, offlineMapPath, topo, networkAvailable) {
        val readyMap = map ?: return@LaunchedEffect
        val localPath = offlineMapPath?.takeIf { File(it).isFile }
        val styleKey = localPath ?: ONLINE_OUTDOOR_STYLE
        // Retry an online style exactly when connectivity returns. Offline PMTiles
        // never depend on this flag and are not needlessly reloaded.
        if (session.requestedStyleKey == styleKey && (localPath != null || !networkAvailable)) {
            return@LaunchedEffect
        }
        session.requestedStyleKey = styleKey
        val styleBuilder = if (localPath != null) {
            Style.Builder().fromJson(offlineMapStyle(localPath))
        } else {
            Style.Builder().fromUri(ONLINE_OUTDOOR_STYLE)
        }
        readyMap.setStyle(styleBuilder) { style ->
            installOverlayLayers(style)
            updateRoute(style, session.latestTrackPoints)
            updateLocation(
                style,
                session.latestLatitude,
                session.latestLongitude,
                session.latestTrackColor,
            )
            session.styleRevision++
        }
    }

    LaunchedEffect(
        map,
        styleRevision,
        foregroundRevision,
        latitude,
        longitude,
        accuracyMeters,
        accent,
    ) {
        val readyMap = map ?: return@LaunchedEffect
        val style = readyMap.style ?: return@LaunchedEffect
        installOverlayLayers(style)
        updateLocation(style, latitude, longitude, accent.toArgb())
        if (latitude != null && longitude != null) {
            val target = LatLng(latitude, longitude)
            if (!session.hadFirstFix) {
                session.hadFirstFix = true
                if (trackRecording || trackPoints.isEmpty()) {
                    readyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 15.0))
                }
            } else if (session.follow) {
                readyMap.animateCamera(CameraUpdateFactory.newLatLng(target))
            }
        }
    }

    LaunchedEffect(map, styleRevision, foregroundRevision, trackPoints, trackRecording) {
        val readyMap = map ?: return@LaunchedEffect
        val style = readyMap.style ?: return@LaunchedEffect
        installOverlayLayers(style)
        updateRoute(style, trackPoints)
        if (!trackRecording && trackPoints.size >= 2) {
            session.follow = false
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

    Box(
        modifier = modifier
            .clip(if (expanded) RectangleShape else RoundedCornerShape(22.dp))
            .background(Color(0xFF10192B))
    ) {
        AndroidView(
            factory = {
                (mapView.parent as? ViewGroup)?.removeView(mapView)
                mapView
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .then(if (expanded) Modifier.statusBarsPadding() else Modifier)
                .padding(10.dp),
        ) {
            Surface(shape = RoundedCornerShape(50), color = Color(0xCC101826)) {
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
            trackStatus?.let { status ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xE61A2535),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        text = status,
                        color = if (trackPointCount >= 2) TRACK_COLOR else Color(0xFFF2B94B),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }

        if (showExpandControl) {
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
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .then(if (expanded) Modifier.statusBarsPadding() else Modifier)
                    .padding(10.dp),
            )
        }

        TouristMapButton(
            icon = {
                Icon(
                    Icons.Rounded.MyLocation,
                    contentDescription = stringResource(R.string.cd_center_map),
                    tint = if (session.follow) accent else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = {
                session.follow = true
                if (latitude != null && longitude != null) {
                    session.map?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 15.0)
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = bottomControlPadding),
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
private fun rememberNetworkAvailable(): Boolean {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    var available by remember(manager) {
        mutableStateOf(
            manager.activeNetwork?.let(manager::getNetworkCapabilities)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        )
    }
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available = true
            }

            override fun onLost(network: Network) {
                available = manager.activeNetwork?.let(manager::getNetworkCapabilities)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return available
}

private fun installOverlayLayers(style: Style) {
    if (style.getSource(ROUTE_SOURCE) == null) {
        style.addSource(
            GeoJsonSource(ROUTE_SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>()))
        )
    }
    if (style.getLayer(ROUTE_CASING_LAYER) == null) {
        style.addLayer(
            LineLayer(ROUTE_CASING_LAYER, ROUTE_SOURCE).withProperties(
                lineColor(TRACK_CASING_COLOR),
                lineWidth(8f),
                lineOpacity(0.92f),
            )
        )
    }
    if (style.getLayer(ROUTE_LAYER) == null) {
        style.addLayer(
            LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                lineColor(TRACK_COLOR_ARGB),
                lineWidth(4.5f),
                lineOpacity(1f),
            )
        )
    }
    if (style.getSource(TRACK_START_SOURCE) == null) {
        style.addSource(
            GeoJsonSource(TRACK_START_SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>()))
        )
    }
    if (style.getLayer(TRACK_START_OUTER_LAYER) == null) {
        style.addLayer(
            CircleLayer(TRACK_START_OUTER_LAYER, TRACK_START_SOURCE).withProperties(
                circleRadius(8f),
                circleColor(AndroidColor.WHITE),
                circleStrokeColor(TRACK_CASING_COLOR),
                circleStrokeWidth(1.5f),
            )
        )
    }
    if (style.getLayer(TRACK_START_INNER_LAYER) == null) {
        style.addLayer(
            CircleLayer(TRACK_START_INNER_LAYER, TRACK_START_SOURCE).withProperties(
                circleRadius(4.5f),
                circleColor(TRACK_COLOR_ARGB),
            )
        )
    }
    if (style.getSource(LOCATION_SOURCE) == null) {
        style.addSource(
            GeoJsonSource(LOCATION_SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>()))
        )
    }
    if (style.getLayer(LOCATION_OUTER_LAYER) == null) {
        style.addLayer(
            CircleLayer(LOCATION_OUTER_LAYER, LOCATION_SOURCE).withProperties(
                circleRadius(9f),
                circleColor(AndroidColor.WHITE),
                circleStrokeColor(AndroidColor.argb(100, 0, 0, 0)),
                circleStrokeWidth(1.5f),
            )
        )
    }
    if (style.getLayer(LOCATION_INNER_LAYER) == null) {
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

private fun updateRoute(style: Style, track: List<TrackMapPoint>) {
    val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE) ?: return
    val segments = mutableListOf<MutableList<Point>>()
    for (point in track) {
        if (segments.isEmpty() || point.startsNewSegment) segments.add(mutableListOf())
        segments.last() += Point.fromLngLat(point.longitude, point.latitude)
    }
    val features = segments
        .filter { it.size >= 2 }
        .map { Feature.fromGeometry(LineString.fromLngLats(it)) }
        .toTypedArray()
    source.setGeoJson(FeatureCollection.fromFeatures(features))
    val startFeatures = track.firstOrNull()?.let { first ->
        arrayOf(Feature.fromGeometry(Point.fromLngLat(first.longitude, first.latitude)))
    } ?: emptyArray()
    style.getSourceAs<GeoJsonSource>(TRACK_START_SOURCE)
        ?.setGeoJson(FeatureCollection.fromFeatures(startFeatures))
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
private const val ROUTE_CASING_LAYER = "errarium-route-casing-layer"
private const val ROUTE_LAYER = "errarium-route-layer"
private const val TRACK_START_SOURCE = "errarium-track-start-source"
private const val TRACK_START_OUTER_LAYER = "errarium-track-start-outer"
private const val TRACK_START_INNER_LAYER = "errarium-track-start-inner"
private const val LOCATION_SOURCE = "errarium-location-source"
private const val LOCATION_OUTER_LAYER = "errarium-location-outer"
private const val LOCATION_INNER_LAYER = "errarium-location-inner"
private const val MAX_TRACK_ACCURACY_METERS = 50f
private val TRACK_COLOR_ARGB = 0xFF35E0D0.toInt()
private val TRACK_CASING_COLOR = 0xE6101826.toInt()
private val TRACK_COLOR = Color(TRACK_COLOR_ARGB)
