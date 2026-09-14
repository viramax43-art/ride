package com.rideminiapp.data.remote

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeoLocation(
    val lat: Double,
    val lng: Double,
    val label: String,
)

data class RouteGeometry(
    val points: List<GeoLocation>,
    val distanceKm: Double? = null,
    val durationMin: Double? = null,
)

class NominatimClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    private val cache = LinkedHashMap<String, List<GeoLocation>>(32, 0.75f, true)

    suspend fun search(query: String, viewbox: String? = null, countryCodes: String = "lt,az"): List<GeoLocation> {
        val key = "${query.trim().lowercase()}|$viewbox|$countryCodes"
        cache[key]?.let { return it }

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("nominatim.openstreetmap.org")
            .addPathSegments("search")
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "8")
            .addQueryParameter("countrycodes", countryCodes)
            .apply {
                if (!viewbox.isNullOrBlank()) addQueryParameter("viewbox", viewbox)
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RideMiniApp/1.0 (Android)")
            .build()

        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            val array = JSONArray(body)
            val result = buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        GeoLocation(
                            lat = item.optString("lat").toDoubleOrNull() ?: 0.0,
                            lng = item.optString("lon").toDoubleOrNull() ?: 0.0,
                            label = item.optString("display_name"),
                        ),
                    )
                }
            }
            cache[key] = result
            return result
        }
    }

    suspend fun reverse(lat: Double, lng: Double): String {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("nominatim.openstreetmap.org")
            .addPathSegments("reverse")
            .addQueryParameter("lat", lat.toString())
            .addQueryParameter("lon", lng.toString())
            .addQueryParameter("format", "json")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RideMiniApp/1.0 (Android)")
            .build()

        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return ""
            return JSONObject(resp.body?.string().orEmpty()).optString("display_name", "")
        }
    }
}

class OsrmClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun route(points: List<GeoLocation>): RouteGeometry {
        if (points.size < 2) return RouteGeometry(points)
        val coords = points.joinToString(";") { "${it.lng},${it.lat}" }
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("router.project-osrm.org")
            .addPathSegments("route/v1/driving/$coords")
            .addQueryParameter("overview", "full")
            .addQueryParameter("geometries", "geojson")
            .build()

        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return RouteGeometry(points)
            val json = JSONObject(resp.body?.string().orEmpty())
            val routes = json.optJSONArray("routes") ?: return RouteGeometry(points)
            if (routes.length() == 0) return RouteGeometry(points)
            val geometry = routes.getJSONObject(0).optJSONObject("geometry") ?: return RouteGeometry(points)
            val coordinates = geometry.optJSONArray("coordinates") ?: return RouteGeometry(points)
            val mapped = buildList {
                for (index in 0 until coordinates.length()) {
                    val pair = coordinates.getJSONArray(index)
                    add(
                        GeoLocation(
                            lat = pair.optDouble(1, 0.0),
                            lng = pair.optDouble(0, 0.0),
                            label = "",
                        ),
                    )
                }
            }
            return RouteGeometry(mapped)
        }
    }
}
