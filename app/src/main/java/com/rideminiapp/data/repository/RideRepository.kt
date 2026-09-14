package com.rideminiapp.data.repository

import com.rideminiapp.data.remote.GeoLocation
import com.rideminiapp.data.remote.NominatimClient
import com.rideminiapp.data.remote.OsrmClient
import com.rideminiapp.data.remote.RideApiService
import com.rideminiapp.di.CookieApi
import com.rideminiapp.di.PassengerApi
import com.rideminiapp.di.PublicApi
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RideRepository @Inject constructor(
    @PassengerApi private val passengerApi: RideApiService,
    @PublicApi private val publicApi: RideApiService,
    @CookieApi private val cookieApi: RideApiService,
    private val nominatimClient: NominatimClient,
    private val osrmClient: OsrmClient,
) {
    suspend fun currentUser() = passengerApi.getCurrentUser()

    suspend fun completeOnboarding() = passengerApi.completeOnboarding()

    suspend fun updateLanguage(language: String) = passengerApi.updateLanguage(mapOf("language" to language))

    suspend fun userCabinet(limit: Int = 20, offset: Int = 0) = passengerApi.getUserCabinet(limit, offset)

    suspend fun myRequests(limit: Int = 20, offset: Int = 0, scope: String? = null) =
        passengerApi.listMyRequests(limit, offset, scope)

    suspend fun requestById(id: String) = passengerApi.getRequest(id)

    suspend fun createRequest(passengerName: String, fromAddress: String, fromLat: Double, fromLng: Double, toAddress: String, toLat: Double, toLng: Double, dateTime: String) =
        passengerApi.createRequest(
            mapOf(
                "passengerName" to passengerName,
                "fromPoint" to mapOf("address" to fromAddress, "latlng" to mapOf("lat" to fromLat, "lng" to fromLng)),
                "toPoint" to mapOf("address" to toAddress, "latlng" to mapOf("lat" to toLat, "lng" to toLng)),
                "dateTime" to dateTime,
            ),
        )

    suspend fun pricing() = passengerApi.getPricing()

    suspend fun serviceZones() = passengerApi.listServiceZones()

    suspend fun publicMarks() = passengerApi.listPublicMapMarks()

    suspend fun passengerRideOffers(limit: Int = 50, offset: Int = 0) =
        passengerApi.listPassengerRideOffers(limit, offset)

    suspend fun passengerRideOfferMatches(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double) =
        passengerApi.listPassengerRideOfferMatches(fromLat, fromLng, toLat, toLng)

    suspend fun passengerRideOffer(offerId: String) = passengerApi.getPassengerRideOffer(offerId)

    suspend fun bookPassengerRideOffer(offerId: String) = passengerApi.bookPassengerRideOffer(offerId)

    suspend fun passengerNotifications(limit: Int = 50, offset: Int = 0) =
        passengerApi.listPassengerNotifications(limit, offset)

    suspend fun markAllPassengerNotificationsRead() = passengerApi.markAllPassengerNotificationsRead()

    suspend fun issuePointsQr(payload: Map<String, Any?> = emptyMap()) = passengerApi.issuePointsQr(payload)

    suspend fun redeemPointsQr(payload: Map<String, Any?>) = passengerApi.redeemPointsQr(payload)

    suspend fun purchasePointsCard(payload: Map<String, Any?>) = passengerApi.purchasePointsCard(payload)

    suspend fun transferPoints(payload: Map<String, Any?>) = passengerApi.transferPoints(payload)

    suspend fun rideQuote(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double) =
        passengerApi.getRideQuote(fromLat, fromLng, toLat, toLng)

    suspend fun driverSession() = cookieApi.getDriverSession()

    suspend fun loginDriver(key: String) = cookieApi.loginDriver(mapOf("key" to key))

    suspend fun adminSession() = cookieApi.getAdminSession()

    suspend fun loginAdmin(key: String) = cookieApi.loginAdmin(mapOf("key" to key))

    suspend fun logoutDriver() = cookieApi.logoutDriver()

    suspend fun logoutAdmin() = cookieApi.logoutAdmin()

    suspend fun driverOffers(limit: Int = 50, offset: Int = 0) = cookieApi.listDriverOffers(limit, offset)

    suspend fun createDriverOffer(payload: Map<String, Any?>) = cookieApi.createDriverOffer(payload)

    suspend fun driverOffer(offerId: String) = cookieApi.getDriverOffer(offerId)

    suspend fun driverOfferMatches(offerId: String) = cookieApi.getDriverOfferMatches(offerId)

    suspend fun deleteDriverOffer(offerId: String) = cookieApi.deleteDriverOffer(offerId)

    suspend fun driverNotifications(limit: Int = 50, offset: Int = 0) = cookieApi.listDriverNotifications(limit, offset)

    suspend fun driverUnreadNotificationCount() = cookieApi.getDriverNotificationsUnreadCount()

    suspend fun markDriverNotificationRead(notificationId: String) = cookieApi.markDriverNotificationRead(notificationId)

    suspend fun markAllDriverNotificationsRead() = cookieApi.markAllDriverNotificationsRead()

    suspend fun bootstrapDriverAccess() = cookieApi.bootstrapDriverAccess()

    suspend fun driverRegistrationForm() = publicApi.getDriverRegistrationForm()

    suspend fun myDriverApplication() = cookieApi.getMyDriverApplication()

    suspend fun driverCabinetEnterUrl() = cookieApi.getDriverCabinetEnterUrl()

    suspend fun listDriverApplications(status: String? = null, limit: Int = 20, offset: Int = 0) =
        cookieApi.listDriverApplications(status, limit, offset)

    suspend fun uploadDriverApplicationFile(file: MultipartBody.Part) = cookieApi.uploadDriverApplicationFile(file)

    suspend fun adminNotifications(limit: Int = 50, offset: Int = 0) = cookieApi.listAdminNotifications(limit, offset)

    suspend fun markAllAdminNotificationsRead() = cookieApi.markAllAdminNotificationsRead()

    suspend fun adminInfoBlocks(limit: Int = 50, offset: Int = 0) = cookieApi.listAdminInfoBlocks(limit, offset)

    suspend fun adminKeys(limit: Int = 20, offset: Int = 0) = cookieApi.listAdminKeys(limit, offset)

    suspend fun mapMarks(limit: Int = 100, offset: Int = 0) = cookieApi.listMapMarks(limit, offset)

    suspend fun createMapMark(payload: Map<String, Any?>) = cookieApi.createMapMark(payload)

    suspend fun deleteMapMark(markId: String) = cookieApi.deleteMapMark(markId)

    suspend fun createServiceZone(payload: Map<String, Any?>) = cookieApi.createServiceZone(payload)

    suspend fun updateServiceZone(zoneId: String, payload: Map<String, Any?>) = cookieApi.updateServiceZone(zoneId, payload)

    suspend fun deleteServiceZone(zoneId: String) = cookieApi.deleteServiceZone(zoneId)

    suspend fun updatePricing(payload: Map<String, Any?>) = cookieApi.updatePricing(payload)

    suspend fun nominatimSearch(query: String, viewbox: String? = null, countryCodes: String = "lt,az") =
        nominatimClient.search(query, viewbox, countryCodes)

    suspend fun nominatimReverse(lat: Double, lng: Double) = nominatimClient.reverse(lat, lng)

    suspend fun osrmRoute(points: List<GeoLocation>) = osrmClient.route(points)
}
