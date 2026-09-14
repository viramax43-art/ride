package com.rideminiapp.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Part
import retrofit2.http.Multipart

interface RideApiService {
    @POST("/api/auth")
    suspend fun loginWithTelegram(@Body body: InitDataRequest): TokenResponse

    @GET("/api/users/me")
    suspend fun getCurrentUser(): CurrentUserDto

    @POST("/api/users/me/complete-onboarding")
    suspend fun completeOnboarding(): Map<String, Boolean>

    @PATCH("/api/users/me/language")
    suspend fun updateLanguage(@Body body: Map<String, String>): CurrentUserDto

    @GET("/api/users/me/cabinet")
    suspend fun getUserCabinet(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): UserCabinetDto

    @GET("/api/ride-requests/me")
    suspend fun listMyRequests(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("scope") scope: String? = null,
    ): ApiPage<RideRequestDto>

    @GET("/api/ride-requests/{id}")
    suspend fun getRequest(@Path("id") id: String): RideRequestDto

    @POST("/api/ride-requests")
    suspend fun createRequest(@Body body: Map<String, Any?>): RideRequestDto

    @PATCH("/api/ride-requests/{id}")
    suspend fun updateRequest(
        @Path("id") id: String,
        @Body body: Map<String, Any?>,
    ): RideRequestDto

    @POST("/api/ride-requests/{id}/confirm-pickup")
    suspend fun confirmPickup(@Path("id") id: String): RideRequestDto

    @DELETE("/api/ride-requests/{id}")
    suspend fun deleteRequest(@Path("id") id: String): Map<String, Boolean>

    @POST("/api/ride-requests/{id}/rate")
    suspend fun rateRideAsPassenger(@Path("id") id: String, @Body body: Map<String, Any?>): RideRequestDto

    @GET("/api/pricing")
    suspend fun getPricing(): PricingSettingsDto

    @GET("/api/service-zones")
    suspend fun listServiceZones(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): ApiPage<ServiceZoneDto>

    @GET("/api/map-marks/public")
    suspend fun listPublicMapMarks(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): ApiPage<MapMarkDto>

    @GET("/api/ride-quote")
    suspend fun getRideQuote(
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double,
    ): Map<String, Any?>

    @GET("/api/ride-offers")
    suspend fun listPassengerRideOffers(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): ApiPage<Map<String, Any?>>

    @GET("/api/ride-offers/matches")
    suspend fun listPassengerRideOfferMatches(
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double,
    ): ApiPage<Map<String, Any?>>

    @GET("/api/ride-offers/{offerId}")
    suspend fun getPassengerRideOffer(@Path("offerId") offerId: String): Map<String, Any?>

    @POST("/api/ride-offers/{offerId}/book")
    suspend fun bookPassengerRideOffer(@Path("offerId") offerId: String): RideRequestDto

    @GET("/api/notifications/passenger")
    suspend fun listPassengerNotifications(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): ApiPage<Map<String, Any?>>

    @POST("/api/notifications/passenger/read-all")
    suspend fun markAllPassengerNotificationsRead(): Map<String, Any?>

    @POST("/api/points/qr/issue")
    suspend fun issuePointsQr(@Body body: Map<String, Any?> = emptyMap()): Map<String, Any?>

    @POST("/api/points/qr/redeem")
    suspend fun redeemPointsQr(@Body body: Map<String, Any?>): Map<String, Any?>

    @POST("/api/points/card/purchase")
    suspend fun purchasePointsCard(@Body body: Map<String, Any?>): Map<String, Any?>

    @POST("/api/points/transfer")
    suspend fun transferPoints(@Body body: Map<String, Any?>): Map<String, Any?>

    @GET("/api/driver-registration/form")
    suspend fun getDriverRegistrationForm(): Map<String, Any?>

    @GET("/api/driver-registration/applications/me")
    suspend fun getMyDriverApplication(): Map<String, Any?>?

    @GET("/api/driver-registration/driver-access/enter-url")
    suspend fun getDriverCabinetEnterUrl(): Map<String, String>

    @POST("/api/driver-registration/driver-access/bootstrap")
    suspend fun bootstrapDriverAccess(): DriverSessionDto

    @GET("/api/driver-registration/applications")
    suspend fun listDriverApplications(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): ApiPage<Map<String, Any?>>

    @Multipart
    @POST("/api/driver-registration/files")
    suspend fun uploadDriverApplicationFile(@Part file: MultipartBody.Part): Map<String, Any?>

    @GET("/api/driver/session/me")
    suspend fun getDriverSession(): DriverSessionDto

    @POST("/api/driver/session/login")
    suspend fun loginDriver(@Body body: Map<String, String>): DriverSessionDto

    @POST("/api/driver/session/logout")
    suspend fun logoutDriver(): Map<String, Boolean>

    @GET("/api/driver/offers")
    suspend fun listDriverOffers(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): ApiPage<Map<String, Any?>>

    @POST("/api/driver/offers")
    suspend fun createDriverOffer(@Body body: Map<String, Any?>): Map<String, Any?>

    @GET("/api/driver/offers/{offerId}")
    suspend fun getDriverOffer(@Path("offerId") offerId: String): Map<String, Any?>

    @GET("/api/driver/offers/{offerId}/matches")
    suspend fun getDriverOfferMatches(@Path("offerId") offerId: String): ApiPage<Map<String, Any?>>

    @DELETE("/api/driver/offers/{offerId}")
    suspend fun deleteDriverOffer(@Path("offerId") offerId: String): Map<String, Boolean>

    @GET("/api/driver/notifications")
    suspend fun listDriverNotifications(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): ApiPage<Map<String, Any?>>

    @GET("/api/driver/notifications/unread-count")
    suspend fun getDriverNotificationsUnreadCount(): Map<String, Any?>

    @PATCH("/api/driver/notifications/{notificationId}/read")
    suspend fun markDriverNotificationRead(@Path("notificationId") notificationId: String): Map<String, Any?>

    @POST("/api/driver/notifications/read-all")
    suspend fun markAllDriverNotificationsRead(): Map<String, Any?>

    @GET("/api/driver/cabinet")
    suspend fun getDriverCabinet(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): Map<String, Any?>

    @GET("/api/driver/cabinet/map")
    suspend fun getDriverMapData(): Map<String, Any?>

    @POST("/api/driver/cabinet/location")
    suspend fun sendDriverLocation(@Body body: Map<String, Double>): Map<String, Boolean>

    @PATCH("/api/driver/cabinet/online")
    suspend fun setDriverOnline(@Body body: Map<String, Boolean>): DriverSessionDto

    @GET("/api/admin/session/me")
    suspend fun getAdminSession(): AdminSessionDto

    @POST("/api/admin/session/login")
    suspend fun loginAdmin(@Body body: Map<String, String>): AdminSessionDto

    @POST("/api/admin/session/logout")
    suspend fun logoutAdmin(): Map<String, Boolean>

    @GET("/api/admin/keys")
    suspend fun listAdminKeys(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): ApiPage<Map<String, Any?>>

    @GET("/api/admin/notifications")
    suspend fun listAdminNotifications(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): ApiPage<Map<String, Any?>>

    @POST("/api/admin/notifications/read-all")
    suspend fun markAllAdminNotificationsRead(): Map<String, Any?>

    @GET("/api/admin/info-blocks")
    suspend fun listAdminInfoBlocks(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): ApiPage<Map<String, Any?>>

    @GET("/api/map-marks")
    suspend fun listMapMarks(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): ApiPage<MapMarkDto>

    @POST("/api/map-marks")
    suspend fun createMapMark(@Body body: Map<String, Any?>): MapMarkDto

    @DELETE("/api/map-marks/{markId}")
    suspend fun deleteMapMark(@Path("markId") markId: String): Map<String, Boolean>

    @POST("/api/service-zones")
    suspend fun createServiceZone(@Body body: Map<String, Any?>): ServiceZoneDto

    @PATCH("/api/service-zones/{zoneId}")
    suspend fun updateServiceZone(@Path("zoneId") zoneId: String, @Body body: Map<String, Any?>): ServiceZoneDto

    @DELETE("/api/service-zones/{zoneId}")
    suspend fun deleteServiceZone(@Path("zoneId") zoneId: String): Map<String, Boolean>

    @PATCH("/api/pricing")
    suspend fun updatePricing(@Body body: Map<String, Any?>): PricingSettingsDto
}
