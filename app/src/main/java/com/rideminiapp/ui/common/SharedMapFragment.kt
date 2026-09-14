package com.rideminiapp.ui.common

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.data.repository.RideRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@AndroidEntryPoint
class SharedMapFragment : Fragment() {
    @Inject lateinit var rideRepository: RideRepository

    private var mapView: MapView? = null

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll = context.screenRoot()
        val root = context.sectionContainer()
        val status = TextView(context)
        val map = MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(11.5)
            controller.setCenter(GeoPoint(54.6872, 25.2797))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.dp(360),
            ).apply {
                topMargin = context.dp(12)
            }
        }
        mapView = map

        root.addView(context.titleView("Map"))
        root.addView(context.bodyView("OpenStreetMap baseline with public marks and service zones."))
        root.addView(status)
        root.addView(map)

        lifecycleScope.launch {
            runCatching {
                val publicMarks = rideRepository.publicMarks()
                val serviceZones = rideRepository.serviceZones()
                map.overlays.clear()

                publicMarks.items.forEach { mark ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(mark.position.lat, mark.position.lng)
                            title = mark.title
                            snippet = mark.visibility
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        },
                    )
                }

                serviceZones.items.forEach { zone ->
                    val points = zone.polygon.map { GeoPoint(it.lat, it.lng) }
                    if (points.size >= 3) {
                        map.overlays.add(
                            Polygon(map).apply {
                                setPoints(points)
                                outlinePaint.color = colorOrFallback(zone.color)
                                outlinePaint.strokeWidth = context.dp(2).toFloat()
                                fillPaint.color = Color.argb(40, Color.red(outlinePaint.color), Color.green(outlinePaint.color), Color.blue(outlinePaint.color))
                            },
                        )
                    }
                }
                map.invalidate()
                status.text = "Loaded ${publicMarks.items.size} marks and ${serviceZones.items.size} zones."
            }.onFailure {
                status.text = it.message ?: "Failed to load map overlays."
            }
        }

        scroll.addView(root)
        return scroll
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    private fun colorOrFallback(value: String): Int = runCatching {
        Color.parseColor(value)
    }.getOrDefault(Color.parseColor("#2F80ED"))
}
