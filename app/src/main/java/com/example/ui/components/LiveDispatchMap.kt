package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.location.NearbyMechanicMatcher
import com.example.data.model.Mechanic
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time interactive OpenStreetMap view (osmdroid) rendering the customer's breakdown
 * location, search scan radius ring, and all online nearby mechanics as live Ola/Uber-style
 * markers with distance & ETA callouts.
 */
@Composable
fun LiveDispatchMap(
    customerLat: Double?,
    customerLng: Double?,
    customerLabel: String = "Your Breakdown Spot",
    mechanics: List<Mechanic>,
    showSearchRadius: Boolean = true,
    searchRadiusMeters: Double = 3500.0,
    focusedMechanicId: String? = null,
    recenterTrigger: Int = 0,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember(context) {
        configureOsmdroidOnce(context)
        true
    }

    val mapView = remember(context) {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            isTilesScaledToDpi = true
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    val cameraState = remember { CameraSyncState() }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            val validCustomer = NearbyMechanicMatcher.isValidCoordinate(customerLat, customerLng)
            val customerPoint = if (validCustomer) GeoPoint(customerLat!!, customerLng!!) else null

            val plottedMechanics = mechanics.filter {
                NearbyMechanicMatcher.isValidCoordinate(it.latitude, it.longitude)
            }

            map.overlays.clear()

            // 1. Search radius translucent ring around customer breakdown location
            if (showSearchRadius && customerPoint != null) {
                val circle = Polygon(map).apply {
                    points = Polygon.pointsAsCircle(customerPoint, searchRadiusMeters)
                    fillPaint.color = Color.argb(30, 249, 115, 22)
                    outlinePaint.color = Color.argb(145, 249, 115, 22)
                    outlinePaint.strokeWidth = 3f
                }
                map.overlays.add(circle)
            }

            // 2. Online mechanic pins (Ola/Uber vehicle/service style)
            plottedMechanics.forEach { mech ->
                val mLat = mech.latitude ?: return@forEach
                val mLng = mech.longitude ?: return@forEach
                val mechPoint = GeoPoint(mLat, mLng)
                val distKm = mech.currentDistanceKm ?: if (customerPoint != null) {
                    (NearbyMechanicMatcher.haversineDistanceKm(
                        customerPoint.latitude,
                        customerPoint.longitude,
                        mLat,
                        mLng
                    ) * 10.0).toInt() / 10f
                } else null
                val etaMins = mech.estimatedArrivalMinutes ?: distKm?.let {
                    ((it / 25.0) * 60.0).toInt().coerceAtLeast(3)
                }

                val isFocused = focusedMechanicId == mech.id
                val shortLabel = buildString {
                    append(mech.name.take(16))
                    if (distKm != null) {
                        append(" • ")
                        append("${"%.1f".format(distKm)} km")
                    }
                }

                val marker = Marker(map).apply {
                    position = mechPoint
                    title = mech.name
                    snippet = buildString {
                        append(if (mech.isVerified) "Verified Mechanic • Online" else "Online Mechanic")
                        if (distKm != null && etaMins != null) {
                            append("\n${"%.1f".format(distKm)} km away • ~$etaMins min ETA")
                        }
                        if (mech.locality.isNotBlank()) {
                            append("\n${mech.locality}")
                        }
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = createMechanicPinDrawable(context, shortLabel, isFocused)
                }
                map.overlays.add(marker)
            }

            // 3. Customer breakdown pin on top
            if (customerPoint != null) {
                val customerMarker = Marker(map).apply {
                    position = customerPoint
                    title = "You"
                    snippet = customerLabel
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = createCustomerPinDrawable(context)
                }
                map.overlays.add(customerMarker)
            }

            // 4. Camera positioning: focus tapped mechanic, recenter, or auto-fit all live markers
            val focusedMech = focusedMechanicId?.let { id ->
                plottedMechanics.firstOrNull { it.id == id }
            }
            val recenterChanged = cameraState.lastRecenterTrigger != recenterTrigger
            val focusChanged = cameraState.lastFocusedId != focusedMechanicId
            val markersKey = buildString {
                append(customerPoint?.latitude?.toString() ?: "")
                append(",")
                append(customerPoint?.longitude?.toString() ?: "")
                append("|")
                plottedMechanics.forEach { append("${it.id}:${it.latitude}:${it.longitude};") }
            }
            val dataChanged = cameraState.lastMarkersKey != markersKey

            if (focusedMech != null && (focusChanged || recenterChanged)) {
                cameraState.lastFocusedId = focusedMechanicId
                cameraState.lastRecenterTrigger = recenterTrigger
                map.controller.animateTo(
                    GeoPoint(focusedMech.latitude!!, focusedMech.longitude!!),
                    15.5,
                    550L
                )
            } else if (!cameraState.hasInitialized || recenterChanged || dataChanged) {
                cameraState.hasInitialized = true
                cameraState.lastRecenterTrigger = recenterTrigger
                cameraState.lastFocusedId = focusedMechanicId
                cameraState.lastMarkersKey = markersKey

                val allPoints = buildList {
                    if (customerPoint != null) add(customerPoint)
                    plottedMechanics.forEach { add(GeoPoint(it.latitude!!, it.longitude!!)) }
                }

                when {
                    allPoints.isEmpty() -> {
                        // Neutral India center fallback before GPS resolves
                        map.controller.setZoom(12.0)
                        map.controller.setCenter(GeoPoint(19.0760, 72.8777))
                    }
                    allPoints.size == 1 -> {
                        map.controller.setZoom(14.8)
                        map.controller.setCenter(allPoints.first())
                    }
                    else -> {
                        var minLat = Double.MAX_VALUE
                        var maxLat = -Double.MAX_VALUE
                        var minLng = Double.MAX_VALUE
                        var maxLng = -Double.MAX_VALUE
                        allPoints.forEach { pt ->
                            minLat = min(minLat, pt.latitude)
                            maxLat = max(maxLat, pt.latitude)
                            minLng = min(minLng, pt.longitude)
                            maxLng = max(maxLng, pt.longitude)
                        }
                        val latSpan = max(maxLat - minLat, 0.008)
                        val lngSpan = max(maxLng - minLng, 0.008)
                        val padLat = latSpan * 0.35
                        val padLng = lngSpan * 0.35
                        val bbox = BoundingBox(
                            (maxLat + padLat).coerceAtMost(85.0),
                            (maxLng + padLng).coerceAtMost(180.0),
                            (minLat - padLat).coerceAtLeast(-85.0),
                            (minLng - padLng).coerceAtLeast(-180.0)
                        )
                        map.post {
                            runCatching {
                                map.zoomToBoundingBox(bbox, true, 96)
                                if (map.zoomLevelDouble > 16.0) {
                                    map.controller.setZoom(16.0)
                                }
                            }.onFailure {
                                map.controller.setZoom(14.2)
                                map.controller.setCenter(customerPoint ?: allPoints.first())
                            }
                        }
                    }
                }
            }

            map.invalidate()
        }
    )
}

private class CameraSyncState {
    var hasInitialized: Boolean = false
    var lastRecenterTrigger: Int = 0
    var lastFocusedId: String? = null
    var lastMarkersKey: String = ""
}

private fun configureOsmdroidOnce(context: Context) {
    runCatching {
        val config = Configuration.getInstance()
        config.userAgentValue = context.packageName
        val basePath = File(context.cacheDir, "osmdroid").apply { mkdirs() }
        val tileCache = File(basePath, "tiles").apply { mkdirs() }
        config.osmdroidBasePath = basePath
        config.osmdroidTileCache = tileCache
    }
}

private fun createCustomerPinDrawable(context: Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val width = (96 * density).toInt()
    val height = (72 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f

    // Top "YOU" pill
    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }
    val pillW = 54 * density
    val pillH = 20 * density
    val pillRect = RectF(cx - pillW / 2f, 2 * density, cx + pillW / 2f, 2 * density + pillH)
    canvas.drawRoundRect(pillRect, 10 * density, 10 * density, pillPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("YOU", cx, pillRect.centerY() + 3.5f * density, textPaint)

    // Pin shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 15, 23, 42) }
    canvas.drawOval(
        RectF(cx - 10 * density, height - 8 * density, cx + 10 * density, height - 2 * density),
        shadowPaint
    )

    // Teardrop pin body
    val pinCenterY = 42 * density
    val pinRadius = 15 * density
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EA580C") }

    val tipPath = Path().apply {
        moveTo(cx, height - 5 * density)
        lineTo(cx - 11 * density, pinCenterY + 6 * density)
        lineTo(cx + 11 * density, pinCenterY + 6 * density)
        close()
    }
    canvas.drawPath(tipPath, borderPaint)
    canvas.drawCircle(cx, pinCenterY, pinRadius + 2.5f * density, borderPaint)

    val innerTipPath = Path().apply {
        moveTo(cx, height - 8 * density)
        lineTo(cx - 9 * density, pinCenterY + 6 * density)
        lineTo(cx + 9 * density, pinCenterY + 6 * density)
        close()
    }
    canvas.drawPath(innerTipPath, pinPaint)
    canvas.drawCircle(cx, pinCenterY, pinRadius, pinPaint)

    // White center dot
    canvas.drawCircle(cx, pinCenterY, 5.5f * density, borderPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createMechanicPinDrawable(
    context: Context,
    label: String,
    isFocused: Boolean
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10.5f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textWidth = textPaint.measureText(label)
    val pillW = max(84 * density, textWidth + 24 * density)
    val pillH = 22 * density
    val width = (pillW + 12 * density).toInt()
    val height = (76 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f

    // Floating dark-navy name + distance pill (Ola/Uber vehicle tag)
    val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isFocused) Color.parseColor("#EA580C") else Color.parseColor("#0F172A")
    }
    val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    val pillRect = RectF(cx - pillW / 2f, 2 * density, cx + pillW / 2f, 2 * density + pillH)
    canvas.drawRoundRect(pillRect, 11 * density, 11 * density, pillBg)
    canvas.drawRoundRect(pillRect, 11 * density, 11 * density, pillBorder)
    canvas.drawText(label, cx, pillRect.centerY() + 3.8f * density, textPaint)

    // Ground shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(65, 15, 23, 42) }
    canvas.drawOval(
        RectF(cx - 12 * density, height - 8 * density, cx + 12 * density, height - 2 * density),
        shadowPaint
    )

    // Circular service-unit badge with emerald live ring
    val badgeY = 48 * density
    val badgeRadius = 17 * density
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isFocused) Color.parseColor("#F97316") else Color.parseColor("#10B981")
    }
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }

    // Pointer tail
    val pointer = Path().apply {
        moveTo(cx, height - 5 * density)
        lineTo(cx - 8 * density, badgeY + 12 * density)
        lineTo(cx + 8 * density, badgeY + 12 * density)
        close()
    }
    canvas.drawPath(pointer, ringPaint)
    canvas.drawCircle(cx, badgeY, badgeRadius + 3 * density, whitePaint)
    canvas.drawCircle(cx, badgeY, badgeRadius + 1 * density, ringPaint)
    canvas.drawCircle(cx, badgeY, badgeRadius - 2.5f * density, innerPaint)

    // Crisp mechanic wrench/service glyph inside circle
    val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2.6f * density
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    canvas.drawLine(cx - 5.5f * density, badgeY + 5.5f * density, cx + 4.5f * density, badgeY - 4.5f * density, glyphPaint)
    canvas.drawCircle(cx + 5f * density, badgeY - 5f * density, 3.2f * density, glyphPaint)
    canvas.drawCircle(cx - 5.5f * density, badgeY + 5.5f * density, 2.2f * density, glyphPaint)

    // Live green online status dot on top-right of badge
    val dotX = cx + badgeRadius * 0.78f
    val dotY = badgeY - badgeRadius * 0.72f
    canvas.drawCircle(dotX, dotY, 5.2f * density, whitePaint)
    canvas.drawCircle(dotX, dotY, 3.6f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22C55E")
    })

    return BitmapDrawable(context.resources, bitmap)
}
